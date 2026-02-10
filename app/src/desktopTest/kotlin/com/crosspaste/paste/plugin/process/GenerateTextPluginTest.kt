package com.crosspaste.paste.plugin.process

import com.crosspaste.paste.item.CreatePasteItemHelper.createHtmlPasteItem
import com.crosspaste.paste.item.CreatePasteItemHelper.createTextPasteItem
import com.crosspaste.paste.item.HtmlPasteItem
import com.crosspaste.paste.item.PasteCoordinate
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.paste.item.TextPasteItem
import com.crosspaste.utils.getJsonUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GenerateTextPluginTest {

    @Suppress("unused")
    private val jsonUtils = getJsonUtils()

    private val coord = PasteCoordinate(id = 1L, appInstanceId = "test")

    @Test
    fun `empty list returns empty`() {
        val result = GenerateTextPlugin.process(coord, emptyList(), null)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `list with text only returns unchanged`() {
        val textItem = createTextPasteItem(text = "hello")
        val result = GenerateTextPlugin.process(coord, listOf(textItem), null)
        assertEquals(1, result.size)
        assertTrue(result[0] is TextPasteItem)
    }

    @Test
    fun `html without text generates text item`() {
        val htmlItem = createHtmlPasteItem(html = "<p>hello world</p>")
        val items: List<PasteItem> = listOf(htmlItem)
        val result = GenerateTextPlugin.process(coord, items, null)
        assertEquals(2, result.size)
        assertTrue(result.any { it is HtmlPasteItem })
        assertTrue(result.any { it is TextPasteItem })
    }

    @Test
    fun `html with existing text does not generate duplicate`() {
        val htmlItem = createHtmlPasteItem(html = "<p>hello</p>")
        val textItem = createTextPasteItem(text = "existing")
        val items: List<PasteItem> = listOf(htmlItem, textItem)
        val result = GenerateTextPlugin.process(coord, items, null)
        assertEquals(2, result.size)
        // No new text item added
        assertEquals(1, result.count { it is TextPasteItem })
    }

    @Test
    fun `text-only list returns unchanged`() {
        val textItem = createTextPasteItem(text = "plain text")
        val result = GenerateTextPlugin.process(coord, listOf(textItem), null)
        assertEquals(1, result.size)
        assertTrue(result[0] is TextPasteItem)
    }

    @Test
    fun `generated text from html has content`() {
        val htmlItem = createHtmlPasteItem(html = "<b>bold text</b>")
        val items: List<PasteItem> = listOf(htmlItem)
        val result = GenerateTextPlugin.process(coord, items, null)
        val textItem = result.filterIsInstance<TextPasteItem>().first()
        assertTrue(textItem.text.isNotEmpty())
    }
}
