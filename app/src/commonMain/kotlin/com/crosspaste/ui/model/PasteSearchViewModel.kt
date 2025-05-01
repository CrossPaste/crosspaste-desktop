package com.crosspaste.ui.model

import androidx.lifecycle.ViewModel
import com.crosspaste.db.paste.PasteData
import com.crosspaste.paste.SearchContentService
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce

abstract class PasteSearchViewModel(
    private val searchContentService: SearchContentService,
) : ViewModel() {

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

    @OptIn(FlowPreview::class)
    val searchParams =
        combine(
            _inputSearch.debounce(500),
            _searchFavorite,
            _searchSort,
            _searchPasteType,
            _searchLimit,
        ) { inputSearch, favorite, sort, pasteType, limit ->
            val searchTerms =
                searchContentService.createSearchTerms(
                    inputSearch.trim().lowercase(),
                ).filterNot { it.isEmpty() }
                    .distinct()

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

    fun setPasteType(pasteType: Int?) {
        _searchPasteType.value = pasteType
    }

    fun tryAddLimit(): Boolean {
        val currentResults = searchResults.value
        return if (_searchLimit.value == currentResults.size) {
            _searchLimit.value += QUERY_BATCH_SIZE
            true
        } else {
            false
        }
    }

    fun resetSearch() {
        _inputSearch.value = ""
        _searchFavorite.value = false
        _searchSort.value = true
        _searchLimit.value = QUERY_BATCH_SIZE
    }
}
