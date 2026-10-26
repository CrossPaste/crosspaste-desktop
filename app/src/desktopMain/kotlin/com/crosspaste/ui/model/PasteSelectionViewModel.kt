package com.crosspaste.ui.model

import androidx.compose.foundation.lazy.LazyListState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.paste.PasteData
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

    /** Kept current by the search input so Right only leaves it once the caret has nowhere to go. */
    var isSearchCaretAtEnd: Boolean = true

    private val _uiEvent = MutableSharedFlow<UIEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    fun requestPasteListFocus() {
        viewModelScope.launch { _uiEvent.emit(RequestPasteListFocus) }
    }

    fun requestSearchInputFocus() {
        viewModelScope.launch { _uiEvent.emit(RequestSearchInputFocus) }
    }

    /** Enters the tag bar on the active filter tag, or the first chip when no tag filters the list. */
    fun requestTagBarFocus() {
        val selectedTag = searchViewModel.searchBaseParams.value.tag
        _highlightedTagIndex.value =
            searchViewModel.tagList.value
                .indexOfFirst { it.id == selectedTag }
                .coerceAtLeast(0)
        viewModelScope.launch { _uiEvent.emit(RequestTagBarFocus) }
    }

    fun highlightPrevTag() {
        val index = clampedTagIndex()
        if (index == 0) {
            requestSearchInputFocus()
        } else {
            _highlightedTagIndex.value = index - 1
        }
    }

    fun highlightNextTag() {
        _highlightedTagIndex.value = (clampedTagIndex() + 1).coerceAtMost(searchViewModel.tagList.value.size)
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
