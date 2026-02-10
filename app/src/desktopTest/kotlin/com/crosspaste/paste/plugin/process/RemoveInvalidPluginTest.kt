package com.crosspaste.paste.plugin.process

import com.crosspaste.paste.item.CreatePasteItemHelper.createTextPasteItem
import com.crosspaste.paste.item.PasteCoordinate
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.paste.item.TextPasteItem
import com.crosspaste.utils.getJsonUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RemoveInvalidPluginTest {

    @Suppress("unused")
    private val jsonUtils = getJsonUtils()

    private val coord = PasteCoordinate(id = 1L, appInstanceId = "test")

    @Test
    fun `empty list returns empty`() {
        val result = RemoveInvalidPlugin.process(coord, emptyList(), null)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `valid items are preserved`() {
        val item = createTextPasteItem(text = "hello")
        val result = RemoveInvalidPlugin.process(coord, listOf(item), null)
        assertEquals(1, result.size)
    }

    @Test
    fun `invalid items are removed`() {
        // TextPasteItem with empty text/hash/zero size is invalid
        val invalidItem =
            TextPasteItem(
                identifiers = listOf(),
                hash = "",
                size = 0L,
                text = "",
            )
        val result = RemoveInvalidPlugin.process(coord, listOf(invalidItem), null)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `mixed valid and invalid items`() {
        val validItem = createTextPasteItem(text = "hello")
        val invalidItem =
            TextPasteItem(
                identifiers = listOf(),
                hash = "",
                size = 0L,
                text = "",
            )
        val result = RemoveInvalidPlugin.process(coord, listOf(validItem, invalidItem), null)
        assertEquals(1, result.size)
        assertTrue(result[0] is TextPasteItem)
        assertEquals("hello", (result[0] as TextPasteItem).text)
    }

    @Test
    fun `all valid items preserved`() {
        val items: List<PasteItem> =
            listOf(
                createTextPasteItem(text = "one"),
                createTextPasteItem(text = "two"),
                createTextPasteItem(text = "three"),
            )
        val result = RemoveInvalidPlugin.process(coord, items, null)
        assertEquals(3, result.size)
    }
}
