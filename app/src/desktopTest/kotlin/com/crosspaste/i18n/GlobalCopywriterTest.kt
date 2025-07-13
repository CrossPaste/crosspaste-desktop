package com.crosspaste.i18n

import com.crosspaste.config.CommonConfigManager
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.db.TestDriverFactory
import com.crosspaste.db.createDatabase
import com.crosspaste.db.task.TaskDao
import com.crosspaste.i18n.DesktopGlobalCopywriter.Companion.EMPTY_STRING
import com.crosspaste.i18n.DesktopGlobalCopywriter.Companion.EN
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

        @Suppress("UNCHECKED_CAST")
        val configManager =
            DesktopConfigManager(
                OneFilePersist(configPath),
                DesktopDeviceUtils(platform),
                DesktopLocaleUtils,
            ) as CommonConfigManager

        configManager.updateConfig("language", "")

        val database = createDatabase(TestDriverFactory())

        val taskDao = TaskDao(database)

        val copywriter = DesktopGlobalCopywriter(configManager, lazy { TaskExecutor(listOf(), taskDao) }, taskDao)
        assertEquals(EN, copywriter.language())
    }

    @Test
    fun testI18nKeys() {
        val languageList = DesktopGlobalCopywriter.LANGUAGE_LIST
        val copywriterMap: Map<String, Copywriter> =
            languageList.associateWith { language ->
                DesktopCopywriter(language)
            }

        val enCopywriter = copywriterMap[EN]

        assertTrue(enCopywriter != null, "English copywriter should not be null")

        val enKeys = enCopywriter.getKeys()

        copywriterMap.filter { (key, _) -> key != EN }.forEach {
                (key, copywriter) ->
            val keys = copywriter.getKeys()

            assertTrue(
                enKeys.containsAll(keys),
                "All keys in $key should be a subset of English keys. Missing keys: ${keys - enKeys.joinToString(",")}",
            )

            keys.forEach {
                assertTrue { copywriter.getText(it, "") != EMPTY_STRING }
            }
        }
    }
}
