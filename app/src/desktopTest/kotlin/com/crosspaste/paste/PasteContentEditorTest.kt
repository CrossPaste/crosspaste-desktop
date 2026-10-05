package com.crosspaste.paste

import com.crosspaste.app.AppInfo
import com.crosspaste.db.TestDriverFactory
import com.crosspaste.db.createDatabase
import com.crosspaste.db.paste.SqlPasteDao
import com.crosspaste.paste.item.ColorPasteItem
import com.crosspaste.paste.item.CreatePasteItemHelper.createColorPasteItem
import com.crosspaste.paste.item.CreatePasteItemHelper.createHtmlPasteItem
import com.crosspaste.paste.item.CreatePasteItemHelper.createTextPasteItem
import com.crosspaste.paste.item.CreatePasteItemHelper.createUrlPasteItem
import com.crosspaste.paste.item.DefaultPasteItemReader
import com.crosspaste.paste.item.HtmlPasteItem
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.paste.item.PasteItemProperties
import com.crosspaste.paste.item.TextPasteItem
import com.crosspaste.paste.item.UrlPasteItem
import com.crosspaste.utils.DateUtils
import com.crosspaste.utils.getJsonUtils
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PasteContentEditorTest {

    // Eagerly initialize JsonUtils to avoid circular class initialization between
    // PasteItem.Companion (which calls getJsonUtils()) and TextPasteItem
    @Suppress("unused")
    private val jsonUtils = getJsonUtils()

    private val searchContentService =
        object : SearchContentService {
            override fun createSearchContent(
                source: String?,
                searchContentList: List<String>,
            ): String = searchContentList.joinToString(" ")

            override fun createSearchTerms(queryString: String): List<String> = listOf(queryString)
        }

    private val pasteItemReader = DefaultPasteItemReader()

    private val pasteDao =
        SqlPasteDao(
            appInfo = AppInfo("test-instance", "1.0.0", "abc", "testUser"),
            database = createDatabase(TestDriverFactory()),
            pasteItemReader = pasteItemReader,
            searchContentService = searchContentService,
            taskSubmitter = mockk(relaxed = true),
            userDataPathProvider = mockk(relaxed = true),
        )

    private val editor = PasteContentEditor(pasteDao, pasteItemReader, searchContentService)

    private suspend fun storePaste(
        appearItem: PasteItem,
        companions: List<PasteItem>,
        pasteType: PasteType,
    ): PasteData {
        val id =
            pasteDao.createPasteData(
                PasteData(
                    appInstanceId = "test-instance",
                    pasteAppearItem = appearItem,
                    pasteCollection = PasteCollection(companions),
                    pasteType = pasteType.type,
                    size = appearItem.size + companions.sumOf { it.size },
                    hash = appearItem.hash,
                    pasteState = PasteState.LOADED,
                    createTime = DateUtils.nowEpochMilliseconds(),
                ),
            )
        return assertNotNull(pasteDao.getNoDeletePasteData(id))
    }

    @Test
    fun `html edit re-derives the plain-text companion and drops underivable ones`() =
        runTest {
            val html =
                createHtmlPasteItem(
                    html = "<p>old words</p>",
                    extraInfo = buildJsonObject { put(PasteItemProperties.BACKGROUND, 0xFF112233.toInt()) },
                )
            val pasteData =
                storePaste(
                    appearItem = html,
                    companions =
                        listOf(
                            createTextPasteItem(text = "old words"),
                            createUrlPasteItem(url = "https://old.example.com"),
                        ),
                    pasteType = PasteType.HTML_TYPE,
                )
            val newHtml = "<p>new words</p>"

            val outcome = editor.updateContent(pasteData, newHtml, pasteData.hash)

            assertIs<PasteContentEditor.EditOutcome.Updated>(outcome)
            val stored = assertNotNull(pasteDao.getNoDeletePasteData(pasteData.id))
            val storedHtml = assertIs<HtmlPasteItem>(stored.pasteAppearItem)
            assertEquals(newHtml, storedHtml.html)
            assertEquals(html.getBackgroundColor(), storedHtml.getBackgroundColor())
            val companions = stored.pasteCollection.pasteItems
            assertEquals(1, companions.size)
            assertEquals("new words", assertIs<TextPasteItem>(companions.single()).text)
            assertTrue(companions.none { it is UrlPasteItem })
        }

    @Test
    fun `color edit re-derives the plain-text companion`() =
        runTest {
            val red = createColorPasteItem(color = 0xFFFF0000.toInt())
            val pasteData =
                storePaste(
                    appearItem = red,
                    companions = listOf(createTextPasteItem(text = red.toHexString())),
                    pasteType = PasteType.COLOR_TYPE,
                )
            val blue = 0xFF0000FF.toInt()

            val outcome = editor.updateColor(pasteData, blue)

            assertIs<PasteContentEditor.EditOutcome.Updated>(outcome)
            val stored = assertNotNull(pasteDao.getNoDeletePasteData(pasteData.id))
            val storedColor = assertIs<ColorPasteItem>(stored.pasteAppearItem)
            assertEquals(blue, storedColor.color)
            val companion = assertIs<TextPasteItem>(stored.pasteCollection.pasteItems.single())
            assertEquals(storedColor.toHexString(), companion.text)
        }

    @Test
    fun `edit from a stale snapshot is rejected instead of overwriting`() =
        runTest {
            val pasteData =
                storePaste(
                    appearItem = createTextPasteItem(text = "first"),
                    companions = listOf(),
                    pasteType = PasteType.TEXT_TYPE,
                )
            assertIs<PasteContentEditor.EditOutcome.Updated>(
                editor.updateContent(pasteData, "second", pasteData.hash),
            )

            val outcome = editor.updateContent(pasteData, "third", pasteData.hash)

            assertIs<PasteContentEditor.EditOutcome.Conflict>(outcome)
            val stored = assertNotNull(pasteDao.getNoDeletePasteData(pasteData.id))
            assertEquals("second", assertIs<TextPasteItem>(stored.pasteAppearItem).text)
        }
}
