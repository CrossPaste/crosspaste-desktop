package com.crosspaste.clip

import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.crosspaste.app.AppWindowManager
import com.crosspaste.dao.clip.ClipDao
import com.crosspaste.dao.clip.ClipData
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
import org.mongodb.kbson.ObjectId

class DesktopClipSearchService(
    override val appWindowManager: AppWindowManager,
    private val clipboardService: ClipboardService,
    private val clipDao: ClipDao,
) : ClipSearchService {

    private var searchJob: Job? = null

    private val ioScope = CoroutineScope(ioDispatcher)

    private var searchParams by mutableStateOf(
        SearchParams(
            emptyList(),
            favorite = false,
            sort = false,
            clipType = null,
            limit = 50,
        ),
    )

    override var selectedIndex by mutableStateOf(0)

    override var inputSearch by mutableStateOf("")

    override var searchFavorite by mutableStateOf(false)

    override var searchSort by mutableStateOf(false)

    override var searchClipType by mutableStateOf<Int?>(null)

    override var searchLimit by mutableStateOf(50)

    override var searchTime: Int = 0

    override val searchResult: MutableList<ClipData> = mutableStateListOf()

    override val currentClipData: State<ClipData?> get() = _currentClipData

    private var _currentClipData = mutableStateOf<ClipData?>(null)

    override fun updateInputSearch(inputSearch: String) {
        this.inputSearch = inputSearch
    }

    override fun switchFavorite() {
        searchFavorite = !searchFavorite
    }

    override fun switchSort() {
        searchSort = !searchSort
    }

    override fun setClipType(clipType: Int?) {
        searchClipType = clipType
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
                clipType = searchClipType,
                limit = searchLimit,
            )

        if (searchParams == currentSearchParams) {
            return
        }

        searchParams = currentSearchParams

        searchJob?.cancel()

        searchJob =
            ioScope.launch {
                val searchClipData =
                    clipDao.searchClipData(
                        searchTerms = searchTerms,
                        favorite = if (searchFavorite) searchFavorite else null,
                        sort = searchSort,
                        clipType = searchClipType,
                        limit = searchLimit,
                    )
                val searchClipDataFlow: Flow<ResultsChange<ClipData>> = searchClipData.asFlow()

                searchClipDataFlow.collect { changes ->
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

    private fun setCurrentClipData() {
        if (selectedIndex >= 0 && selectedIndex < searchResult.size) {
            _currentClipData.value = searchResult[selectedIndex]
        } else {
            _currentClipData.value = null
        }
    }

    override fun clickSetSelectedIndex(selectedIndex: Int) {
        innerSetSelectedIndex(selectedIndex)
    }

    private fun innerSetSelectedIndex(selectedIndex: Int) {
        this.selectedIndex = selectedIndex
        setCurrentClipData()
    }

    private fun initSearchResult(
        searchResult: List<ClipData>,
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
        setCurrentClipData()
        searchTime++
    }

    private fun updateSearchResult(searchResult: List<ClipData>) {
        val prevSelectedId: ObjectId? = _currentClipData.value?.id
        this.searchResult.clear()
        this.searchResult.addAll(searchResult)
        if (appWindowManager.showSearchWindow) {
            prevSelectedId?.let { id ->
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
        setCurrentClipData()
        searchTime++
    }

    override fun upSelectedIndex() {
        if (selectedIndex > 0) {
            selectedIndex--
            setCurrentClipData()
        }
    }

    override fun downSelectedIndex() {
        if (selectedIndex < searchResult.size - 1) {
            selectedIndex++
            setCurrentClipData()
        }
    }

    override suspend fun activeWindow() {
        appWindowManager.activeSearchWindow()
        inputSearch = ""
        searchFavorite = false
        searchSort = true
        searchLimit = 50
    }

    override suspend fun unActiveWindow() {
        appWindowManager.unActiveSearchWindow { false }
        innerSetSelectedIndex(0)
    }

    override suspend fun toPaste() {
        appWindowManager.unActiveSearchWindow {
            _currentClipData.value?.let { clipData ->
                clipboardService.tryWriteClipboard(clipData, localOnly = true)
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
    val clipType: Int?,
    val limit: Int,
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SearchParams) return false

        if (searchTerms != other.searchTerms) return false
        if (favorite != other.favorite) return false
        if (sort != other.sort) return false
        if (clipType != other.clipType) return false
        if (limit != other.limit) return false

        return true
    }

    override fun hashCode(): Int {
        var result = searchTerms.hashCode()
        result = 31 * result + favorite.hashCode()
        result = 31 * result + sort.hashCode()
        result = 31 * result + (clipType ?: 0)
        result = 31 * result + limit
        return result
    }

    override fun toString(): String {
        return "SearchParams(searchTerms=$searchTerms, favorite=$favorite, sort=$sort, clipType=$clipType, limit=$limit)"
    }
}
