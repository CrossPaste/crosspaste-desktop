package com.crosspaste.paste.item

import com.crosspaste.paste.item.CreatePasteItemHelper.copy
import com.crosspaste.paste.item.CreatePasteItemHelper.createColorPasteItem
import com.crosspaste.paste.item.CreatePasteItemHelper.createHtmlPasteItem
import com.crosspaste.paste.item.CreatePasteItemHelper.createRtfPasteItem
import com.crosspaste.paste.item.CreatePasteItemHelper.createTextPasteItem
import com.crosspaste.paste.item.CreatePasteItemHelper.createUrlPasteItem
import com.crosspaste.presist.SingleFileInfoTree
import com.crosspaste.utils.getJsonUtils
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CreatePasteItemHelperTest {

    // Eagerly initialize JsonUtils to avoid circular class initialization between
    // PasteItem.Companion (which calls getJsonUtils()) and TextPasteItem
    @Suppress("unused")
    private val jsonUtils = getJsonUtils()

    // --- TextPasteItem ---

    @Test
    fun `createTextPasteItem creates item with correct text`() {
        val item = createTextPasteItem(text = "Hello World")
        assertEquals("Hello World", item.text)
    }

    @Test
    fun `createTextPasteItem computes hash from text bytes`() {
        val item = createTextPasteItem(text = "test")
        assertTrue(item.hash.isNotEmpty())
    }

    @Test
    fun `createTextPasteItem computes size from text bytes`() {
        val text = "Hello"
        val item = createTextPasteItem(text = text)
        assertEquals(text.encodeToByteArray().size.toLong(), item.size)
    }

    @Test
    fun `createTextPasteItem same text produces same hash`() {
        val item1 = createTextPasteItem(text = "identical")
        val item2 = createTextPasteItem(text = "identical")
        assertEquals(item1.hash, item2.hash)
    }

    @Test
    fun `createTextPasteItem different text produces different hash`() {
        val item1 = createTextPasteItem(text = "alpha")
        val item2 = createTextPasteItem(text = "beta")
        assertNotEquals(item1.hash, item2.hash)
    }

    @Test
    fun `createTextPasteItem stores identifiers`() {
        val ids = listOf("text/plain", "text/html")
        val item = createTextPasteItem(identifiers = ids, text = "test")
        assertEquals(ids, item.identifiers)
    }

    @Test
    fun `createTextPasteItem handles empty text`() {
        val item = createTextPasteItem(text = "")
        assertEquals("", item.text)
        assertEquals(0L, item.size)
    }

    @Test
    fun `createTextPasteItem handles unicode text`() {
        val text = "你好世界 🌍"
        val item = createTextPasteItem(text = text)
        assertEquals(text, item.text)
        assertEquals(text.encodeToByteArray().size.toLong(), item.size)
    }

    @Test
    fun `TextPasteItem copy creates new item with different text`() {
        val original = createTextPasteItem(identifiers = listOf("id"), text = "original")
        val copy = original.copy(text = "modified")
        assertEquals("modified", copy.text)
        assertEquals(original.identifiers, copy.identifiers)
        assertNotEquals(original.hash, copy.hash)
    }

    // --- HtmlPasteItem ---

    @Test
    fun `createHtmlPasteItem creates item with correct html`() {
        val html = "<p>Hello</p>"
        val item = createHtmlPasteItem(html = html)
        assertEquals(html, item.html)
    }

    @Test
    fun `createHtmlPasteItem computes hash from html bytes`() {
        val item = createHtmlPasteItem(html = "<b>test</b>")
        assertTrue(item.hash.isNotEmpty())
    }

    @Test
    fun `createHtmlPasteItem computes size from html bytes`() {
        val html = "<b>Hello</b>"
        val item = createHtmlPasteItem(html = html)
        assertEquals(html.encodeToByteArray().size.toLong(), item.size)
    }

    @Test
    fun `HtmlPasteItem copy preserves identifiers and changes html`() {
        val original = createHtmlPasteItem(identifiers = listOf("text/html"), html = "<p>old</p>")
        val copy = original.copy(html = "<p>new</p>")
        assertEquals("<p>new</p>", copy.html)
        assertEquals(original.identifiers, copy.identifiers)
    }

    // --- RtfPasteItem ---

    @Test
    fun `createRtfPasteItem creates item with correct rtf`() {
        val rtf = "{\\rtf1 Hello}"
        val item = createRtfPasteItem(rtf = rtf)
        assertEquals(rtf, item.rtf)
    }

    @Test
    fun `createRtfPasteItem computes hash and size`() {
        val rtf = "{\\rtf1 test}"
        val item = createRtfPasteItem(rtf = rtf)
        assertTrue(item.hash.isNotEmpty())
        assertEquals(rtf.encodeToByteArray().size.toLong(), item.size)
    }

    @Test
    fun `RtfPasteItem copy changes rtf content`() {
        val original = createRtfPasteItem(rtf = "{\\rtf1 old}")
        val copy = original.copy(rtf = "{\\rtf1 new}")
        assertEquals("{\\rtf1 new}", copy.rtf)
    }

    // --- UrlPasteItem ---

    @Test
    fun `createUrlPasteItem creates item with correct url`() {
        val url = "https://example.com"
        val item = createUrlPasteItem(url = url)
        assertEquals(url, item.url)
    }

    @Test
    fun `createUrlPasteItem computes hash from url bytes`() {
        val item = createUrlPasteItem(url = "https://example.com")
        assertTrue(item.hash.isNotEmpty())
    }

    @Test
    fun `createUrlPasteItem size includes title when provided`() {
        val url = "https://example.com"
        val title = "Example"
        val extraInfo = JsonObject(mapOf(PasteItemProperties.TITLE to JsonPrimitive(title)))
        val item = createUrlPasteItem(url = url, extraInfo = extraInfo)
        assertEquals(url.encodeToByteArray().size.toLong() + title.length.toLong(), item.size)
    }

    @Test
    fun `createUrlPasteItem size without title is just url size`() {
        val url = "https://example.com"
        val item = createUrlPasteItem(url = url)
        assertEquals(url.encodeToByteArray().size.toLong(), item.size)
    }

    @Test
    fun `UrlPasteItem copy creates new item with different url`() {
        val original = createUrlPasteItem(identifiers = listOf("url"), url = "https://old.com")
        val copy = original.copy(url = "https://new.com")
        assertTrue(copy is UrlPasteItem)
        assertEquals("https://new.com", (copy as UrlPasteItem).url)
    }

    // --- ColorPasteItem ---

    @Test
    fun `createColorPasteItem creates item with correct color`() {
        val item = createColorPasteItem(color = 0xFF0000)
        assertEquals(0xFF0000, item.color)
    }

    @Test
    fun `createColorPasteItem hash is string of color`() {
        val item = createColorPasteItem(color = 255)
        assertEquals("255", item.hash)
    }

    @Test
    fun `createColorPasteItem size is always 8`() {
        val item = createColorPasteItem(color = 0)
        assertEquals(8L, item.size)
    }

    @Test
    fun `ColorPasteItem copy changes color value`() {
        val original = createColorPasteItem(color = 0xFF0000)
        val copy = original.copy(color = 0x00FF00)
        assertEquals(0x00FF00, copy.color)
        assertEquals(original.identifiers, copy.identifiers)
    }

    // --- FilesPasteItem ---

    @Test
    fun `createFilesPasteItem computes hash from sorted file names`() {
        val fileInfoTreeMap =
            mapOf(
                "b.txt" to SingleFileInfoTree(size = 100, hash = "hash_b"),
                "a.txt" to SingleFileInfoTree(size = 200, hash = "hash_a"),
            )
        val item =
            CreatePasteItemHelper.createFilesPasteItem(
                relativePathList = listOf("a.txt", "b.txt"),
                fileInfoTreeMap = fileInfoTreeMap,
            )
        assertTrue(item.hash.isNotEmpty())
        assertEquals(300L, item.size)
        assertEquals(2L, item.count)
    }

    @Test
    fun `createFilesPasteItem with single file`() {
        val fileInfoTreeMap =
            mapOf(
                "test.txt" to SingleFileInfoTree(size = 50, hash = "hash_test"),
            )
        val item =
            CreatePasteItemHelper.createFilesPasteItem(
                relativePathList = listOf("test.txt"),
                fileInfoTreeMap = fileInfoTreeMap,
            )
        assertEquals(50L, item.size)
        assertEquals(1L, item.count)
    }

    // --- ImagesPasteItem ---

    @Test
    fun `createImagesPasteItem computes correct size and count`() {
        val fileInfoTreeMap =
            mapOf(
                "img1.png" to SingleFileInfoTree(size = 1024, hash = "hash_img1"),
                "img2.jpg" to SingleFileInfoTree(size = 2048, hash = "hash_img2"),
            )
        val item =
            CreatePasteItemHelper.createImagesPasteItem(
                relativePathList = listOf("img1.png", "img2.jpg"),
                fileInfoTreeMap = fileInfoTreeMap,
            )
        assertEquals(3072L, item.size)
        assertEquals(2L, item.count)
        assertTrue(item.hash.isNotEmpty())
    }
}
