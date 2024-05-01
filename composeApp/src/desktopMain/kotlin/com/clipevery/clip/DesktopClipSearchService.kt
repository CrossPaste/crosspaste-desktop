package com.clipevery.clip

import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.clipevery.app.AppWindowManager
import com.clipevery.dao.clip.ClipDao
import com.clipevery.dao.clip.ClipData
import com.clipevery.utils.ioDispatcher
import io.realm.kotlin.notifications.InitialResults
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.UpdatedResults
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.mongodb.kbson.ObjectId

class DesktopClipSearchService(
    override val appWindowManager: AppWindowManager,
    private val clipboardService: ClipboardService,
    private val clipDao: ClipDao,
) : ClipSearchService {

    private var searchJob: Job? = null

    private val ioScope = CoroutineScope(ioDispatcher)

    private val mutex: Mutex = Mutex()

    override var selectedIndex by mutableStateOf(0)

    override val inputSearch: State<String> get() = _inputSearch

    override var searchFavorite by mutableStateOf(false)

    override var searchSort by mutableStateOf(false)

    override var searchClipType by mutableStateOf<Int?>(null)

    private var _inputSearch = mutableStateOf("")

    override val searchResult: MutableList<ClipData> = mutableStateListOf()

    override val currentClipData: State<ClipData?> get() = _currentClipData

    private var _currentClipData = mutableStateOf<ClipData?>(null)

    override fun updateInputSearch(inputSearch: String) {
        _inputSearch.value = inputSearch
    }

    override fun switchFavorite() {
        ioScope.launch {
            mutex.withLock {
                searchFavorite = !searchFavorite
                search()
            }
        }
    }

    override fun switchSort() {
        ioScope.launch {
            mutex.withLock {
                searchSort = !searchSort
                search()
            }
        }
    }

    override fun setClipType(clipType: Int?) {
        ioScope.launch {
            mutex.withLock {
                searchClipType = clipType
                search()
            }
        }
    }

    override suspend fun search() {
        searchJob?.cancel()

        searchJob =
            ioScope.launch {
                val searchClipData =
                    clipDao.searchClipData(
                        inputSearch = _inputSearch.value,
                        favorite = if (searchFavorite) searchFavorite else null,
                        sort = searchSort,
                        clipType = searchClipType,
                        limit = 50,
                    )
                val searchClipDataFlow: Flow<ResultsChange<ClipData>> = searchClipData.asFlow()

                searchClipDataFlow.collect { changes ->
                    when (changes) {
                        is InitialResults -> {
                            initSearchResult(changes.list)
                        }
                        is UpdatedResults -> {
                            updateSearchResult(changes.list)
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
        ioScope.launch {
            mutex.withLock {
                innerSetSelectedIndex(selectedIndex)
            }
        }
    }

    private fun innerSetSelectedIndex(selectedIndex: Int) {
        this.selectedIndex = selectedIndex
        setCurrentClipData()
    }

    private suspend fun initSearchResult(searchResult: List<ClipData>) {
        mutex.withLock {
            this.searchResult.clear()
            this.searchResult.addAll(searchResult)
            this.selectedIndex = 0
            setCurrentClipData()
        }
    }

    private suspend fun updateSearchResult(searchResult: List<ClipData>) {
        mutex.withLock {
            val prevSelectedId: ObjectId? = _currentClipData.value?.id
            this.searchResult.clear()
            this.searchResult.addAll(searchResult)
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
            setCurrentClipData()
        }
    }

    override fun upSelectedIndex() {
        ioScope.launch {
            mutex.withLock {
                if (selectedIndex > 0) {
                    selectedIndex--
                    setCurrentClipData()
                }
            }
        }
    }

    override fun downSelectedIndex() {
        ioScope.launch {
            mutex.withLock {
                if (selectedIndex < searchResult.size - 1) {
                    selectedIndex++
                    setCurrentClipData()
                }
            }
        }
    }

    override suspend fun activeWindow() {
        appWindowManager.activeSearchWindow()
        _inputSearch.value = ""
        searchFavorite = false
        searchSort = true
        search()
    }

    override suspend fun unActiveWindow() {
        appWindowManager.unActiveSearchWindow { false }
        mutex.withLock {
            searchResult.clear()
            innerSetSelectedIndex(0)
        }
    }

    override suspend fun toPaste() {
        appWindowManager.unActiveSearchWindow {
            _currentClipData.value?.let { clipData ->
                clipboardService.tryWriteClipboard(clipData, localOnly = true)
                true
            } ?: false
        }
        mutex.withLock {
            searchResult.clear()
            innerSetSelectedIndex(0)
        }
    }
}
