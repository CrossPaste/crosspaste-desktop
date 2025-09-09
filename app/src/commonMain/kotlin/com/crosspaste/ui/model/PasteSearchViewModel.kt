package com.crosspaste.ui.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crosspaste.paste.PasteData
import com.crosspaste.utils.getDateUtils
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

abstract class PasteSearchViewModel : ViewModel() {

    companion object {
        const val QUERY_BATCH_SIZE = 50
        const val LOAD_MORE_THROTTLE_MS = 500L
    }

    private val _inputSearch = MutableStateFlow("")

    val inputSearch = _inputSearch

    private val _searchBaseParams =
        MutableStateFlow(
            SearchBaseParams(
                favorite = false,
                sort = true,
                pasteType = null,
                limit = QUERY_BATCH_SIZE,
            ),
        )

    val searchBaseParams = _searchBaseParams

    private val _loadAll = MutableStateFlow(false)

    abstract val convertTerm: (String) -> List<String>

    private var lastLoadTime = 0L
    private val loadMoreMutex = Mutex()

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
                favorite = searchBaseParams.favorite,
                sort = searchBaseParams.sort,
                pasteType = searchBaseParams.pasteType,
                limit = searchBaseParams.limit,
            )
        }

    abstract val searchResults: StateFlow<List<PasteData>>

    fun updateInputSearch(input: String) {
        _inputSearch.value = input
        _searchBaseParams.value =
            _searchBaseParams.value.copy(
                limit = QUERY_BATCH_SIZE,
            )
        _loadAll.value = false
    }

    fun switchFavorite() {
        _searchBaseParams.value =
            _searchBaseParams.value.copy(
                favorite = !_searchBaseParams.value.favorite,
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

    fun updatePasteType(pasteType: Int?) {
        _searchBaseParams.value =
            _searchBaseParams.value.copy(
                pasteType = pasteType,
                limit = QUERY_BATCH_SIZE,
            )
        _loadAll.value = false
    }

    fun tryAddLimit(): Boolean {
        if (_loadAll.value) {
            return false
        }

        val currentTime = dateUtils.nowEpochMilliseconds()

        if (currentTime - lastLoadTime < LOAD_MORE_THROTTLE_MS) {
            return false
        }

        viewModelScope.launch {
            loadMoreMutex.withLock {
                if (!_loadAll.value &&
                    dateUtils.nowEpochMilliseconds() - lastLoadTime >= LOAD_MORE_THROTTLE_MS
                ) {
                    lastLoadTime = dateUtils.nowEpochMilliseconds()
                    _searchBaseParams.value =
                        _searchBaseParams.value.copy(
                            limit = _searchBaseParams.value.limit + QUERY_BATCH_SIZE,
                        )
                }
            }
        }

        return true
    }

    fun checkLoadAll(size: Int) {
        if (size < _searchBaseParams.value.limit) {
            _loadAll.value = true
        }
    }

    fun resetSearch() {
        _inputSearch.value = ""
        _searchBaseParams.value =
            _searchBaseParams.value.copy(
                favorite = false,
                pasteType = null,
                sort = true,
                limit = QUERY_BATCH_SIZE,
            )
        _loadAll.value = false
    }
}
