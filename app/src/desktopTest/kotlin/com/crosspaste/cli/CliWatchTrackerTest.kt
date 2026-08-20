package com.crosspaste.cli

import com.crosspaste.paste.PasteCollection
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteState
import com.crosspaste.paste.PasteType
import com.crosspaste.paste.item.CreatePasteItemHelper.createTextPasteItem
import com.crosspaste.utils.getJsonUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CliWatchTrackerTest {

    // Guard against PasteItem/JsonUtils circular class initialization
    private val jsonUtils = getJsonUtils()

    private companion object {
        const val WINDOW_LIMIT = 3
    }

    private fun row(
        id: Long,
        createTime: Long,
        pasteState: Int = PasteState.LOADED,
    ): PasteData {
        val item = createTextPasteItem(text = "paste-$id")
        return PasteData(
            id = id,
            appInstanceId = "test-instance",
            pasteAppearItem = item,
            pasteCollection = PasteCollection(listOf()),
            pasteType = PasteType.TEXT_TYPE.type,
            source = "Test",
            size = item.size,
            hash = item.hash,
            createTime = createTime,
            pasteState = pasteState,
        )
    }

    /** Newest-first, as the DAO window query orders. */
    private fun window(vararg rows: PasteData): List<PasteData> =
        rows
            .sortedWith(
                compareByDescending<PasteData> {
                    it.createTime
                }.thenByDescending { it.id },
            ).take(WINDOW_LIMIT)

    @Test
    fun `baseline rows are never replayed`() {
        val baseline = window(row(1, 100), row(2, 200))
        val tracker = CliWatchTracker(baseline, WINDOW_LIMIT)

        assertTrue(tracker.onWindow(baseline).isEmpty())
    }

    @Test
    fun `a new loaded row arrives exactly once`() {
        val tracker = CliWatchTracker(window(row(1, 100)), WINDOW_LIMIT)

        val snapshot = window(row(1, 100), row(2, 200))
        assertEquals(listOf(2L), tracker.onWindow(snapshot).map { it.id })
        assertTrue(tracker.onWindow(snapshot).isEmpty())
    }

    @Test
    fun `multiple arrivals come oldest first`() {
        val tracker = CliWatchTracker(window(row(1, 100)), WINDOW_LIMIT)

        val snapshot = window(row(1, 100), row(2, 200), row(3, 300))
        assertEquals(listOf(2L, 3L), tracker.onWindow(snapshot).map { it.id })
    }

    @Test
    fun `a loading row arrives only when it finishes loading`() {
        val tracker = CliWatchTracker(window(row(1, 100)), WINDOW_LIMIT)

        val loading = window(row(1, 100), row(2, 200, PasteState.LOADING))
        assertTrue(tracker.onWindow(loading).isEmpty())

        val loaded = window(row(1, 100), row(2, 200))
        assertEquals(listOf(2L), tracker.onWindow(loaded).map { it.id })
    }

    @Test
    fun `a loading baseline row arrives when it finishes loading`() {
        val baseline = window(row(1, 100), row(2, 200, PasteState.LOADING))
        val tracker = CliWatchTracker(baseline, WINDOW_LIMIT)

        val loaded = window(row(1, 100), row(2, 200))
        assertEquals(listOf(2L), tracker.onWindow(loaded).map { it.id })
        assertTrue(tracker.onWindow(loaded).isEmpty())
    }

    @Test
    fun `a re-copy createTime bump arrives again`() {
        val tracker = CliWatchTracker(window(row(1, 100), row(2, 200)), WINDOW_LIMIT)

        val snapshot = window(row(1, 300), row(2, 200))
        assertEquals(listOf(1L), tracker.onWindow(snapshot).map { it.id })
        assertTrue(tracker.onWindow(snapshot).isEmpty())
    }

    @Test
    fun `an old row sliding back into a full window is not an arrival`() {
        // Full baseline window: rows below its floor exist but are unseen
        val baseline = window(row(2, 200), row(3, 300), row(4, 400))
        assertEquals(WINDOW_LIMIT, baseline.size)
        val tracker = CliWatchTracker(baseline, WINDOW_LIMIT)

        // Deleting row 4 slides pre-subscription row 1 back into view
        val snapshot = window(row(1, 100), row(2, 200), row(3, 300))
        assertTrue(tracker.onWindow(snapshot).isEmpty())
    }

    @Test
    fun `an unseen row is an arrival when the baseline window was not full`() {
        // A non-full baseline saw every existing row, so there is no floor
        val tracker = CliWatchTracker(window(row(5, 500)), WINDOW_LIMIT)

        val snapshot = window(row(1, 100), row(5, 500))
        assertEquals(listOf(1L), tracker.onWindow(snapshot).map { it.id })
    }

    @Test
    fun `a deleted row simply disappears`() {
        val tracker = CliWatchTracker(window(row(1, 100), row(2, 200)), WINDOW_LIMIT)

        assertTrue(tracker.onWindow(window(row(1, 100))).isEmpty())
    }

    @Test
    fun `a re-copied slid-back row does arrive`() {
        val baseline = window(row(2, 200), row(3, 300), row(4, 400))
        val tracker = CliWatchTracker(baseline, WINDOW_LIMIT)

        // Row 1 slides back in (adopted silently), then gets re-copied
        tracker.onWindow(window(row(1, 100), row(2, 200), row(3, 300)))
        val snapshot = window(row(1, 500), row(2, 200), row(3, 300))
        assertEquals(listOf(1L), tracker.onWindow(snapshot).map { it.id })
    }
}
