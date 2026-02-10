package com.crosspaste.paste

import com.crosspaste.paste.item.CreatePasteItemHelper.createColorPasteItem
import com.crosspaste.paste.item.CreatePasteItemHelper.createHtmlPasteItem
import com.crosspaste.paste.item.CreatePasteItemHelper.createTextPasteItem
import com.crosspaste.paste.item.CreatePasteItemHelper.createUrlPasteItem
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.paste.item.TextPasteItem
import com.crosspaste.utils.getJsonUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PasteCollectionTest {

    // Eagerly initialize JsonUtils to avoid circular class initialization between
    // PasteItem.Companion (which calls getJsonUtils()) and TextPasteItem
    @Suppress("unused")
    private val jsonUtils = getJsonUtils()

    // --- Construction ---

    @Test
    fun `empty PasteCollection has no items`() {
        val collection = PasteCollection(listOf())
        assertTrue(collection.pasteItems.isEmpty())
    }

    @Test
    fun `PasteCollection preserves item order`() {
        val item1 = createTextPasteItem(text = "first")
        val item2 = createTextPasteItem(text = "second")
        val item3 = createTextPasteItem(text = "third")
        val collection = PasteCollection(listOf(item1, item2, item3))

        assertEquals(3, collection.pasteItems.size)
        assertEquals("first", (collection.pasteItems[0] as TextPasteItem).text)
        assertEquals("second", (collection.pasteItems[1] as TextPasteItem).text)
        assertEquals("third", (collection.pasteItems[2] as TextPasteItem).text)
    }

    // --- toJson / fromJson ---

    @Test
    fun `empty collection roundtrip`() {
        val original = PasteCollection(listOf())
        val json = original.toJson()
        val restored = PasteCollection.fromJson(json)
        assertTrue(restored.pasteItems.isEmpty())
    }

    @Test
    fun `single text item collection roundtrip`() {
        val textItem = createTextPasteItem(text = "hello")
        val original = PasteCollection(listOf(textItem))

        val json = original.toJson()
        val restored = PasteCollection.fromJson(json)

        assertEquals(1, restored.pasteItems.size)
        val restoredItem = restored.pasteItems[0] as TextPasteItem
        assertEquals("hello", restoredItem.text)
        assertEquals(textItem.hash, restoredItem.hash)
    }

    @Test
    fun `multiple mixed items collection roundtrip`() {
        val items: List<PasteItem> =
            listOf(
                createTextPasteItem(text = "text content"),
                createUrlPasteItem(url = "https://example.com"),
                createColorPasteItem(color = 0xFF0000.toInt()),
            )
        val original = PasteCollection(items)

        val json = original.toJson()
        val restored = PasteCollection.fromJson(json)

        assertEquals(3, restored.pasteItems.size)
    }

    @Test
    fun `html item collection roundtrip preserves html`() {
        val htmlItem = createHtmlPasteItem(html = "<div>Hello <b>World</b></div>")
        val original = PasteCollection(listOf(htmlItem))

        val json = original.toJson()
        val restored = PasteCollection.fromJson(json)

        assertEquals(1, restored.pasteItems.size)
    }
}
