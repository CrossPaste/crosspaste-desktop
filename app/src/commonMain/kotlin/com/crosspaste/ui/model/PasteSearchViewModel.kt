package com.crosspaste.ui.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.db.paste.PasteData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

class PasteSearchViewModel(private val pasteDao: PasteDao) : ViewModel() {

    private val _inputSearch = MutableStateFlow("")
    val inputSearch = _inputSearch.asStateFlow()

    private val _searchFavorite = MutableStateFlow(false)
    val searchFavorite = _searchFavorite.asStateFlow()

    private val _searchSort = MutableStateFlow(true)
    val searchSort = _searchSort.asStateFlow()

    private val _searchPasteType = MutableStateFlow<Int?>(null)
    val searchPasteType = _searchPasteType.asStateFlow()

    private val _searchLimit = MutableStateFlow(50)

    @OptIn(FlowPreview::class)
    private val searchParams =
        combine(
            _inputSearch.debounce(500),
            _searchFavorite,
            _searchSort,
            _searchPasteType,
            _searchLimit,
        ) { inputSearch, favorite, sort, pasteType, limit ->
            val searchTerms =
                inputSearch
                    .trim()
                    .lowercase()
                    .split("\\s+".toRegex())
                    .filterNot { it.isEmpty() }
                    .distinct()

            SearchParams(
                searchTerms = searchTerms,
                favorite = favorite,
                sort = sort,
                pasteType = pasteType,
                limit = limit,
            )
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    val searchResults: StateFlow<List<PasteData>> =
        searchParams
            .distinctUntilChanged()
            .flatMapLatest { params ->
                pasteDao.searchPasteDataFlow(
                    searchTerms = params.searchTerms,
                    favorite = if (params.favorite) true else null,
                    sort = params.sort,
                    pasteType = params.pasteType,
                    limit = params.limit,
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = listOf(),
            )

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
            _searchLimit.value += 50
            true
        } else {
            false
        }
    }

    fun resetSearch() {
        _inputSearch.value = ""
        _searchFavorite.value = false
        _searchSort.value = true
        _searchLimit.value = 50
    }
}
