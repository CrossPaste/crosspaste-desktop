package com.crosspaste.ui.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteTag
import com.crosspaste.utils.getDateUtils
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
                favorite = false,
                pasteType = null,
                sort = true,
                tag = null,
                limit = QUERY_BATCH_SIZE,
            ),
        )

    val searchBaseParams: StateFlow<SearchBaseParams> = _searchBaseParams.asStateFlow()

    private val _loadAll = MutableStateFlow(false)

    abstract val convertTerm: (String) -> List<String>

    @Volatile
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
                pasteType = searchBaseParams.pasteType,
                sort = searchBaseParams.sort,
                tag = searchBaseParams.tag,
                limit = searchBaseParams.limit,
            )
        }

    abstract val tagList: StateFlow<List<PasteTag>>

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

    fun updateTag(tag: Long?) {
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
