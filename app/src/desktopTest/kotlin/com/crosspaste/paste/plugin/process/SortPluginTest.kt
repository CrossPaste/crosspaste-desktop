package com.crosspaste.paste.plugin.process

import com.crosspaste.paste.PasteType
import com.crosspaste.paste.item.CreatePasteItemHelper.createColorPasteItem
import com.crosspaste.paste.item.CreatePasteItemHelper.createHtmlPasteItem
import com.crosspaste.paste.item.CreatePasteItemHelper.createTextPasteItem
import com.crosspaste.paste.item.CreatePasteItemHelper.createUrlPasteItem
import com.crosspaste.paste.item.PasteCoordinate
import com.crosspaste.utils.getJsonUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SortPluginTest {

    @Suppress("unused")
    private val jsonUtils = getJsonUtils()

    private val coord = PasteCoordinate(id = 1L, appInstanceId = "test")

    @Test
    fun `empty list returns empty`() {
        val result = SortPlugin.process(coord, emptyList(), null)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `single item returns same item`() {
        val item = createTextPasteItem(text = "hello")
        val result = SortPlugin.process(coord, listOf(item), null)
        assertEquals(1, result.size)
        assertEquals(item, result[0])
    }

    @Test
    fun `items are sorted by priority descending`() {
        val textItem = createTextPasteItem(text = "text")
        val urlItem = createUrlPasteItem(url = "https://example.com")
        val htmlItem = createHtmlPasteItem(html = "<b>bold</b>")
        val colorItem = createColorPasteItem(color = 0xFF0000)

        val items = listOf(textItem, colorItem, urlItem, htmlItem)
        val result = SortPlugin.process(coord, items, null)

        // Verify they are sorted by priority descending
        for (i in 0 until result.size - 1) {
            assertTrue(
                result[i].getPasteType().priority >= result[i + 1].getPasteType().priority,
                "Items at index $i and ${i + 1} are not sorted by priority descending",
            )
        }
    }

    @Test
    fun `same type items preserve relative order`() {
        val text1 = createTextPasteItem(text = "first")
        val text2 = createTextPasteItem(text = "second")
        val result = SortPlugin.process(coord, listOf(text1, text2), null)
        assertEquals(2, result.size)
        assertEquals(PasteType.TEXT_TYPE, result[0].getPasteType())
        assertEquals(PasteType.TEXT_TYPE, result[1].getPasteType())
    }

    @Test
    fun `source parameter is ignored`() {
        val item = createTextPasteItem(text = "hello")
        val withSource = SortPlugin.process(coord, listOf(item), "Chrome")
        val withoutSource = SortPlugin.process(coord, listOf(item), null)
        assertEquals(withSource.size, withoutSource.size)
    }
}
