package com.crosspaste.i18n

import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.db.TestDriverFactory
import com.crosspaste.db.createDatabase
import com.crosspaste.db.task.TaskDao
import com.crosspaste.i18n.GlobalCopywriterImpl.Companion.EN
import com.crosspaste.platform.DesktopPlatformProvider
import com.crosspaste.presist.OneFilePersist
import com.crosspaste.task.TaskExecutor
import com.crosspaste.utils.DesktopDeviceUtils
import com.crosspaste.utils.DesktopLocaleUtils
import okio.Path.Companion.toOkioPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlobalCopywriterTest {

    @Test
    fun testDefaultLanguage() {
        val configDirPath = Files.createTempDirectory("configDir").toOkioPath()
        configDirPath.toFile().deleteOnExit()
        val configPath = configDirPath.resolve("appConfig.json")

        val platform = DesktopPlatformProvider().getPlatform()

        val configManager =
            DesktopConfigManager(
                OneFilePersist(configPath),
                DesktopDeviceUtils(platform),
                DesktopLocaleUtils,
            )

        configManager.updateConfig("language", "")

        val database = createDatabase(TestDriverFactory())

        val taskDao = TaskDao(database)

        val copywriter = GlobalCopywriterImpl(configManager, lazy { TaskExecutor(listOf(), taskDao) }, taskDao)
        assertEquals(EN, copywriter.language())
    }

    @Test
    fun testI18nKeys() {
        val languageList = GlobalCopywriterImpl.LANGUAGE_LIST
        val copywriterMap: Map<String, Copywriter> =
            languageList.associateWith { language ->
                CopywriterImpl(language)
            }

        // Verify that all keys are the same
        val keys = copywriterMap.values.first().getKeys()
        copywriterMap.values.forEach { copywriter ->
            var diff = keys - copywriter.getKeys()
            assertTrue(diff.isEmpty(), diff.joinToString(","))

            diff = copywriter.getKeys() - keys
            assertTrue(diff.isEmpty(), diff.joinToString(","))
        }
    }
}
