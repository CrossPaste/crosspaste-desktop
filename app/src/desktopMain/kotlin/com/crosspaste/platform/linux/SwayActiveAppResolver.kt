package com.crosspaste.platform.linux

import com.crosspaste.utils.getJsonUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Path

/**
 * Resolves the focused app over Sway's i3-compatible IPC socket (`$SWAYSOCK`):
 * one `GET_TREE` request per query, then a walk of the layout tree for the
 * single `focused: true` node. Native Wayland windows carry `app_id`; XWayland
 * windows carry `window_properties.class` instead. The framing is the i3 IPC
 * format: `"i3-ipc"` magic + u32 LE payload length + u32 LE message type.
 */
class SwayActiveAppResolver(
    private val socketPath: Path,
) : LinuxActiveAppResolver {

    private val logger = KotlinLogging.logger {}

    override fun getActiveApp(): LinuxActiveApp? =
        runCatching {
            requestTree()?.let { tree ->
                parseFocusedAppName(tree)?.let { appName ->
                    LinuxActiveApp(appName, x11Window = null)
                }
            }
        }.getOrElse { e ->
            logger.warn(e) { "Failed to resolve active app via Sway IPC" }
            null
        }

    private fun requestTree(): String? =
        SocketChannel.open(UnixDomainSocketAddress.of(socketPath)).use { channel ->
            val request = ByteBuffer.allocate(MAGIC.size + 8).order(ByteOrder.LITTLE_ENDIAN)
            request.put(MAGIC)
            request.putInt(0)
            request.putInt(GET_TREE)
            request.flip()
            while (request.hasRemaining()) {
                channel.write(request)
            }

            val header = ByteBuffer.allocate(MAGIC.size + 8).order(ByteOrder.LITTLE_ENDIAN)
            if (!readFully(channel, header)) {
                return null
            }
            header.flip()
            val magic = ByteArray(MAGIC.size)
            header.get(magic)
            if (!magic.contentEquals(MAGIC)) {
                logger.warn { "Sway IPC reply has invalid magic" }
                return null
            }
            val length = header.int
            header.int // message type, unused
            if (length <= 0 || length > MAX_REPLY_BYTES) {
                logger.warn { "Sway IPC reply has implausible length $length" }
                return null
            }
            val payload = ByteBuffer.allocate(length)
            if (!readFully(channel, payload)) {
                return null
            }
            payload.flip()
            Charsets.UTF_8.decode(payload).toString()
        }

    private fun readFully(
        channel: SocketChannel,
        buffer: ByteBuffer,
    ): Boolean {
        while (buffer.hasRemaining()) {
            if (channel.read(buffer) < 0) {
                return false
            }
        }
        return true
    }

    companion object {

        private val MAGIC = "i3-ipc".toByteArray(Charsets.US_ASCII)

        private const val GET_TREE = 4

        private const val MAX_REPLY_BYTES = 16 * 1024 * 1024

        private val jsonUtils = getJsonUtils()

        fun detect(env: (String) -> String?): SwayActiveAppResolver? =
            env("SWAYSOCK")
                ?.let(Path::of)
                ?.takeIf(Files::exists)
                ?.let { SwayActiveAppResolver(it) }

        /**
         * Finds the `focused: true` node in a `GET_TREE` reply and returns its
         * app name, or null when nothing (or only a container/workspace) has
         * focus.
         */
        fun parseFocusedAppName(treeJson: String): String? =
            runCatching {
                (jsonUtils.JSON.parseToJsonElement(treeJson) as? JsonObject)?.let { root ->
                    findFocusedNode(root)?.let { appNameOf(it) }
                }
            }.getOrNull()

        private fun findFocusedNode(node: JsonObject): JsonObject? {
            if ((node["focused"] as? JsonPrimitive)?.booleanOrNull == true) {
                return node
            }
            for (key in listOf("nodes", "floating_nodes")) {
                (node[key] as? JsonArray)?.forEach { child ->
                    (child as? JsonObject)?.let { childNode ->
                        findFocusedNode(childNode)?.let { return it }
                    }
                }
            }
            return null
        }

        private fun appNameOf(node: JsonObject): String? =
            (node["app_id"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: ((node["window_properties"] as? JsonObject)?.get("class") as? JsonPrimitive)
                    ?.contentOrNull
                    ?.takeIf { it.isNotBlank() }
    }
}
