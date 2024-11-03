package com.crosspaste.ui.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.paste.PasteboardService
import com.crosspaste.realm.paste.PasteData
import com.crosspaste.utils.ioDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

class PasteSelectionViewModel(
    private val appWindowManager: DesktopAppWindowManager,
    private val pasteboardService: PasteboardService,
    private val searchViewModel: PasteSearchViewModel,
) : ViewModel() {

    private val _selectedIndex = MutableStateFlow(0)

    val selectedIndex: StateFlow<Int> =
        combine(
            searchViewModel.searchResults,
            _selectedIndex,
        ) { results, index ->
            if (index >= results.size) 0 else index
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

    fun upSelectedIndex() {
        _selectedIndex.value = (_selectedIndex.value - 1).coerceAtLeast(0)
    }

    fun downSelectedIndex() {
        _selectedIndex.value =
            (_selectedIndex.value + 1)
                .coerceAtMost(searchViewModel.searchResults.value.size - 1)
    }

    fun setSelectedIndex(selectedIndex: Int) {
        _selectedIndex.value = selectedIndex
    }

    suspend fun toPaste() {
        appWindowManager.unActiveSearchWindow {
            currentPasteData.value?.let { pasteData ->
                withContext(ioDispatcher) {
                    pasteboardService.tryWritePasteboard(
                        pasteData = pasteData,
                        localOnly = true,
                        updateCreateTime = true,
                    )
                }
                true
            } == true
        }
        _selectedIndex.value = 0
    }
}
