package com.crosspaste.ui.model

import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteTag
import com.crosspaste.paste.PasteboardService
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class PasteSelectionViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val resultsFlow = MutableStateFlow<List<PasteData>>(listOf())

    private val searchViewModel =
        object : PasteSearchViewModel() {
            override val convertTerm: (String) -> List<String> = { listOf() }
            override val tagList: StateFlow<List<PasteTag>> = MutableStateFlow(listOf())
            override val searchResults: StateFlow<List<PasteData>> = resultsFlow
        }

    private val appWindowManager = mockk<DesktopAppWindowManager>(relaxed = true)
    private val pasteboardService = mockk<PasteboardService>(relaxed = true)

    private fun createVm(): PasteSelectionViewModel =
        PasteSelectionViewModel(appWindowManager, pasteboardService, searchViewModel)

    private fun createMockResults(count: Int): List<PasteData> =
        (0 until count).map { mockk<PasteData>(relaxed = true) }

    /**
     * Helper to get selectedIndexes via flow collection rather than .value,
     * because the combine uses WhileSubscribed which requires active subscription.
     */
    private suspend fun PasteSelectionViewModel.awaitSelectedIndexes(): List<Int> = selectedIndexes.first()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial focused element is PASTE_LIST`() {
        val vm = createVm()
        assertEquals(FocusedElement.PASTE_LIST, vm.focusedElement.value)
    }

    @Test
    fun `setFocusedElement changes focused element`() {
        val vm = createVm()
        vm.setFocusedElement(FocusedElement.SEARCH_INPUT)
        assertEquals(FocusedElement.SEARCH_INPUT, vm.focusedElement.value)
    }

    @Test
    fun `initial selected indexes is list of 0`() =
        runTest {
            val vm = createVm()
            assertEquals(listOf(0), vm.awaitSelectedIndexes())
        }

    @Test
    fun `selectPrev decrements selection index`() =
        runTest {
            resultsFlow.value = createMockResults(5)
            val vm = createVm()
            advanceUntilIdle()

            // Start at 0, go to index 3 via click, then prev
            vm.clickSelectedIndex(3)
            vm.selectPrev()
            advanceUntilIdle()

            assertEquals(listOf(2), vm.awaitSelectedIndexes())
        }

    @Test
    fun `selectPrev does not go below 0`() =
        runTest {
            resultsFlow.value = createMockResults(5)
            val vm = createVm()
            advanceUntilIdle()

            vm.selectPrev()
            advanceUntilIdle()

            assertEquals(listOf(0), vm.awaitSelectedIndexes())
        }

    @Test
    fun `selectNext increments selection index`() =
        runTest {
            resultsFlow.value = createMockResults(5)
            val vm = createVm()
            advanceUntilIdle()

            vm.selectNext()
            advanceUntilIdle()

            assertEquals(listOf(1), vm.awaitSelectedIndexes())
        }

    @Test
    fun `selectNext does not exceed result size`() =
        runTest {
            resultsFlow.value = createMockResults(3)
            val vm = createVm()
            advanceUntilIdle()

            vm.clickSelectedIndex(2)
            vm.selectNext()
            advanceUntilIdle()

            assertEquals(listOf(2), vm.awaitSelectedIndexes())
        }

    @Test
    fun `selectNext with empty results does nothing`() =
        runTest {
            resultsFlow.value = listOf()
            val vm = createVm()
            advanceUntilIdle()

            vm.selectNext()
            advanceUntilIdle()

            // Should remain at initial value
            assertEquals(listOf(0), vm.awaitSelectedIndexes())
        }

    @Test
    fun `clickSelectedIndex without shift replaces selection`() =
        runTest {
            resultsFlow.value = createMockResults(5)
            val vm = createVm()
            advanceUntilIdle()

            vm.clickSelectedIndex(3)
            advanceUntilIdle()

            assertEquals(listOf(3), vm.awaitSelectedIndexes())
        }

    @Test
    fun `clickSelectedIndex with shift adds to selection`() =
        runTest {
            resultsFlow.value = createMockResults(5)
            val vm = createVm()
            advanceUntilIdle()

            vm.clickSelectedIndex(1)
            vm.clickSelectedIndex(3, isShiftPressed = true)
            advanceUntilIdle()

            assertEquals(listOf(1, 3), vm.awaitSelectedIndexes())
        }

    @Test
    fun `clickSelectedIndex with shift removes already selected index`() =
        runTest {
            resultsFlow.value = createMockResults(5)
            val vm = createVm()
            advanceUntilIdle()

            vm.clickSelectedIndex(1)
            vm.clickSelectedIndex(3, isShiftPressed = true)
            // Now [1, 3] are selected. Remove 1 with shift.
            vm.clickSelectedIndex(1, isShiftPressed = true)
            advanceUntilIdle()

            assertEquals(listOf(3), vm.awaitSelectedIndexes())
        }

    @Test
    fun `shift-click on last selected item does not remove it`() =
        runTest {
            resultsFlow.value = createMockResults(5)
            val vm = createVm()
            advanceUntilIdle()

            vm.clickSelectedIndex(2)
            // Try to remove the only selected item
            vm.clickSelectedIndex(2, isShiftPressed = true)
            advanceUntilIdle()

            // Should keep at least one item selected
            assertEquals(listOf(2), vm.awaitSelectedIndexes())
        }

    @Test
    fun `initSelectIndex resets to 0`() =
        runTest {
            resultsFlow.value = createMockResults(5)
            val vm = createVm()
            advanceUntilIdle()

            vm.clickSelectedIndex(3)
            vm.initSelectIndex()
            advanceUntilIdle()

            assertEquals(listOf(0), vm.awaitSelectedIndexes())
        }

    @Test
    fun `selectPrev with multi-selection selects one before minimum`() =
        runTest {
            resultsFlow.value = createMockResults(5)
            val vm = createVm()
            advanceUntilIdle()

            vm.clickSelectedIndex(2)
            vm.clickSelectedIndex(4, isShiftPressed = true)
            // Selected: [2, 4]. selectPrev uses min (2) - 1 = 1
            vm.selectPrev()
            advanceUntilIdle()

            assertEquals(listOf(1), vm.awaitSelectedIndexes())
        }

    @Test
    fun `selectNext with multi-selection selects one after maximum`() =
        runTest {
            resultsFlow.value = createMockResults(5)
            val vm = createVm()
            advanceUntilIdle()

            vm.clickSelectedIndex(1)
            vm.clickSelectedIndex(3, isShiftPressed = true)
            // Selected: [1, 3]. selectNext uses max (3) + 1 = 4
            vm.selectNext()
            advanceUntilIdle()

            assertEquals(listOf(4), vm.awaitSelectedIndexes())
        }

    @Test
    fun `selectedIndexes filters out-of-range indexes when results shrink`() =
        runTest {
            resultsFlow.value = createMockResults(5)
            val vm = createVm()
            advanceUntilIdle()

            vm.clickSelectedIndex(4)
            advanceUntilIdle()
            assertEquals(listOf(4), vm.awaitSelectedIndexes())

            // Results shrink to size 3, index 4 is now out of range
            resultsFlow.value = createMockResults(3)
            advanceUntilIdle()

            // Should fall back to [0] since 4 >= 3
            assertEquals(listOf(0), vm.awaitSelectedIndexes())
        }
}
