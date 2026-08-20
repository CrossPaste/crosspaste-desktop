package com.crosspaste.paste.item

import com.crosspaste.db.paste.PasteDao
import com.crosspaste.paste.PasteCollection
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.SearchContentService
import com.crosspaste.paste.item.CreatePasteItemHelper.createTextPasteItem
import com.crosspaste.paste.item.CreatePasteItemHelper.createUrlPasteItem
import com.crosspaste.utils.getJsonUtils
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UpdatePasteItemHelperTest {

    @Suppress("unused")
    private val jsonUtils = getJsonUtils()

    private val pasteDao = mockk<PasteDao>()
    private val pasteItemReader = mockk<PasteItemReader>()
    private val searchContentService = mockk<SearchContentService>()
    private val helper = UpdatePasteItemHelper(pasteDao, pasteItemReader, searchContentService)

    @Test
    fun `updateTitle uses UTF-8 byte delta when replacing a title`() =
        runTest {
            val oldTitle = "旧标题"
            val newTitle = "新标题🙂"
            val original =
                createUrlPasteItem(
                    url = "https://example.com",
                    extraInfo = JsonObject(mapOf(PasteItemProperties.TITLE to JsonPrimitive(oldTitle))),
                )
            val addedSize = slot<Long>()
            stubUpdate(addedSize)

            val result = helper.updateTitle(createPasteData(original), newTitle, original)

            assertTrue(result.isSuccess)
            assertEquals(
                newTitle.encodeToByteArray().size.toLong() - oldTitle.encodeToByteArray().size.toLong(),
                addedSize.captured,
            )
        }

    @Test
    fun `updateName uses UTF-8 byte delta when replacing a name`() =
        runTest {
            val oldName = "旧名称"
            val newName = "新名称🙂"
            val original =
                createTextPasteItem(
                    text = "content",
                    extraInfo = JsonObject(mapOf(PasteItemProperties.NAME to JsonPrimitive(oldName))),
                )
            val addedSize = slot<Long>()
            stubUpdate(addedSize)

            val result = helper.updateName(createPasteData(original), newName, original)

            assertTrue(result.isSuccess)
            assertEquals(
                newName.encodeToByteArray().size.toLong() - oldName.encodeToByteArray().size.toLong(),
                addedSize.captured,
            )
        }

    private fun stubUpdate(addedSize: io.mockk.CapturingSlot<Long>) {
        every { pasteItemReader.getSearchContent(any()) } returns "content"
        every { searchContentService.createSearchContent(any(), any<List<String>>()) } returns "content"
        coEvery {
            pasteDao.updatePasteAppearItemIfUnchanged(
                expectedPasteData = any(),
                pasteItem = any(),
                pasteSearchContent = any(),
                addedSize = capture(addedSize),
            )
        } returns true
    }

    @Test
    fun `updateTitle refuses to apply onto a row whose url changed`() =
        runTest {
            // The Open Graph writeback runs after a network fetch; if the URL
            // was edited meanwhile, the row hash no longer matches the item
            // the fetch was made for and the guarded update reports a miss
            val original = createUrlPasteItem(url = "https://old.example.com")
            every { pasteItemReader.getSearchContent(any()) } returns "content"
            every { searchContentService.createSearchContent(any(), any<List<String>>()) } returns "content"
            val expectedPasteData = slot<PasteData>()
            coEvery {
                pasteDao.updatePasteAppearItemIfUnchanged(
                    expectedPasteData = capture(expectedPasteData),
                    pasteItem = any(),
                    pasteSearchContent = any(),
                    addedSize = any(),
                )
            } returns false

            val pasteData = createPasteData(original)
            val result = helper.updateTitle(pasteData, "Late Title", original)

            assertTrue(result.isFailure)
            // The guard must be the complete snapshot the fetch started from.
            assertEquals(pasteData, expectedPasteData.captured)
        }

    private fun createPasteData(item: PasteItem): PasteData =
        PasteData(
            id = 1L,
            appInstanceId = "test",
            pasteAppearItem = item,
            pasteCollection = PasteCollection(emptyList()),
            pasteType = item.getPasteType().type,
            size = item.size,
            hash = item.hash,
        )
}
