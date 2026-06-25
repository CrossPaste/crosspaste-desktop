package com.crosspaste.ui.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteboardService
import com.crosspaste.utils.ioDispatcher
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PasteSelectionViewModel(
    private val appWindowManager: DesktopAppWindowManager,
    private val pasteboardService: PasteboardService,
    private val searchViewModel: PasteSearchViewModel,
) : ViewModel() {

    private val _focusedElement: MutableStateFlow<FocusedElement> =
        MutableStateFlow(FocusedElement.PASTE_LIST)

    val focusedElement: StateFlow<FocusedElement> = _focusedElement

    private val _selectedIndexes = MutableStateFlow(listOf(0))

    val selectedIndexes: StateFlow<List<Int>> =
        combine(
            searchViewModel.searchResults,
            _selectedIndexes,
        ) { results, indexes ->
            if (indexes.isEmpty()) {
                listOf(0)
            } else {
                indexes.filter { it >= 0 && it < results.size }.ifEmpty { listOf(0) }
            }
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

    private val _uiEvent = MutableSharedFlow<UIEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    // One-shot "scroll the list back to the newest item" signal. The LazyListState lives in the UI
    // layer (hoisted in CrossPasteWindows), so the ViewModel never touches Compose objects: it only
    // declares the intent and the UI performs the scroll. extraBufferCapacity = 1 + DROP_OLDEST lets
    // emit() never suspend even when momentarily no collector is active.
    private val _scrollToTopEvents =
        MutableSharedFlow<Unit>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val scrollToTopEvents: SharedFlow<Unit> = _scrollToTopEvents.asSharedFlow()

    init {
        // Declaratively reset selection to the first match whenever the query identity changes
        // (term, sort, type, or tag). drop(1) skips the initial value so opening the window does
        // not count as a change. This replaces the imperative initSelectIndex()/scrollToItem(0)
        // calls that the search content view used to fire from query-keyed effects.
        searchViewModel.searchQuery
            .drop(1)
            .onEach {
                _selectedIndexes.value = listOf(0)
                _scrollToTopEvents.tryEmit(Unit)
            }.launchIn(viewModelScope)
    }

    fun requestPasteListFocus() {
        viewModelScope.launch { _uiEvent.emit(RequestPasteListFocus) }
    }

    fun requestSearchInputFocus() {
        viewModelScope.launch { _uiEvent.emit(RequestSearchInputFocus) }
    }

    fun selectPrev() {
        val indexes = _selectedIndexes.value
        if (indexes.isEmpty()) return
        _selectedIndexes.value = listOf((indexes.min() - 1).coerceAtLeast(0))
    }

    fun selectNext() {
        val resultSize = searchViewModel.searchResults.value.size
        if (resultSize == 0) return
        val indexes = _selectedIndexes.value
        if (indexes.isEmpty()) return
        _selectedIndexes.value = listOf((indexes.max() + 1).coerceAtMost(resultSize - 1))
    }

    fun setFocusedElement(focusedElement: FocusedElement) {
        _focusedElement.value = focusedElement
    }

    /**
     * Reset selection to the newest item (index 0). Called when the search window opens; the UI
     * layer is responsible for the matching scroll-to-top, driven off [scrollToTopEvents] or the
     * window's own open transition.
     */
    fun initSelectIndex() {
        _selectedIndexes.value = listOf(0)
    }

    fun clickSelectedIndex(
        selectedIndex: Int,
        isShiftPressed: Boolean = false,
    ) {
        _selectedIndexes.value =
            if (isShiftPressed) {
                val list = _selectedIndexes.value
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
        _selectedIndexes.value = listOf(0)
    }

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
