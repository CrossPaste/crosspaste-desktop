package com.crosspaste.paste

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PasteImportSelectionTest {

    @Test
    fun `select updates path and clears outcome`() {
        val selection = PasteImportSelection()
        val path1 = "/tmp/test1.data".toPath()
        val path2 = "/tmp/test2.data".toPath()

        selection.select(path1)
        assertEquals(path1, selection.path.value)
        assertNull(selection.outcome.value)

        selection.clear()
        assertNull(selection.path.value)

        selection.select(path2)
        assertEquals(path2, selection.path.value)
    }

    @Test
    fun `dismissOutcome clears outcome`() {
        val selection = PasteImportSelection()
        val path = "/tmp/test.data".toPath()

        var resultCallback: ((PasteImportResult) -> Unit)? = null
        val mockService =
            mockk<PasteImportService> {
                every { import(any(), any(), any()) } answers {
                    resultCallback = thirdArg()
                }
            }
        val mockFactory =
            mockk<PasteImportParamFactory<Any>> {
                every { createPasteImportParam(any()) } returns mockk()
            }

        selection.select(path)
        selection.startImport(mockService, mockFactory)
        resultCallback?.invoke(PasteImportResult.Completed(5, 5))

        assertNotNull(selection.outcome.value)
        selection.dismissOutcome()
        assertNull(selection.outcome.value)
    }

    @Test
    fun `startImport guards against concurrent imports and state modifications`() {
        val selection = PasteImportSelection()
        val path = "/tmp/archive.data".toPath()
        val otherPath = "/tmp/other.data".toPath()

        var progressCallback: ((Float) -> Unit)? = null
        var resultCallback: ((PasteImportResult) -> Unit)? = null

        val mockService =
            mockk<PasteImportService> {
                every { import(any(), any(), any()) } answers {
                    progressCallback = secondArg()
                    resultCallback = thirdArg()
                }
            }
        val mockFactory =
            mockk<PasteImportParamFactory<Any>> {
                every { createPasteImportParam(any()) } returns mockk()
            }

        selection.select(path)
        selection.startImport(mockService, mockFactory)

        assertTrue(selection.isImporting.value)
        assertEquals(0f, selection.progress.value)

        // While importing, selecting another path or clearing must be ignored
        selection.select(otherPath)
        assertEquals(path, selection.path.value)

        selection.clear()
        assertEquals(path, selection.path.value)

        // Duplicate startImport must be ignored
        selection.startImport(mockService, mockFactory)
        verify(exactly = 1) { mockService.import(any(), any(), any()) }

        // Progress updates
        progressCallback?.invoke(0.5f)
        assertEquals(0.5f, selection.progress.value)

        // NaN progress is sanitized to 0f
        progressCallback?.invoke(Float.NaN)
        assertEquals(0f, selection.progress.value)

        // Complete import
        resultCallback?.invoke(PasteImportResult.Completed(10, 10))

        assertFalse(selection.isImporting.value)
        assertEquals(0f, selection.progress.value)
        assertNull(selection.path.value)

        val outcome = selection.outcome.value
        assertNotNull(outcome)
        assertEquals("archive.data", outcome.fileName)
        assertEquals(PasteImportResult.Completed(10, 10), outcome.result)
    }

    @Test
    fun `PasteImportResult Completed semantic properties`() {
        val empty = PasteImportResult.Completed(0, 0)
        assertTrue(empty.isEmpty)
        assertFalse(empty.isAllSuccess)
        assertFalse(empty.isPartial)
        assertFalse(empty.isAllFailed)

        val allSuccess = PasteImportResult.Completed(5, 5)
        assertFalse(allSuccess.isEmpty)
        assertTrue(allSuccess.isAllSuccess)
        assertFalse(allSuccess.isPartial)
        assertFalse(allSuccess.isAllFailed)

        val partial = PasteImportResult.Completed(3, 5)
        assertFalse(partial.isEmpty)
        assertFalse(partial.isAllSuccess)
        assertTrue(partial.isPartial)
        assertFalse(partial.isAllFailed)

        val allFailed = PasteImportResult.Completed(0, 5)
        assertFalse(allFailed.isEmpty)
        assertFalse(allFailed.isAllSuccess)
        assertFalse(allFailed.isPartial)
        assertTrue(allFailed.isAllFailed)
    }
}
