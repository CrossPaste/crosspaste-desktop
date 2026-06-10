package com.crosspaste.platform.linux

import com.crosspaste.utils.getJsonUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.ByteArrayOutputStream
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Path

/**
 * Resolves the focused app over Hyprland's IPC socket: one short-lived
 * connection per query, sending `j/activewindow` and parsing the `class` field
 * of the JSON reply (Hyprland reports `class` for both native Wayland and
 * XWayland windows). Hyprland answers and closes the socket immediately, the
 * same blocking round trip `hyprctl` performs.
 */
class HyprlandActiveAppResolver(
    private val socketPath: Path,
) : LinuxActiveAppResolver {

    private val logger = KotlinLogging.logger {}

    override fun getActiveApp(): LinuxActiveApp? =
        runCatching {
            parseActiveWindowClass(request("j/activewindow"))?.let { appName ->
                LinuxActiveApp(appName, x11Window = null)
            }
        }.getOrElse { e ->
            logger.warn(e) { "Failed to resolve active app via Hyprland IPC" }
            null
        }

    private fun request(command: String): String =
        SocketChannel.open(UnixDomainSocketAddress.of(socketPath)).use { channel ->
            val out = ByteBuffer.wrap(command.toByteArray(Charsets.UTF_8))
            while (out.hasRemaining()) {
                channel.write(out)
            }
            val result = ByteArrayOutputStream()
            val buffer = ByteBuffer.allocate(8192)
            while (channel.read(buffer) >= 0 && result.size() <= MAX_REPLY_BYTES) {
                buffer.flip()
                result.write(buffer.array(), 0, buffer.limit())
                buffer.clear()
            }
            result.toString(Charsets.UTF_8.name())
        }

    companion object {

        private const val MAX_REPLY_BYTES = 1024 * 1024

        private val jsonUtils = getJsonUtils()

        fun detect(env: (String) -> String?): HyprlandActiveAppResolver? {
            val signature = env("HYPRLAND_INSTANCE_SIGNATURE") ?: return null
            return listOfNotNull(
                env("XDG_RUNTIME_DIR")?.let { "$it/hypr/$signature/.socket.sock" },
                "/tmp/hypr/$signature/.socket.sock",
            ).map(Path::of)
                .firstOrNull(Files::exists)
                ?.let { HyprlandActiveAppResolver(it) }
        }

        /**
         * Extracts the window class from a `j/activewindow` reply. Hyprland
         * answers `{}` (or non-JSON like `Invalid`) when no window is focused —
         * both map to null.
         */
        fun parseActiveWindowClass(reply: String): String? =
            runCatching {
                val window = jsonUtils.JSON.parseToJsonElement(reply) as? JsonObject ?: return null
                (window["class"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            }.getOrNull()
    }
}
