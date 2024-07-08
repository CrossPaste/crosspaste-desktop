package com.crosspaste.i18n

import com.crosspaste.config.DefaultConfigManager
import com.crosspaste.i18n.GlobalCopywriterImpl.Companion.EN
import com.crosspaste.presist.DesktopOneFilePersist
import com.crosspaste.ui.base.DesktopToastManager
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class GlobCopywriterTest {

    @Test
    fun testDefaultLanguage() {
        val configDirPath = Files.createTempDirectory("configDir")
        configDirPath.toFile().deleteOnExit()
        val configPath = configDirPath.resolve("appConfig.json")

        lateinit var configManager: DefaultConfigManager

        configManager = DefaultConfigManager(DesktopOneFilePersist(configPath), DesktopToastManager(), lazy { GlobalCopywriterImpl(configManager) })

        configManager.updateConfig { config -> config.copy(language = "") }

        val copywriter = GlobalCopywriterImpl(configManager)
        assertEquals(EN, copywriter.language())
    }
}
