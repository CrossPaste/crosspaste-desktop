package com.crosspaste.listen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.crosspaste.app.AppFileType
import com.crosspaste.listener.KeyboardKey
import com.crosspaste.listener.ShortcutKeys
import com.crosspaste.listener.ShortcutKeysCore
import com.crosspaste.path.DesktopAppPathProvider
import com.crosspaste.platform.getPlatform
import com.crosspaste.utils.DesktopResourceUtils
import com.crosspaste.utils.getFileUtils
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import okio.Path
import okio.Path.Companion.toOkioPath
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.Date
import java.util.Properties

class DesktopShortcutKeys(
    private val shortcutKeysLoader: ShortcutKeysLoader,
) : ShortcutKeys {

    companion object {
        const val PASTE = "paste"
        const val PASTE_PLAIN_TEXT = "paste_plain_text"
        const val PASTE_PRIMARY_TYPE = "paste_primary_type"
        const val PASTE_LOCAL_LAST = "paste_local_last"
        const val PASTE_REMOTE_LAST = "paste_remote_last"
        const val SHOW_MAIN = "show_main"
        const val SHOW_SEARCH = "show_search"
        const val HIDE_WINDOW = "hide_window"
        const val SWITCH_MONITOR_PASTEBOARD = "switch_monitor_pasteboard"
        const val SWITCH_ENCRYPT = "switch_encrypt"
    }

    private val logger: KLogger = KotlinLogging.logger {}

    private val platform = getPlatform()

    private val fileUtils = getFileUtils()

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
        return shortcutKeysLoader.load(platform.name)
    }

    private fun loadKeysCore(): ShortcutKeysCore? {
        try {
            val shortcutKeysPropertiesPath =
                DesktopAppPathProvider
                    .resolve("shortcut-keys.properties", AppFileType.USER)

            val platformProperties =
                DesktopResourceUtils.loadProperties(
                    "shortcut_keys/${platform.name}.properties",
                )

            if (!fileUtils.existFile(shortcutKeysPropertiesPath)) {
                writeProperties(platformProperties, shortcutKeysPropertiesPath)
            } else {
                val properties = Properties()
                InputStreamReader(shortcutKeysPropertiesPath.toFile().inputStream(), StandardCharsets.UTF_8)
                    .use { inputStreamReader -> properties.load(inputStreamReader) }
                for (key in platformProperties.keys) {
                    if (!properties.containsKey(key)) {
                        properties.setProperty(key.toString(), platformProperties.getProperty(key.toString()))
                    }
                }
                writeProperties(properties, shortcutKeysPropertiesPath)
            }

            val path = shortcutKeysPropertiesPath.toFile().toOkioPath()

            return shortcutKeysLoader.load(path)
        } catch (e: Exception) {
            logger.error(e) { "Failed to load shortcut keys" }
            return null
        }
    }

    private fun writeProperties(
        properties: Properties,
        path: Path,
    ) {
        path.toFile().outputStream().use { fileOutputStream ->
            OutputStreamWriter(fileOutputStream, Charsets.UTF_8).use { writer ->
                properties.store(writer, Date().toString())
            }
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
