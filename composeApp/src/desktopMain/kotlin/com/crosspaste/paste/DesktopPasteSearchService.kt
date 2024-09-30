package com.crosspaste.paste

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.realm.paste.PasteData
import com.crosspaste.realm.paste.PasteRealm
import com.crosspaste.utils.ioDispatcher
import com.crosspaste.utils.mainDispatcher
import io.realm.kotlin.notifications.InitialResults
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.UpdatedResults
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DesktopPasteSearchService(
    private val appWindowManager: DesktopAppWindowManager,
    private val pasteboardService: PasteboardService,
    private val pasteRealm: PasteRealm,
) : PasteSearchService {

    private var searchJob: Job? = null

    private val ioScope = CoroutineScope(ioDispatcher)

    private var searchParams by mutableStateOf(
        SearchParams(
            emptyList(),
            favorite = false,
            sort = false,
            pasteType = null,
            limit = 50,
        ),
    )

    override var selectedIndex by mutableStateOf(0)

    override var inputSearch by mutableStateOf("")

    override var searchFavorite by mutableStateOf(false)

    override var searchSort by mutableStateOf(false)

    override var searchPasteType by mutableStateOf<Int?>(null)

    override var searchLimit by mutableStateOf(50)

    override var searchTime: Int = 0

    override val searchResult: MutableList<PasteData> = mutableStateListOf()

    override var currentPasteData by mutableStateOf<PasteData?>(null)

    override fun updateInputSearch(inputSearch: String) {
        this.inputSearch = inputSearch
    }

    override fun switchFavorite() {
        searchFavorite = !searchFavorite
    }

    override fun switchSort() {
        searchSort = !searchSort
    }

    override fun setPasteType(pasteType: Int?) {
        searchPasteType = pasteType
    }

    override fun tryAddLimit(): Boolean {
        if (searchLimit == searchResult.size) {
            searchLimit += 50
            return true
        } else {
            return false
        }
    }

    override suspend fun search(keepSelectIndex: Boolean) {
        val searchTerms =
            inputSearch.trim().lowercase().split("\\s+".toRegex()).filterNot { it.isEmpty() }.distinct()

        val currentSearchParams =
            SearchParams(
                searchTerms = searchTerms,
                favorite = searchFavorite,
                sort = searchSort,
                pasteType = searchPasteType,
                limit = searchLimit,
            )

        if (searchParams == currentSearchParams) {
            return
        }

        searchParams = currentSearchParams

        searchJob?.cancel()

        searchJob =
            ioScope.launch {
                val searchPasteData =
                    pasteRealm.searchPasteData(
                        searchTerms = searchTerms,
                        favorite = if (searchFavorite) searchFavorite else null,
                        sort = searchSort,
                        pasteType = searchPasteType,
                        limit = searchLimit,
                    )
                val searchPasteDataFlow: Flow<ResultsChange<PasteData>> = searchPasteData.asFlow()

                searchPasteDataFlow.collect { changes ->
                    withContext(mainDispatcher) {
                        when (changes) {
                            is InitialResults -> {
                                initSearchResult(changes.list, keepSelectIndex)
                            }

                            is UpdatedResults -> {
                                updateSearchResult(changes.list)
                            }
                        }
                    }
                }
            }
    }

    private fun setCurrentPasteData() {
        currentPasteData =
            if (selectedIndex >= 0 && selectedIndex < searchResult.size) {
                searchResult[selectedIndex]
            } else {
                null
            }
    }

    override fun clickSetSelectedIndex(selectedIndex: Int) {
        innerSetSelectedIndex(selectedIndex)
    }

    private fun innerSetSelectedIndex(selectedIndex: Int) {
        this.selectedIndex = selectedIndex
        setCurrentPasteData()
    }

    private fun initSearchResult(
        searchResult: List<PasteData>,
        keepSelectIndex: Boolean,
    ) {
        this.searchResult.clear()
        this.searchResult.addAll(searchResult)
        if (keepSelectIndex) {
            if (selectedIndex > searchResult.size - 1) {
                this.selectedIndex = 0
            }
        } else {
            this.selectedIndex = 0
        }
        setCurrentPasteData()
        searchTime++
    }

    private fun updateSearchResult(searchResult: List<PasteData>) {
        this.searchResult.clear()
        this.searchResult.addAll(searchResult)
        if (appWindowManager.getShowSearchWindow()) {
            currentPasteData?.id?.let { id ->
                val newSelectedIndex = searchResult.indexOfFirst { it.id == id }
                if (newSelectedIndex >= 0) {
                    this.selectedIndex = newSelectedIndex
                } else {
                    this.selectedIndex = 0
                }
            } ?: run {
                this.selectedIndex = 0
            }
        } else {
            this.selectedIndex = 0
        }
        setCurrentPasteData()
        searchTime++
    }

    override fun upSelectedIndex() {
        if (selectedIndex > 0) {
            selectedIndex--
            setCurrentPasteData()
        }
    }

    override fun downSelectedIndex() {
        if (selectedIndex < searchResult.size - 1) {
            selectedIndex++
            setCurrentPasteData()
        }
    }

    suspend fun activeWindow() {
        appWindowManager.activeSearchWindow()
        inputSearch = ""
        searchFavorite = false
        searchSort = true
        searchLimit = 50
    }

    suspend fun unActiveWindow() {
        appWindowManager.unActiveSearchWindow { false }
        innerSetSelectedIndex(0)
    }

    override suspend fun toPaste() {
        appWindowManager.unActiveSearchWindow {
            currentPasteData?.let { pasteData ->
                withContext(ioDispatcher) {
                    pasteboardService.tryWritePasteboard(
                        pasteData = pasteData,
                        localOnly = true,
                        updateCreateTime = true,
                    )
                }
                true
            } ?: false
        }
        innerSetSelectedIndex(0)
    }
}

data class SearchParams(
    val searchTerms: List<String>,
    val favorite: Boolean,
    val sort: Boolean,
    val pasteType: Int?,
    val limit: Int,
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SearchParams) return false

        if (searchTerms != other.searchTerms) return false
        if (favorite != other.favorite) return false
        if (sort != other.sort) return false
        if (pasteType != other.pasteType) return false
        if (limit != other.limit) return false

        return true
    }

    override fun hashCode(): Int {
        var result = searchTerms.hashCode()
        result = 31 * result + favorite.hashCode()
        result = 31 * result + sort.hashCode()
        result = 31 * result + (pasteType ?: 0)
        result = 31 * result + limit
        return result
    }

    override fun toString(): String {
        return "SearchParams(searchTerms=$searchTerms, favorite=$favorite, sort=$sort, pasteType=$pasteType, limit=$limit)"
    }
}
