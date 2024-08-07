package com.crosspaste.listen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.crosspaste.app.AppFileType
import com.crosspaste.listener.KeyboardKey
import com.crosspaste.listener.ShortcutKeys
import com.crosspaste.listener.ShortcutKeysCore
import com.crosspaste.path.DesktopAppPathProvider
import com.crosspaste.platform.currentPlatform
import com.crosspaste.presist.DesktopOneFilePersist
import com.crosspaste.utils.getResourceUtils
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.Properties

class DesktopShortcutKeys(
    private val shortcutKeysLoader: ShortcutKeysLoader,
) : ShortcutKeys {

    companion object {
        const val PASTE = "paste"
        const val PASTE_LOCAL_LAST = "paste_local_last"
        const val PASTE_REMOTE_LAST = "paste_remote_last"
        const val SHOW_MAIN = "show_main"
        const val SHOW_SEARCH = "show_search"
        const val HIDE_WINDOW = "hide_window"
        const val SWITCH_MONITOR_PASTEBOARD = "switch_monitor_pasteboard"
        const val SWITCH_ENCRYPT = "switch_encrypt"
    }

    private val logger: KLogger = KotlinLogging.logger {}

    override var shortcutKeysCore by mutableStateOf(defaultKeysCore())

    init {
        try {
            loadKeysCore()?.let {
                shortcutKeysCore = it
            }
        } catch (e: Exception) {
            defaultKeysCore()
        }
    }

    private fun defaultKeysCore(): ShortcutKeysCore {
        val platform = currentPlatform()
        return shortcutKeysLoader.load(platform.name)
    }

    private fun loadKeysCore(): ShortcutKeysCore? {
        try {
            val shortcutKeysPropertiesPath =
                DesktopAppPathProvider
                    .resolve("shortcut-keys.properties", AppFileType.USER)
            if (!FileSystem.SYSTEM.exists(shortcutKeysPropertiesPath)) {
                val filePersist = DesktopOneFilePersist(shortcutKeysPropertiesPath)
                val platform = currentPlatform()
                val bytes =
                    getResourceUtils()
                        .readResourceBytes("shortcut_keys/${platform.name}.properties")
                filePersist.saveBytes(bytes)
            }

            val path = shortcutKeysPropertiesPath.toFile().toOkioPath()

            return shortcutKeysLoader.load(path)
        } catch (e: Exception) {
            logger.error(e) { "Failed to load shortcut keys" }
            return null
        }
    }

    override fun update(
        keyName: String,
        keys: List<KeyboardKey>,
    ) {
        try {
            val shortcutKeysPropertiesPath =
                DesktopAppPathProvider
                    .resolve("shortcut-keys.properties", AppFileType.USER)

            val properties = Properties()

            InputStreamReader(shortcutKeysPropertiesPath.toFile().inputStream(), StandardCharsets.UTF_8)
                .use { inputStreamReader -> properties.load(inputStreamReader) }

            properties.setProperty(keyName, keys.joinToString("+") { "${it.code}" })

            FileOutputStream(shortcutKeysPropertiesPath.toFile()).use { fileOutputStream ->
                OutputStreamWriter(fileOutputStream, Charsets.UTF_8).use { writer ->
                    properties.store(writer, "Comments")
                }
            }
            shortcutKeysCore = shortcutKeysLoader.load(shortcutKeysPropertiesPath)
        } catch (e: Exception) {
            logger.error(e) { "Failed to update shortcut keys" }
        }
    }
}
