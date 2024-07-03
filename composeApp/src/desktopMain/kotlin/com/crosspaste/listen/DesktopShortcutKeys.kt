package com.crosspaste.listen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.crosspaste.app.AppFileType
import com.crosspaste.listener.KeyboardKey
import com.crosspaste.listener.ShortcutKeys
import com.crosspaste.listener.ShortcutKeysCore
import com.crosspaste.path.PathProvider
import com.crosspaste.platform.currentPlatform
import com.crosspaste.presist.DesktopOneFilePersist
import com.crosspaste.utils.getResourceUtils
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.Properties
import kotlin.io.path.exists

class DesktopShortcutKeys(
    private val pathProvider: PathProvider,
    private val shortcutKeysLoader: ShortcutKeysLoader,
) : ShortcutKeys {

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
        val properties = getResourceUtils().loadProperties("shortcut_keys/${platform.name}.properties")
        return shortcutKeysLoader.load(properties)
    }

    private fun loadKeysCore(): ShortcutKeysCore? {
        try {
            val shortcutKeysPropertiesPath =
                pathProvider
                    .resolve("shortcut-keys.properties", AppFileType.USER)
            if (!shortcutKeysPropertiesPath.exists()) {
                val filePersist = DesktopOneFilePersist(shortcutKeysPropertiesPath)
                val platform = currentPlatform()
                val bytes =
                    getResourceUtils()
                        .resourceInputStream("shortcut_keys/${platform.name}.properties")
                        .readBytes()
                filePersist.saveBytes(bytes)
            }

            val properties = Properties()

            InputStreamReader(shortcutKeysPropertiesPath.toFile().inputStream(), StandardCharsets.UTF_8)
                .use { inputStreamReader -> properties.load(inputStreamReader) }

            return shortcutKeysLoader.load(properties)
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
                pathProvider
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
            shortcutKeysCore = shortcutKeysLoader.load(properties)
        } catch (e: Exception) {
            logger.error(e) { "Failed to update shortcut keys" }
        }
    }
}
