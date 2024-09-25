package com.crosspaste.ui.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crosspaste.realm.paste.PasteData
import com.crosspaste.realm.paste.PasteRealm
import com.crosspaste.utils.mainDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PasteDataViewModel(private val pasteRealm: PasteRealm) : ViewModel() {

    private val _limit = MutableStateFlow(50)
    val limit: StateFlow<Int> = _limit.asStateFlow()

    private val _pasteDatas = MutableStateFlow<List<PasteData>>(emptyList())
    val pasteDatas: StateFlow<List<PasteData>> = _pasteDatas.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val pasteDataFlow =
        limit.flatMapLatest { currentLimit ->
            pasteRealm.getPasteDataFlow(limit = currentLimit).map { it.list }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    init {
        viewModelScope.launch {
            pasteDataFlow.collect { change ->
                withContext(mainDispatcher) {
                    _pasteDatas.value = change
                }
            }
        }
    }

    fun loadMore() {
        if (_limit.value > pasteDatas.value.size) {
            return
        } else {
            _limit.value += 20
        }
    }

    fun initList() {
        _limit.value = 50
    }
}
