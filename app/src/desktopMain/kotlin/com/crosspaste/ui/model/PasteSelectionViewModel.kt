package com.crosspaste.ui.model

import androidx.compose.foundation.lazy.LazyListState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteType
import com.crosspaste.paste.PasteboardService
import com.crosspaste.utils.ioDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PasteSelectionViewModel(
    private val appWindowManager: DesktopAppWindowManager,
    private val pasteboardService: PasteboardService,
    private val searchViewModel: PasteSearchViewModel,
) : ViewModel() {

    /** Set by SidePasteboardContentView so BubbleWindow can read item positions. */
    var searchListState: LazyListState? = null

    private val _focusedElement: MutableStateFlow<FocusedElement> =
        MutableStateFlow(FocusedElement.PASTE_LIST)

    val focusedElement: StateFlow<FocusedElement> = _focusedElement

    /**
     * Selected rows, tracked by paste id rather than index so that pastes arriving at the top
     * while the window is open (remote sync, CLI, extension) do not shift the selection onto
     * another row. Empty means the first row.
     */
    private val _selectedIds = MutableStateFlow<List<Long>>(listOf())

    val selectedIndexes: StateFlow<List<Int>> =
        combine(
            searchViewModel.searchResults,
            _selectedIds,
        ) { results, ids ->
            indexesOf(results, ids)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = listOf(0),
        )

    val currentPasteDataList: StateFlow<List<PasteData>> =
        combine(
            searchViewModel.searchResults,
            selectedIndexes,
        ) { results, indexes ->
            indexes.mapNotNull { index -> results.getOrNull(index) }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = listOf(),
        )

    /**
     * Keyboard highlight in the tag bar: an index into the tag list, where `tags.size` is the
     * add-tag chip. Only shown while the tag bar holds focus.
     */
    private val _highlightedTagIndex = MutableStateFlow(0)
    val highlightedTagIndex: StateFlow<Int> = _highlightedTagIndex

    /**
     * Kept current by the search input (main thread only) so Right only leaves it once the caret
     * has nowhere to go.
     */
    var isSearchCaretAtEnd: Boolean = true
        private set

    fun updateSearchCaret(atEnd: Boolean) {
        isSearchCaretAtEnd = atEnd
    }

    private val _uiEvent = MutableSharedFlow<UIEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    fun requestFocus(zone: FocusedElement) {
        viewModelScope.launch { _uiEvent.emit(RequestFocus(zone)) }
    }

    fun requestPasteListFocus() = requestFocus(FocusedElement.PASTE_LIST)

    fun requestSearchInputFocus() = requestFocus(FocusedElement.SEARCH_INPUT)

    /**
     * Enters the tag bar on the active filter tag, or the first chip when no tag filters the list.
     * Coming back from its right end ([atAddChip]) lands on the add chip instead.
     */
    fun requestTagBarFocus(atAddChip: Boolean = false) {
        val tags = searchViewModel.tagList.value
        val selectedTag = searchViewModel.searchBaseParams.value.tag
        _highlightedTagIndex.value =
            if (atAddChip) {
                tags.size
            } else {
                tags.indexOfFirst { it.id == selectedTag }.coerceAtLeast(0)
            }
        requestFocus(FocusedElement.TAG_BAR)
    }

    /** Left of the first chip is the type filter, right of the add chip is the settings button. */
    fun highlightPrevTag() {
        val index = clampedTagIndex()
        if (index == 0) {
            requestFocus(FocusedElement.TYPE_FILTER)
        } else {
            _highlightedTagIndex.value = index - 1
        }
    }

    fun highlightNextTag() {
        val index = clampedTagIndex()
        if (index == searchViewModel.tagList.value.size) {
            requestFocus(FocusedElement.SETTINGS)
        } else {
            _highlightedTagIndex.value = index + 1
        }
    }

    /** Toggles the highlighted tag's filter, or starts a new tag when the add chip is highlighted. */
    fun activateHighlightedTag() {
        val tag = searchViewModel.tagList.value.getOrNull(clampedTagIndex())
        if (tag != null) {
            searchViewModel.updateTag(tag.id)
        } else {
            viewModelScope.launch { _uiEvent.emit(RequestCreateTag) }
        }
    }

    fun openSettings() {
        viewModelScope.launch { _uiEvent.emit(RequestOpenSettings) }
    }

    /** Rows of the type filter menu: all types first, then each single type. */
    val pasteTypeChoices: List<PasteType?> = listOf<PasteType?>(null) + PasteType.TYPES

    /** Keyboard highlight in the open type filter menu, an index into [pasteTypeChoices]; null while it is closed. */
    private val _typeMenuIndex = MutableStateFlow<Int?>(null)
    val typeMenuIndex: StateFlow<Int?> = _typeMenuIndex

    /** Opens the type menu on the active type, so Enter right away keeps the current filter. */
    fun openTypeMenu() {
        val current =
            searchViewModel.searchBaseParams.value.pasteTypeList
                .singleOrNull()
        _typeMenuIndex.value = pasteTypeChoices.indexOfFirst { it?.type == current }.coerceAtLeast(0)
    }

    fun closeTypeMenu() {
        _typeMenuIndex.value = null
    }

    /**
     * Dismissing the menu without a pick (Esc, a click outside) returns to the search input, like
     * picking does, so typing carries on. Kept apart from [closeTypeMenu], which also runs when the
     * user moves focus away and must not pull it back.
     */
    fun cancelTypeMenu() {
        closeTypeMenu()
        requestSearchInputFocus()
    }

    fun highlightPrevPasteType() = moveTypeMenu(-1)

    fun highlightNextPasteType() = moveTypeMenu(1)

    private fun moveTypeMenu(step: Int) {
        _typeMenuIndex.value = _typeMenuIndex.value?.let { (it + step).coerceIn(0, pasteTypeChoices.lastIndex) }
    }

    fun confirmPasteType() {
        _typeMenuIndex.value?.let { selectPasteType(pasteTypeChoices[it]) }
    }

    /**
     * Applies [pasteType] as the filter (null = all types), closes the menu and hands focus back to
     * the search input, so typing carries on right after picking a type (as on mobile).
     */
    fun selectPasteType(pasteType: PasteType?) {
        searchViewModel.updatePasteType(listOfNotNull(pasteType?.type))
        closeTypeMenu()
        requestSearchInputFocus()
    }

    // Tags can be deleted while highlighted; the add chip then takes the highlight
    private fun clampedTagIndex(): Int = _highlightedTagIndex.value.coerceAtMost(searchViewModel.tagList.value.size)

    fun selectPrev() {
        val results = searchViewModel.searchResults.value
        if (results.isEmpty()) return
        val indexes = indexesOf(results, _selectedIds.value)
        selectIndexes(results, listOf((indexes.min() - 1).coerceAtLeast(0)))
    }

    fun selectNext() {
        val results = searchViewModel.searchResults.value
        if (results.isEmpty()) return
        val indexes = indexesOf(results, _selectedIds.value)
        selectIndexes(results, listOf((indexes.max() + 1).coerceAtMost(results.size - 1)))
    }

    /**
     * Driven by each zone's focus events. Losing focus only clears the zone it still names, so the
     * order in which the old and new zone report a focus move does not matter.
     */
    fun updateFocus(
        element: FocusedElement,
        focused: Boolean,
    ) {
        if (focused) {
            _focusedElement.value = element
        } else {
            _focusedElement.compareAndSet(element, FocusedElement.NONE)
            // The type menu is driven by the type filter's keys, so it cannot outlive its focus
            if (element == FocusedElement.TYPE_FILTER) closeTypeMenu()
        }
    }

    fun initSelectIndex() {
        _selectedIds.value = listOf()
    }

    /**
     * Reset the search list back to the newest item: select index 0 and scroll to the top.
     *
     * Must be driven from a composable that stays in the composition while the search window is
     * hidden (e.g. SearchWindow). The window content's own composition is paused while the window
     * is invisible, so effects inside it never observe the hide/show transition and cannot reliably
     * reset on reopen.
     */
    suspend fun resetToTop() {
        _selectedIds.value = listOf()
        // Don't rely on the type filter losing focus while hidden to close its menu
        closeTypeMenu()
        _highlightedTagIndex.value = 0
        searchListState?.scrollToItem(0)
    }

    fun clickSelectedIndex(
        selectedIndex: Int,
        isShiftPressed: Boolean = false,
    ) {
        val results = searchViewModel.searchResults.value
        val indexes =
            if (isShiftPressed) {
                val list = indexesOf(results, _selectedIds.value)
                when {
                    selectedIndex in list -> {
                        if (list.size > 1) {
                            list.filter { it != selectedIndex }
                        } else {
                            list
                        }
                    }
                    else -> list + selectedIndex
                }
            } else {
                listOf(selectedIndex)
            }
        selectIndexes(results, indexes)
        requestPasteListFocus()
    }

    suspend fun toPaste() {
        currentPasteDataList.first().let { pasteDataList ->
            appWindowManager.hideSearchWindowAndPaste(pasteDataList.size) { index ->
                withContext(ioDispatcher) {
                    pasteboardService
                        .tryWritePasteboard(
                            pasteData = pasteDataList[index],
                            localOnly = true,
                            updateCreateTime = true,
                        ).isSuccess
                }
            }
        }
        _selectedIds.value = listOf()
    }

    private fun selectIndexes(
        results: List<PasteData>,
        indexes: List<Int>,
    ) {
        _selectedIds.value = indexes.mapNotNull { results.getOrNull(it)?.id }
    }

    /** Selected ids that are no longer in [results] are dropped; nothing left means the first row. */
    private fun indexesOf(
        results: List<PasteData>,
        ids: List<Long>,
    ): List<Int> =
        ids
            .map { id -> results.indexOfFirst { it.id == id } }
            .filter { it >= 0 }
            .ifEmpty { listOf(0) }

    suspend fun toPaste(pasteData: PasteData) {
        appWindowManager.hideSearchWindowAndPaste(1) {
            withContext(ioDispatcher) {
                pasteboardService
                    .tryWritePasteboard(
                        pasteData = pasteData,
                        localOnly = true,
                        updateCreateTime = true,
                    ).isSuccess
            }
        }
    }
}
