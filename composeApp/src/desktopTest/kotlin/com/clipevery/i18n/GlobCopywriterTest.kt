package com.clipevery.i18n

import com.clipevery.config.DefaultConfigManager
import com.clipevery.i18n.GlobalCopywriterImpl.Companion.EN
import com.clipevery.presist.DesktopOneFilePersist
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class GlobCopywriterTest {

    @Test
    fun testDefaultLanguage() {
        val configDirPath = Files.createTempDirectory("configDir")
        configDirPath.toFile().deleteOnExit()
        val configPath = configDirPath.resolve("appConfig.json")
        val configManager = DefaultConfigManager(DesktopOneFilePersist(configPath))

        configManager.updateConfig { config -> config.copy(language = "") }

        val copywriter = GlobalCopywriterImpl(configManager)
        assertEquals(EN, copywriter.language())
    }
}
