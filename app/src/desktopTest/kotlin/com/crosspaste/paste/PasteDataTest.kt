package com.crosspaste.paste

import com.crosspaste.paste.item.ColorPasteItem
import com.crosspaste.paste.item.CreatePasteItemHelper.createHtmlPasteItem
import com.crosspaste.paste.item.CreatePasteItemHelper.createTextPasteItem
import com.crosspaste.paste.item.PasteText
import com.crosspaste.utils.DateUtils
import com.crosspaste.utils.getJsonUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PasteDataTest {

    private val jsonUtils = getJsonUtils()

    private fun createTestPasteData(
        text: String = "test",
        pasteType: PasteType = PasteType.TEXT_TYPE,
        pasteState: Int = PasteState.LOADED,
        collection: List<com.crosspaste.paste.item.PasteItem> = listOf(),
    ): PasteData {
        val textItem = createTextPasteItem(text = text)
        return PasteData(
            appInstanceId = "test-app",
            pasteAppearItem = textItem,
            pasteCollection = PasteCollection(collection),
            pasteType = pasteType.type,
            size = textItem.size,
            hash = textItem.hash,
            pasteState = pasteState,
            createTime = DateUtils.nowEpochMilliseconds(),
        )
    }

    // --- getType ---

    @Test
    fun `getType returns correct PasteType for TEXT`() {
        val pd = createTestPasteData(pasteType = PasteType.TEXT_TYPE)
        assertEquals(PasteType.TEXT_TYPE, pd.getType())
    }

    @Test
    fun `getType returns INVALID_TYPE for unknown type`() {
        val pd = createTestPasteData().copy(pasteType = 999)
        assertEquals(PasteType.INVALID_TYPE, pd.getType())
    }

    // --- getPasteItem ---

    @Test
    fun `getPasteItem returns item when type matches`() {
        val pd = createTestPasteData()
        val item = pd.getPasteItem(PasteText::class)
        assertNotNull(item)
    }

    @Test
    fun `getPasteItem returns null when type does not match`() {
        val pd = createTestPasteData()
        val item = pd.getPasteItem(ColorPasteItem::class)
        assertNull(item)
    }

    @Test
    fun `getPasteItem returns null when pasteAppearItem is null`() {
        val pd = createTestPasteData().copy(pasteAppearItem = null)
        val item = pd.getPasteItem(PasteText::class)
        assertNull(item)
    }

    // --- getPasteAppearItems ---

    @Test
    fun `getPasteAppearItems returns appear item plus collection items`() {
        val textItem = createTextPasteItem(text = "main")
        val extraItem = createTextPasteItem(text = "extra")
        val pd =
            PasteData(
                appInstanceId = "test",
                pasteAppearItem = textItem,
                pasteCollection = PasteCollection(listOf(extraItem)),
                pasteType = PasteType.TEXT_TYPE.type,
                size = textItem.size + extraItem.size,
                hash = textItem.hash,
                pasteState = PasteState.LOADED,
                createTime = DateUtils.nowEpochMilliseconds(),
            )
        val items = pd.getPasteAppearItems()
        assertEquals(2, items.size)
    }

    @Test
    fun `getPasteAppearItems with null appear item returns only collection`() {
        val extraItem = createTextPasteItem(text = "only")
        val pd =
            PasteData(
                appInstanceId = "test",
                pasteAppearItem = null,
                pasteCollection = PasteCollection(listOf(extraItem)),
                pasteType = PasteType.TEXT_TYPE.type,
                size = extraItem.size,
                hash = "",
                pasteState = PasteState.LOADED,
                createTime = DateUtils.nowEpochMilliseconds(),
            )
        val items = pd.getPasteAppearItems()
        assertEquals(1, items.size)
    }

    @Test
    fun `getPasteAppearItems with empty collection returns only appear item`() {
        val pd = createTestPasteData()
        val items = pd.getPasteAppearItems()
        assertEquals(1, items.size)
    }

    // --- isValid ---

    @Test
    fun `isValid returns true for LOADED paste with valid text item`() {
        val pd = createTestPasteData(pasteState = PasteState.LOADED)
        assertTrue(pd.isValid())
    }

    @Test
    fun `isValid returns true for LOADING paste`() {
        val pd = createTestPasteData(pasteState = PasteState.LOADING)
        assertTrue(pd.isValid())
    }

    @Test
    fun `isValid returns false for DELETED paste`() {
        val pd = createTestPasteData(pasteState = PasteState.DELETED)
        assertFalse(pd.isValid())
    }

    @Test
    fun `isValid returns false when appear item type does not match paste type`() {
        val textItem = createTextPasteItem(text = "text")
        val pd =
            PasteData(
                appInstanceId = "test",
                pasteAppearItem = textItem,
                pasteCollection = PasteCollection(listOf()),
                pasteType = PasteType.COLOR_TYPE.type, // mismatch
                size = textItem.size,
                hash = textItem.hash,
                pasteState = PasteState.LOADED,
                createTime = DateUtils.nowEpochMilliseconds(),
            )
        assertFalse(pd.isValid())
    }

    // --- isFileType ---

    @Test
    fun `isFileType returns false for text only paste`() {
        val pd = createTestPasteData()
        assertFalse(pd.isFileType())
    }

    // --- getTypeName ---

    @Test
    fun `getTypeName returns correct name for each type`() {
        assertEquals("text", createTestPasteData(pasteType = PasteType.TEXT_TYPE).getTypeName())
    }

    // --- getPasteCoordinate ---

    @Test
    fun `getPasteCoordinate returns correct coordinate`() {
        val pd = createTestPasteData().copy(id = 42)
        val coord = pd.getPasteCoordinate()
        assertEquals(42, coord.id)
        assertEquals("test-app", coord.appInstanceId)
    }

    @Test
    fun `getPasteCoordinate with override id`() {
        val pd = createTestPasteData().copy(id = 42)
        val coord = pd.getPasteCoordinate(id = 100)
        assertEquals(100, coord.id)
    }

    // --- getSummary ---

    @Test
    fun `getSummary returns loading string when LOADING`() {
        val pd = createTestPasteData(pasteState = PasteState.LOADING)
        assertEquals("Loading...", pd.getSummary("Loading...", "Unknown"))
    }

    @Test
    fun `getSummary returns text content for TEXT type`() {
        val pd = createTestPasteData(text = "hello world")
        val summary = pd.getSummary("Loading...", "Unknown")
        assertEquals("hello world", summary)
    }

    @Test
    fun `getSummary for HTML type prefers text item from collection`() {
        val htmlItem = createHtmlPasteItem(html = "<p>Hello</p>")
        val textItem = createTextPasteItem(text = "Hello plain")
        val pd =
            PasteData(
                appInstanceId = "test",
                pasteAppearItem = htmlItem,
                pasteCollection = PasteCollection(listOf(textItem)),
                pasteType = PasteType.HTML_TYPE.type,
                size = htmlItem.size,
                hash = htmlItem.hash,
                pasteState = PasteState.LOADED,
                createTime = DateUtils.nowEpochMilliseconds(),
            )
        val summary = pd.getSummary("Loading...", "Unknown")
        assertEquals("Hello plain", summary)
    }

    // --- toJson / fromJson roundtrip ---

    @Test
    fun `toJson and fromJson roundtrip preserves key fields`() {
        val pd = createTestPasteData(text = "roundtrip test")
        val json = pd.toJson()
        val restored = PasteData.fromJson(json)

        assertNotNull(restored)
        assertEquals(pd.appInstanceId, restored.appInstanceId)
        assertEquals(pd.pasteType, restored.pasteType)
        assertEquals(pd.hash, restored.hash)
        assertEquals(pd.size, restored.size)
    }

    @Test
    fun `fromJson returns null for invalid json`() {
        assertNull(PasteData.fromJson("not valid json"))
    }

    @Test
    fun `fromJson returns null for empty json`() {
        assertNull(PasteData.fromJson("{}"))
    }

    // --- buildRawSearchContent ---

    @Test
    fun `buildRawSearchContent with both source and content`() {
        val result = PasteData.buildRawSearchContent("Chrome", "hello")
        assertEquals("chrome hello", result)
    }

    @Test
    fun `buildRawSearchContent with source only`() {
        val result = PasteData.buildRawSearchContent("Chrome", null)
        assertEquals("chrome", result)
    }

    @Test
    fun `buildRawSearchContent with content only`() {
        val result = PasteData.buildRawSearchContent(null, "hello")
        assertEquals("hello", result)
    }

    @Test
    fun `buildRawSearchContent with both null`() {
        val result = PasteData.buildRawSearchContent(null, null)
        assertNull(result)
    }
}
