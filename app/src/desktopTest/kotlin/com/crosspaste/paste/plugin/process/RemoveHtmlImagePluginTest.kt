package com.crosspaste.paste.plugin.process

import com.crosspaste.paste.item.CreatePasteItemHelper.createHtmlPasteItem
import com.crosspaste.paste.item.CreatePasteItemHelper.createImagesPasteItem
import com.crosspaste.paste.item.CreatePasteItemHelper.createTextPasteItem
import com.crosspaste.paste.item.HtmlPasteItem
import com.crosspaste.paste.item.ImagesPasteItem
import com.crosspaste.paste.item.PasteCoordinate
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.presist.SingleFileInfoTree
import com.crosspaste.utils.getJsonUtils
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RemoveHtmlImagePluginTest {

    @Suppress("unused")
    private val jsonUtils = getJsonUtils()

    private val userDataPathProvider: UserDataPathProvider = mockk(relaxed = true)
    private val plugin = RemoveHtmlImagePlugin(userDataPathProvider)
    private val coord = PasteCoordinate(id = 1L, appInstanceId = "test")

    private fun createTestImageItem(): ImagesPasteItem =
        createImagesPasteItem(
            relativePathList = listOf("test.png"),
            fileInfoTreeMap = mapOf("test.png" to SingleFileInfoTree(size = 100L, hash = "abc")),
        )

    @Test
    fun `empty list returns empty`() {
        val result = plugin.process(coord, emptyList(), null)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `no image item preserves all items`() {
        val htmlItem = createHtmlPasteItem(html = "<html><body><p>text</p></body></html>")
        val items: List<PasteItem> = listOf(htmlItem)
        val result = plugin.process(coord, items, null)
        assertEquals(1, result.size)
        assertTrue(result[0] is HtmlPasteItem)
    }

    @Test
    fun `image with single img html removes html`() {
        val htmlItem = createHtmlPasteItem(html = "<html><body><img src='test.png'/></body></html>")
        val imageItem = createTestImageItem()
        val items: List<PasteItem> = listOf(htmlItem, imageItem)
        val result = plugin.process(coord, items, null)
        assertEquals(1, result.size)
        assertTrue(result[0] is ImagesPasteItem)
    }

    @Test
    fun `image with multi-element html preserves html`() {
        val htmlItem = createHtmlPasteItem(html = "<html><body><p>text</p><img src='test.png'/></body></html>")
        val imageItem = createTestImageItem()
        val items: List<PasteItem> = listOf(htmlItem, imageItem)
        val result = plugin.process(coord, items, null)
        assertEquals(2, result.size)
        assertTrue(result.any { it is HtmlPasteItem })
        assertTrue(result.any { it is ImagesPasteItem })
    }

    @Test
    fun `image without html keeps all items`() {
        val textItem = createTextPasteItem(text = "hello")
        val imageItem = createTestImageItem()
        val items: List<PasteItem> = listOf(textItem, imageItem)
        val result = plugin.process(coord, items, null)
        assertEquals(2, result.size)
    }

    @Test
    fun `html with complex body not single img preserves html`() {
        val htmlItem = createHtmlPasteItem(html = "<html><body><div><img src='test.png'/></div></body></html>")
        val imageItem = createTestImageItem()
        val items: List<PasteItem> = listOf(htmlItem, imageItem)
        val result = plugin.process(coord, items, null)
        // The <div> wrapper means the body's direct child is <div>, not <img>
        assertEquals(2, result.size)
    }
}
