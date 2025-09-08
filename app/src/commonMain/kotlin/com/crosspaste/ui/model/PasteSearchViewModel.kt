package com.crosspaste.ui.model

import androidx.lifecycle.ViewModel
import com.crosspaste.paste.PasteData
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce

abstract class PasteSearchViewModel : ViewModel() {

    companion object {
        const val QUERY_BATCH_SIZE = 50
    }

    private val _inputSearch = MutableStateFlow("")
    val inputSearch = _inputSearch

    private val _searchFavorite = MutableStateFlow(false)
    val searchFavorite = _searchFavorite

    private val _searchSort = MutableStateFlow(true)
    val searchSort = _searchSort

    private val _searchPasteType = MutableStateFlow<Int?>(null)
    val searchPasteType = _searchPasteType

    private val _searchLimit = MutableStateFlow(QUERY_BATCH_SIZE)

    // Includes invalid paste data
    private val _allSearchSize = MutableStateFlow(0)

    abstract val convertTerm: (String) -> List<String>

    @OptIn(FlowPreview::class)
    val searchParams =
        combine(
            _inputSearch.debounce(500),
            _searchFavorite,
            _searchSort,
            _searchPasteType,
            _searchLimit,
        ) { inputSearch, favorite, sort, pasteType, limit ->
            val searchTerms = convertTerm(inputSearch)

            SearchParams(
                searchTerms = searchTerms,
                favorite = favorite,
                sort = sort,
                pasteType = pasteType,
                limit = limit,
            )
        }

    abstract val searchResults: StateFlow<List<PasteData>>

    fun updateInputSearch(input: String) {
        _inputSearch.value = input
    }

    fun switchFavorite() {
        _searchFavorite.value = !_searchFavorite.value
    }

    fun switchSort() {
        _searchSort.value = !_searchSort.value
    }

    fun updatePasteType(pasteType: Int?) {
        _searchPasteType.value = pasteType
    }

    fun updateAllSearchSize(size: Int) {
        _allSearchSize.value = size
    }

    fun tryAddLimit(): Boolean =
        if (_searchLimit.value == _allSearchSize.value) {
            _searchLimit.value += QUERY_BATCH_SIZE
            true
        } else {
            false
        }

    fun resetSearch() {
        _inputSearch.value = ""
        _searchFavorite.value = false
        _searchSort.value = true
        _searchLimit.value = QUERY_BATCH_SIZE
        _allSearchSize.value = 0
    }
}
