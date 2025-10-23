package com.crosspaste.ui.model

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

    fun requestPasteListFocus() {
        viewModelScope.launch { _uiEvent.emit(RequestPasteListFocus) }
    }

    fun requestSearchInputFocus() {
        viewModelScope.launch { _uiEvent.emit(RequestSearchInputFocus) }
    }

    fun selectPrev() {
        _selectedIndexes.value = listOf((_selectedIndexes.value.min() - 1).coerceAtLeast(0))
    }

    fun selectNext() {
        _selectedIndexes.value =
            listOf(
                (_selectedIndexes.value.max() + 1)
                    .coerceAtMost(searchViewModel.searchResults.value.size - 1),
            )
    }

    fun setFocusedElement(focusedElement: FocusedElement) {
        _focusedElement.value = focusedElement
    }

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
}
