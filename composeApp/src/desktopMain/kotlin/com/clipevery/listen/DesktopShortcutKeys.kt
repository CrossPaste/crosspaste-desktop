package com.clipevery.listen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.clipevery.app.AppFileType
import com.clipevery.listener.ShortcutKeys
import com.clipevery.listener.ShortcutKeysCore
import com.clipevery.path.PathProvider
import com.clipevery.platform.currentPlatform
import com.clipevery.presist.DesktopOneFilePersist
import com.clipevery.utils.getResourceUtils
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.InputStreamReader
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
            val shortcutKeysPropertiesPath = pathProvider.resolve("shortcut-keys.properties", AppFileType.USER)
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
}
