package com.crosspaste.i18n

import androidx.compose.runtime.snapshots.Snapshot
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.db.TestDriverFactory
import com.crosspaste.db.createDatabase
import com.crosspaste.db.task.SqlTaskDao
import com.crosspaste.i18n.DesktopGlobalCopywriter.Companion.EMPTY_STRING
import com.crosspaste.i18n.SupportedLanguages.EN
import com.crosspaste.i18n.SupportedLanguages.LANGUAGE_LIST
import com.crosspaste.presist.OneFilePersist
import com.crosspaste.task.TaskExecutor
import com.crosspaste.utils.DesktopLocaleUtils
import okio.Path.Companion.toOkioPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlobalCopywriterTest {

    private val dummyArgs: Array<Any?> = arrayOfNulls<Any?>(4).also { it.fill("") }

    private fun createCopywriter(): Pair<DesktopGlobalCopywriter, CommonConfigManager> {
        val configDirPath = Files.createTempDirectory("configDir").toOkioPath()
        configDirPath.toFile().deleteOnExit()
        val configPath = configDirPath.resolve("appConfig.json")

        @Suppress("UNCHECKED_CAST")
        val configManager =
            DesktopConfigManager(
                OneFilePersist(configPath),
                DesktopLocaleUtils,
            ) as CommonConfigManager

        configManager.updateConfig("language", "")

        val database = createDatabase(TestDriverFactory())

        val taskDao = SqlTaskDao(database)

        val copywriter = DesktopGlobalCopywriter(configManager, lazy { TaskExecutor(listOf(), taskDao) }, taskDao)
        return copywriter to configManager
    }

    @Test
    fun testDefaultLanguage() {
        val (copywriter, _) = createCopywriter()
        assertEquals(EN, copywriter.language())
        assertEquals(EN, copywriter.languageFlow.value)
    }

    @Test
    fun testSwitchLanguagePublishesToFlowAndConfig() {
        val (copywriter, configManager) = createCopywriter()

        copywriter.switchLanguage("zh")

        assertEquals("zh", copywriter.language())
        assertEquals("zh", copywriter.languageFlow.value)
        assertEquals("zh", configManager.getCurrentConfig().language)
        assertEquals(DesktopCopywriter("zh").getText("current_language"), copywriter.getText("current_language"))
    }

    @Test
    fun testGetTextReadsSnapshotStateSoComposablesRecompose() {
        val (copywriter, _) = createCopywriter()

        // Composables call getText() directly; they can only recompose on switchLanguage()
        // if that read goes through Compose snapshot state (ComposeGlobalCopywriter's mirror).
        val reads = mutableListOf<Any>()
        Snapshot.observe(readObserver = { reads += it }) {
            copywriter.getText("current_language")
        }
        assertTrue(reads.isNotEmpty(), "getText() must read snapshot state")

        val writes = mutableListOf<Any>()
        Snapshot.observe(writeObserver = { writes += it }) {
            copywriter.switchLanguage("zh")
        }
        assertTrue(writes.isNotEmpty(), "switchLanguage() must write the snapshot state read by getText()")
        assertTrue(reads.intersect(writes.toSet()).isNotEmpty(), "the state written must be the state read")
    }

    @Test
    fun testI18nKeys() {
        val languageList = LANGUAGE_LIST
        val copywriterMap: Map<String, Copywriter> =
            languageList.associateWith { language ->
                DesktopCopywriter(language)
            }

        val enCopywriter = copywriterMap[EN]

        assertTrue(enCopywriter != null, "English copywriter should not be null")

        val enKeys = enCopywriter.getKeys()

        copywriterMap.filter { (key, _) -> key != EN }.forEach { (key, copywriter) ->
            val keys = copywriter.getKeys()

            assertTrue(
                enKeys.containsAll(keys),
                "All keys in $key should be a subset of English keys. Missing keys: ${keys - enKeys.joinToString(",")}",
            )

            // String.format ignores surplus arguments, so hand every key more
            // placeholders than any value uses; the argument count itself is
            // checked against English in I18nConsistencyTest.
            keys.forEach {
                assertTrue { copywriter.getText(it, *dummyArgs) != EMPTY_STRING }
            }
        }
    }
}
