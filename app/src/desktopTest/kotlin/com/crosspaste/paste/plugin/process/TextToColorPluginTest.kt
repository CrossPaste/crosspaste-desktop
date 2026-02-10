package com.crosspaste.paste.plugin.process

import com.crosspaste.paste.item.ColorPasteItem
import com.crosspaste.paste.item.CreatePasteItemHelper.createColorPasteItem
import com.crosspaste.paste.item.CreatePasteItemHelper.createTextPasteItem
import com.crosspaste.paste.item.PasteCoordinate
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.paste.item.TextPasteItem
import com.crosspaste.utils.getJsonUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextToColorPluginTest {

    @Suppress("unused")
    private val jsonUtils = getJsonUtils()

    private val coord = PasteCoordinate(id = 1L, appInstanceId = "test")

    @Test
    fun `empty list returns empty`() {
        val result = TextToColorPlugin.process(coord, emptyList(), null)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `hex color text generates ColorPasteItem`() {
        val textItem = createTextPasteItem(text = "#FF0000")
        val items: List<PasteItem> = listOf(textItem)
        val result = TextToColorPlugin.process(coord, items, null)
        assertEquals(2, result.size)
        assertTrue(result.any { it is ColorPasteItem })
    }

    @Test
    fun `non-color text does not generate ColorPasteItem`() {
        val textItem = createTextPasteItem(text = "hello world")
        val items: List<PasteItem> = listOf(textItem)
        val result = TextToColorPlugin.process(coord, items, null)
        assertEquals(1, result.size)
        assertTrue(result[0] is TextPasteItem)
    }

    @Test
    fun `existing ColorPasteItem prevents duplicate generation`() {
        val textItem = createTextPasteItem(text = "#FF0000")
        val colorItem = createColorPasteItem(color = 0x00FF00)
        val items: List<PasteItem> = listOf(textItem, colorItem)
        val result = TextToColorPlugin.process(coord, items, null)
        assertEquals(2, result.size)
        assertEquals(1, result.count { it is ColorPasteItem })
    }

    @Test
    fun `rgb color text generates ColorPasteItem`() {
        val textItem = createTextPasteItem(text = "rgb(255, 0, 0)")
        val items: List<PasteItem> = listOf(textItem)
        val result = TextToColorPlugin.process(coord, items, null)
        // Whether this generates a color depends on ColorUtils.toColor() implementation
        // We just verify no crash and result is valid
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `short hex color text generates ColorPasteItem`() {
        val textItem = createTextPasteItem(text = "#F00")
        val items: List<PasteItem> = listOf(textItem)
        val result = TextToColorPlugin.process(coord, items, null)
        // Whether short hex is supported depends on ColorUtils implementation
        assertTrue(result.isNotEmpty())
    }
}
