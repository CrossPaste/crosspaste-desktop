package com.crosspaste.ui.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crosspaste.realm.paste.PasteData
import com.crosspaste.realm.paste.PasteRealm
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

class PasteDataViewModel(private val pasteRealm: PasteRealm) : ViewModel() {

    private val _limit = MutableStateFlow(50)
    val limit: StateFlow<Int> = _limit.asStateFlow()

    private val _isActive = MutableStateFlow(true)

    @OptIn(ExperimentalCoroutinesApi::class)
    val pasteDatas: StateFlow<List<PasteData>> =
        _isActive.flatMapLatest { active ->
            if (active) {
                limit.flatMapLatest { currentLimit ->
                    pasteRealm.getPasteDataFlow(limit = currentLimit).map { it.list.toList() }
                }
            } else {
                flow { emit(listOf()) }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = listOf(),
        )

    fun loadMore() {
        viewModelScope.launch {
            val currentLimit = _limit.value
            val currentSize = pasteDatas.value.size
            if (currentLimit <= currentSize) {
                _limit.value = currentLimit + 20
            }
        }
    }

    fun pause() {
        _isActive.value = false
    }

    fun resume() {
        _limit.value = 50
        _isActive.value = true
    }

    fun cleanup() {
        pause()
        _limit.value = 50
    }

    override fun onCleared() {
        super.onCleared()
        cleanup()
    }
}
