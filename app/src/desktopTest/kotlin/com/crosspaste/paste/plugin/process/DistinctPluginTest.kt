package com.crosspaste.paste.plugin.process

import com.crosspaste.paste.item.CreatePasteItemHelper.createTextPasteItem
import com.crosspaste.paste.item.CreatePasteItemHelper.createUrlPasteItem
import com.crosspaste.paste.item.PasteCoordinate
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.paste.item.TextPasteItem
import com.crosspaste.paste.item.UrlPasteItem
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.utils.getJsonUtils
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DistinctPluginTest {

    @Suppress("unused")
    private val jsonUtils = getJsonUtils()

    private val userDataPathProvider: UserDataPathProvider = mockk(relaxed = true)
    private val plugin = DistinctPlugin(userDataPathProvider)
    private val coord = PasteCoordinate(id = 1L, appInstanceId = "test")

    @Test
    fun `empty list returns empty`() {
        val result = plugin.process(coord, emptyList(), null)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `single text item passes through`() {
        val item = createTextPasteItem(text = "hello")
        val result = plugin.process(coord, listOf(item), null)
        assertEquals(1, result.size)
    }

    @Test
    fun `multiple text items keeps only first`() {
        val item1 = createTextPasteItem(text = "first")
        val item2 = createTextPasteItem(text = "second")
        val items: List<PasteItem> = listOf(item1, item2)
        val result = plugin.process(coord, items, null)
        assertEquals(1, result.size)
        assertTrue(result[0] is TextPasteItem)
        assertEquals("first", (result[0] as TextPasteItem).text)
    }

    @Test
    fun `different types each keep one`() {
        val textItem = createTextPasteItem(text = "hello")
        val urlItem = createUrlPasteItem(url = "https://example.com")
        val items: List<PasteItem> = listOf(textItem, urlItem)
        val result = plugin.process(coord, items, null)
        assertEquals(2, result.size)
        assertTrue(result.any { it is TextPasteItem })
        assertTrue(result.any { it is UrlPasteItem })
    }

    @Test
    fun `multiple URL items keeps only first`() {
        val url1 = createUrlPasteItem(url = "https://example.com")
        val url2 = createUrlPasteItem(url = "https://other.com")
        val items: List<PasteItem> = listOf(url1, url2)
        val result = plugin.process(coord, items, null)
        assertEquals(1, result.size)
        assertTrue(result[0] is UrlPasteItem)
    }

    @Test
    fun `mixed types with duplicates deduplicates each type`() {
        val text1 = createTextPasteItem(text = "a")
        val text2 = createTextPasteItem(text = "b")
        val url1 = createUrlPasteItem(url = "https://1.com")
        val url2 = createUrlPasteItem(url = "https://2.com")
        val items: List<PasteItem> = listOf(text1, text2, url1, url2)
        val result = plugin.process(coord, items, null)
        assertEquals(2, result.size)
        assertEquals(1, result.count { it is TextPasteItem })
        assertEquals(1, result.count { it is UrlPasteItem })
    }
}
