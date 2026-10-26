package com.crosspaste.ui.model

import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteTag
import com.crosspaste.paste.PasteType
import com.crosspaste.paste.PasteboardService
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
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

    private val tagsFlow = MutableStateFlow<List<PasteTag>>(listOf())

    private val searchViewModel =
        object : PasteSearchViewModel() {
            override val convertTerm: (String) -> List<String> = { listOf() }
            override val tagList: StateFlow<List<PasteTag>> = tagsFlow
            override val searchResults: StateFlow<List<PasteData>> = resultsFlow
        }

    private val appWindowManager = mockk<DesktopAppWindowManager>(relaxed = true)
    private val pasteboardService = mockk<PasteboardService>(relaxed = true)

    private fun createVm(): PasteSelectionViewModel =
        PasteSelectionViewModel(appWindowManager, pasteboardService, searchViewModel)

    private fun mockPasteData(id: Long): PasteData =
        mockk<PasteData>(relaxed = true) {
            every { this@mockk.id } returns
                id
        }

    private fun createMockResults(count: Int): List<PasteData> = (0 until count).map { mockPasteData(it.toLong()) }

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
    fun `updateFocus moves focus to the zone that gained it`() {
        val vm = createVm()
        vm.updateFocus(FocusedElement.SEARCH_INPUT, focused = true)
        assertEquals(FocusedElement.SEARCH_INPUT, vm.focusedElement.value)
    }

    @Test
    fun `losing focus clears the zone only while it still holds focus`() {
        val vm = createVm()
        vm.updateFocus(FocusedElement.SEARCH_INPUT, focused = true)
        vm.updateFocus(FocusedElement.SEARCH_INPUT, focused = false)
        assertEquals(FocusedElement.NONE, vm.focusedElement.value)

        // The new zone reports before the old one lets go
        vm.updateFocus(FocusedElement.SEARCH_INPUT, focused = true)
        vm.updateFocus(FocusedElement.PASTE_LIST, focused = true)
        vm.updateFocus(FocusedElement.SEARCH_INPUT, focused = false)
        assertEquals(FocusedElement.PASTE_LIST, vm.focusedElement.value)
    }

    private fun tags(vararg ids: Long) =
        ids.map { PasteTag(id = it, name = "tag$it", color = 0xFF0000, sortOrder = it) }

    private fun TestScope.collectEvents(vm: PasteSelectionViewModel): List<UIEvent> {
        val events = mutableListOf<UIEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiEvent.collect { events += it } }
        return events
    }

    @Test
    fun `entering the tag bar highlights the active filter tag`() =
        runTest {
            tagsFlow.value = tags(10, 20, 30)
            searchViewModel.updateTag(20)
            val vm = createVm()
            val events = collectEvents(vm)

            vm.requestTagBarFocus()

            assertEquals(1, vm.highlightedTagIndex.value)
            assertEquals(listOf<UIEvent>(RequestFocus(FocusedElement.TAG_BAR)), events)
        }

    @Test
    fun `entering the tag bar without a filter highlights the first chip`() {
        tagsFlow.value = tags(10, 20)
        val vm = createVm()
        vm.requestTagBarFocus()
        assertEquals(0, vm.highlightedTagIndex.value)
    }

    @Test
    fun `entering the tag bar from its right end highlights the add chip`() {
        tagsFlow.value = tags(10, 20)
        searchViewModel.updateTag(10)
        val vm = createVm()
        vm.requestTagBarFocus(atAddChip = true)
        assertEquals(2, vm.highlightedTagIndex.value)
    }

    @Test
    fun `right on the add chip moves on to the settings button`() =
        runTest {
            tagsFlow.value = tags(10, 20)
            val vm = createVm()
            vm.requestTagBarFocus()
            repeat(2) { vm.highlightNextTag() }
            assertEquals(2, vm.highlightedTagIndex.value)
            val events = collectEvents(vm)

            vm.highlightNextTag()

            assertEquals(2, vm.highlightedTagIndex.value)
            assertEquals(listOf<UIEvent>(RequestFocus(FocusedElement.SETTINGS)), events)
        }

    @Test
    fun `left on the first chip moves back to the type filter`() =
        runTest {
            tagsFlow.value = tags(10, 20)
            val vm = createVm()
            vm.requestTagBarFocus()
            vm.highlightNextTag()
            val events = collectEvents(vm)

            vm.highlightPrevTag()
            assertEquals(0, vm.highlightedTagIndex.value)
            assertEquals(listOf<UIEvent>(), events)

            vm.highlightPrevTag()
            assertEquals(listOf<UIEvent>(RequestFocus(FocusedElement.TYPE_FILTER)), events)
        }

    @Test
    fun `the type menu opens on the active type`() {
        val vm = createVm()
        vm.openTypeMenu()
        assertEquals(0, vm.typeMenuIndex.value)

        searchViewModel.updatePasteType(listOf(PasteType.IMAGE_TYPE.type))
        vm.openTypeMenu()
        assertEquals(vm.pasteTypeChoices.indexOf(PasteType.IMAGE_TYPE), vm.typeMenuIndex.value)
    }

    @Test
    fun `the type menu highlight stops at both ends`() {
        val vm = createVm()
        vm.openTypeMenu()
        vm.highlightPrevPasteType()
        assertEquals(0, vm.typeMenuIndex.value)
        repeat(vm.pasteTypeChoices.size + 2) { vm.highlightNextPasteType() }
        assertEquals(vm.pasteTypeChoices.lastIndex, vm.typeMenuIndex.value)
    }

    @Test
    fun `confirming applies the highlighted type and closes the menu`() {
        val vm = createVm()
        vm.openTypeMenu()
        vm.highlightNextPasteType()
        vm.confirmPasteType()
        assertEquals(listOf(PasteType.TYPES.first().type), searchViewModel.searchBaseParams.value.pasteTypeList)
        assertEquals(null, vm.typeMenuIndex.value)

        // Back to all types
        vm.openTypeMenu()
        vm.highlightPrevPasteType()
        vm.confirmPasteType()
        assertEquals(listOf(), searchViewModel.searchBaseParams.value.pasteTypeList)
    }

    @Test
    fun `picking a type hands focus back to the search input`() =
        runTest {
            val vm = createVm()
            vm.openTypeMenu()
            val events = collectEvents(vm)

            vm.selectPasteType(PasteType.URL_TYPE)

            assertEquals(listOf(PasteType.URL_TYPE.type), searchViewModel.searchBaseParams.value.pasteTypeList)
            assertEquals(listOf<UIEvent>(RequestFocus(FocusedElement.SEARCH_INPUT)), events)
        }

    @Test
    fun `cancelling the type menu returns to the search input but closing on focus loss does not`() =
        runTest {
            val vm = createVm()
            vm.updateFocus(FocusedElement.TYPE_FILTER, focused = true)
            vm.openTypeMenu()
            val events = collectEvents(vm)

            vm.cancelTypeMenu()
            assertEquals(null, vm.typeMenuIndex.value)
            assertEquals(listOf<UIEvent>(RequestFocus(FocusedElement.SEARCH_INPUT)), events)

            vm.openTypeMenu()
            vm.updateFocus(FocusedElement.TYPE_FILTER, focused = false)
            assertEquals(null, vm.typeMenuIndex.value)
            assertEquals(1, events.size)
        }

    @Test
    fun `reopening the window closes the type menu and resets the tag highlight`() =
        runTest {
            tagsFlow.value = tags(10, 20)
            val vm = createVm()
            vm.requestTagBarFocus(atAddChip = true)
            vm.openTypeMenu()

            vm.resetToTop()

            assertEquals(null, vm.typeMenuIndex.value)
            assertEquals(0, vm.highlightedTagIndex.value)
        }

    @Test
    fun `moving the highlight does nothing while the menu is closed`() {
        val vm = createVm()
        vm.highlightNextPasteType()
        vm.confirmPasteType()
        assertEquals(null, vm.typeMenuIndex.value)
        assertEquals(listOf(), searchViewModel.searchBaseParams.value.pasteTypeList)
    }

    @Test
    fun `the type menu closes when the type filter loses focus`() {
        val vm = createVm()
        vm.updateFocus(FocusedElement.TYPE_FILTER, focused = true)
        vm.openTypeMenu()
        vm.updateFocus(FocusedElement.TAG_BAR, focused = true)
        vm.updateFocus(FocusedElement.TYPE_FILTER, focused = false)
        assertEquals(null, vm.typeMenuIndex.value)
    }

    @Test
    fun `open settings is handed to the view`() =
        runTest {
            val vm = createVm()
            val events = collectEvents(vm)
            vm.openSettings()
            assertEquals(listOf<UIEvent>(RequestOpenSettings), events)
        }

    @Test
    fun `activating a tag toggles its filter`() {
        tagsFlow.value = tags(10, 20)
        val vm = createVm()
        vm.requestTagBarFocus()
        vm.highlightNextTag()

        vm.activateHighlightedTag()
        assertEquals(20, searchViewModel.searchBaseParams.value.tag)

        vm.activateHighlightedTag()
        assertEquals(null, searchViewModel.searchBaseParams.value.tag)
    }

    @Test
    fun `activating the add chip asks for a new tag`() =
        runTest {
            tagsFlow.value = tags(10)
            val vm = createVm()
            vm.requestTagBarFocus()
            vm.highlightNextTag()
            val events = collectEvents(vm)

            vm.activateHighlightedTag()

            assertEquals(listOf<UIEvent>(RequestCreateTag), events)
        }

    @Test
    fun `a highlight past the end after tags were deleted falls back to the add chip`() =
        runTest {
            tagsFlow.value = tags(10, 20, 30)
            val vm = createVm()
            vm.requestTagBarFocus()
            repeat(2) { vm.highlightNextTag() }
            tagsFlow.value = tags(10)
            val events = collectEvents(vm)

            vm.activateHighlightedTag()
            assertEquals(listOf<UIEvent>(RequestCreateTag), events)

            vm.highlightPrevTag()
            assertEquals(0, vm.highlightedTagIndex.value)
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

    @Test
    fun `selection follows the row by id when a new paste arrives on top`() =
        runTest {
            val results = createMockResults(3)
            resultsFlow.value = results
            val vm = createVm()
            advanceUntilIdle()

            vm.clickSelectedIndex(1)
            resultsFlow.value = listOf(mockPasteData(100)) + results
            advanceUntilIdle()

            assertEquals(listOf(2), vm.awaitSelectedIndexes())
            assertEquals(listOf(results[1]), vm.currentPasteDataList.first())
        }

    @Test
    fun `multi-selection follows its rows when a new paste arrives on top`() =
        runTest {
            val results = createMockResults(3)
            resultsFlow.value = results
            val vm = createVm()
            advanceUntilIdle()

            vm.clickSelectedIndex(0)
            vm.clickSelectedIndex(2, isShiftPressed = true)
            resultsFlow.value = listOf(mockPasteData(100)) + results
            advanceUntilIdle()

            assertEquals(listOf(1, 3), vm.awaitSelectedIndexes())
        }

    @Test
    fun `selection falls back to the first row when the selected row is removed`() =
        runTest {
            val results = createMockResults(3)
            resultsFlow.value = results
            val vm = createVm()
            advanceUntilIdle()

            vm.clickSelectedIndex(1)
            resultsFlow.value = listOf(results[0], results[2])
            advanceUntilIdle()

            assertEquals(listOf(0), vm.awaitSelectedIndexes())
        }

    @Test
    fun `selectNext steps from the row the selection followed`() =
        runTest {
            val results = createMockResults(3)
            resultsFlow.value = results
            val vm = createVm()
            advanceUntilIdle()

            vm.clickSelectedIndex(1)
            resultsFlow.value = listOf(mockPasteData(100)) + results
            vm.selectNext()
            advanceUntilIdle()

            assertEquals(listOf(results[2]), vm.currentPasteDataList.first())
        }
}
