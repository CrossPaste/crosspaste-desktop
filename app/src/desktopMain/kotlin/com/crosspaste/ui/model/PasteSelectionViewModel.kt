package com.crosspaste.ui.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.db.paste.PasteData
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

    private val _selectedIndex = MutableStateFlow(0)

    val selectedIndex: StateFlow<Int> =
        combine(
            searchViewModel.searchResults,
            _selectedIndex,
        ) { results, index ->
            if (index >= results.size) {
                0
            } else {
                index
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0,
        )

    val currentPasteData: StateFlow<PasteData?> =
        combine(
            searchViewModel.searchResults,
            selectedIndex,
        ) { results, index ->
            results.getOrNull(index)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null,
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
        _selectedIndex.value = (_selectedIndex.value - 1).coerceAtLeast(0)
    }

    fun selectNext() {
        _selectedIndex.value =
            (_selectedIndex.value + 1)
                .coerceAtMost(searchViewModel.searchResults.value.size - 1)
    }

    fun setFocusedElement(focusedElement: FocusedElement) {
        _focusedElement.value = focusedElement
    }

    fun initSelectIndex() {
        _selectedIndex.value = 0
    }

    fun clickSelectedIndex(selectedIndex: Int) {
        _selectedIndex.value = selectedIndex
        requestPasteListFocus()
    }

    suspend fun toPaste() {
        appWindowManager.hideSearchWindowAndPaste {
            currentPasteData.first()?.let { pasteData ->
                withContext(ioDispatcher) {
                    pasteboardService.tryWritePasteboard(
                        pasteData = pasteData,
                        localOnly = true,
                        updateCreateTime = true,
                    ).isSuccess
                }
            } == true
        }
        _selectedIndex.value = 0
    }
}
