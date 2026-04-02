package com.crosspaste.ui.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteTag
import com.crosspaste.utils.getDateUtils
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

abstract class PasteSearchViewModel : ViewModel() {

    companion object {
        const val QUERY_BATCH_SIZE = 50
        const val LOAD_MORE_THROTTLE_MS = 500L
    }

    private val _inputSearch = MutableStateFlow("")

    val inputSearch: StateFlow<String> = _inputSearch.asStateFlow()

    private val _searchBaseParams =
        MutableStateFlow(
            SearchBaseParams(
                pasteTypeList = listOf(),
                sort = true,
                tag = null,
                limit = QUERY_BATCH_SIZE,
            ),
        )

    val searchBaseParams: StateFlow<SearchBaseParams> = _searchBaseParams.asStateFlow()

    private val _loadAll = MutableStateFlow(false)

    val loadAll: StateFlow<Boolean> = _loadAll.asStateFlow()

    abstract val convertTerm: (String) -> List<String>

    @Volatile
    private var lastLoadTime = 0L
    private var pendingLoadJob: Job? = null

    private val dateUtils = getDateUtils()

    @OptIn(FlowPreview::class)
    val searchParams =
        combine(
            _inputSearch.debounce(500),
            _searchBaseParams,
        ) { inputSearch, searchBaseParams ->
            val searchTerms = convertTerm(inputSearch)

            SearchParams(
                searchTerms = searchTerms,
                pasteTypeList = searchBaseParams.pasteTypeList,
                sort = searchBaseParams.sort,
                tag = searchBaseParams.tag,
                limit = searchBaseParams.limit,
            )
        }

    abstract val tagList: StateFlow<List<PasteTag>>

    abstract val searchResults: StateFlow<List<PasteData>>

    fun updateInputSearch(input: String) {
        _inputSearch.value = input
        pendingLoadJob?.cancel()
        _searchBaseParams.value =
            _searchBaseParams.value.copy(
                limit = QUERY_BATCH_SIZE,
            )
        _loadAll.value = false
    }

    fun switchSort() {
        _searchBaseParams.value =
            _searchBaseParams.value.copy(
                sort = !_searchBaseParams.value.sort,
            )
    }

    fun updatePasteType(pasteTypeList: List<Int>) {
        pendingLoadJob?.cancel()
        _searchBaseParams.value =
            _searchBaseParams.value.copy(
                pasteTypeList = pasteTypeList,
                limit = QUERY_BATCH_SIZE,
            )
        _loadAll.value = false
    }

    fun updateTag(tag: Long?) {
        pendingLoadJob?.cancel()
        _searchBaseParams.value =
            if (_searchBaseParams.value.tag != tag) {
                _searchBaseParams.value.copy(
                    tag = tag,
                    limit = QUERY_BATCH_SIZE,
                )
            } else {
                _searchBaseParams.value.copy(
                    tag = null,
                    limit = QUERY_BATCH_SIZE,
                )
            }
        _loadAll.value = false
    }

    fun tryAddLimit() {
        if (_loadAll.value) return
        if (pendingLoadJob?.isActive == true) return

        pendingLoadJob =
            viewModelScope.launch {
                val elapsed = dateUtils.nowEpochMilliseconds() - lastLoadTime
                if (elapsed < LOAD_MORE_THROTTLE_MS) {
                    delay(LOAD_MORE_THROTTLE_MS - elapsed)
                }

                if (!_loadAll.value) {
                    lastLoadTime = dateUtils.nowEpochMilliseconds()
                    _searchBaseParams.value =
                        _searchBaseParams.value.copy(
                            limit = _searchBaseParams.value.limit + QUERY_BATCH_SIZE,
                        )
                }
            }
    }

    fun checkLoadAll(size: Int) {
        if (size < _searchBaseParams.value.limit) {
            _loadAll.value = true
        }
    }

    fun resetSearch() {
        _inputSearch.value = ""
        pendingLoadJob?.cancel()
        _searchBaseParams.value =
            _searchBaseParams.value.copy(
                pasteTypeList = listOf(),
                sort = true,
                tag = null,
                limit = QUERY_BATCH_SIZE,
            )
        _loadAll.value = false
    }
}
