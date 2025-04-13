package com.crosspaste.ui.model

import androidx.lifecycle.viewModelScope
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.db.paste.PasteData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GeneralPasteDataViewModel(private val pasteDao: PasteDao) : PasteDataViewModel() {

    private val _limit = MutableStateFlow(50L)
    val limit: StateFlow<Long> = _limit.asStateFlow()

    private val _isActive = MutableStateFlow(true)

    @OptIn(ExperimentalCoroutinesApi::class)
    override val pasteDataList: StateFlow<List<PasteData>> =
        _isActive.flatMapLatest { active ->
            if (active) {
                limit.flatMapLatest { currentLimit ->
                    pasteDao.getPasteDataFlow(limit = currentLimit)
                        .map { pasteDataList ->
                            pasteDataList.filter { it.isValid() }
                        }
                }
            } else {
                flow { emit(listOf()) }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = listOf(),
        )

    override fun loadMore() {
        viewModelScope.launch {
            val currentLimit = _limit.value
            val currentSize = pasteDataList.value.size
            if (currentLimit <= currentSize) {
                _limit.value = currentLimit + 20
            }
        }
    }

    override fun pause() {
        _isActive.value = false
    }

    override fun resume() {
        _limit.value = 50
        _isActive.value = true
    }

    override fun cleanup() {
        pause()
        _limit.value = 50
    }

    override fun onCleared() {
        super.onCleared()
        cleanup()
    }
}
