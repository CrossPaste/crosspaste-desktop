package com.crosspaste.paste.plugin.process

import com.crosspaste.paste.item.CreatePasteItemHelper.createTextPasteItem
import com.crosspaste.paste.item.CreatePasteItemHelper.createUrlPasteItem
import com.crosspaste.paste.item.PasteCoordinate
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.paste.item.TextPasteItem
import com.crosspaste.paste.item.UrlPasteItem
import com.crosspaste.utils.getJsonUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GenerateUrlPluginTest {

    @Suppress("unused")
    private val jsonUtils = getJsonUtils()

    private val coord = PasteCoordinate(id = 1L, appInstanceId = "test")

    @Test
    fun `empty list returns empty`() {
        val result = GenerateUrlPlugin.process(coord, emptyList(), null)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `text with valid URL generates UrlPasteItem`() {
        val textItem = createTextPasteItem(text = "https://example.com")
        val items: List<PasteItem> = listOf(textItem)
        val result = GenerateUrlPlugin.process(coord, items, null)
        assertEquals(2, result.size)
        assertTrue(result.any { it is TextPasteItem })
        assertTrue(result.any { it is UrlPasteItem })
    }

    @Test
    fun `text with invalid URL does not generate UrlPasteItem`() {
        val textItem = createTextPasteItem(text = "not a url at all")
        val items: List<PasteItem> = listOf(textItem)
        val result = GenerateUrlPlugin.process(coord, items, null)
        assertEquals(1, result.size)
        assertTrue(result[0] is TextPasteItem)
    }

    @Test
    fun `existing UrlPasteItem prevents duplicate generation`() {
        val textItem = createTextPasteItem(text = "https://example.com")
        val urlItem = createUrlPasteItem(url = "https://other.com")
        val items: List<PasteItem> = listOf(textItem, urlItem)
        val result = GenerateUrlPlugin.process(coord, items, null)
        assertEquals(2, result.size)
        // Should not add another URL item
        assertEquals(1, result.count { it is UrlPasteItem })
    }

    @Test
    fun `generated URL item preserves identifiers from text item`() {
        val textItem =
            createTextPasteItem(
                identifiers = listOf("text/plain"),
                text = "https://example.com",
            )
        val items: List<PasteItem> = listOf(textItem)
        val result = GenerateUrlPlugin.process(coord, items, null)
        val urlItem = result.filterIsInstance<UrlPasteItem>().first()
        assertEquals(listOf("text/plain"), urlItem.identifiers)
    }

    @Test
    fun `http URL is also valid`() {
        val textItem = createTextPasteItem(text = "http://example.com")
        val items: List<PasteItem> = listOf(textItem)
        val result = GenerateUrlPlugin.process(coord, items, null)
        assertTrue(result.any { it is UrlPasteItem })
    }
}
