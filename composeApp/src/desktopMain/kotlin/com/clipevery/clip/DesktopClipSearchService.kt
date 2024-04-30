package com.clipevery.clip

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
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

    private var start: Boolean = false

    private var searchJob: Job? = null

    private val ioScope = CoroutineScope(ioDispatcher)

    private val mutex: Mutex = Mutex()

    override val selectedIndex: State<Int> get() = _selectedIndex

    override val inputSearch: State<String> get() = _inputSearch

    private var _inputSearch = mutableStateOf("")

    override val searchResult: MutableList<ClipData> = mutableStateListOf()

    private var _selectedIndex = mutableStateOf(0)

    override val currentClipData: State<ClipData?> get() = _currentClipData

    private var _currentClipData = mutableStateOf<ClipData?>(null)

    override fun updateInputSearch(inputSearch: String) {
        _inputSearch.value = inputSearch
    }

    override suspend fun search() {
        searchJob?.cancel()

        searchJob =
            ioScope.launch {
                val searchClipData =
                    clipDao.searchClipData(
                        inputSearch = _inputSearch.value,
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

    @Synchronized
    override fun tryStart(): Boolean {
        if (!start) {
            start = true
            return true
        } else {
            return false
        }
    }

    override fun stop() {
        if (start) {
            start = false
            // todo stop
        }
    }

    private fun setCurrentClipData() {
        if (_selectedIndex.value >= 0 && _selectedIndex.value < searchResult.size) {
            _currentClipData.value = searchResult[_selectedIndex.value]
        } else {
            _currentClipData.value = null
        }
    }

    override fun setSelectedIndex(selectedIndex: Int) {
        ioScope.launch {
            mutex.withLock {
                innerSetSelectedIndex(selectedIndex)
            }
        }
    }

    private fun innerSetSelectedIndex(selectedIndex: Int) {
        _selectedIndex.value = selectedIndex
        setCurrentClipData()
    }

    private suspend fun initSearchResult(searchResult: List<ClipData>) {
        mutex.withLock {
            this.searchResult.clear()
            this.searchResult.addAll(searchResult)
            _selectedIndex.value = 0
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
                    _selectedIndex.value = newSelectedIndex
                } else {
                    _selectedIndex.value = 0
                }
            } ?: run {
                _selectedIndex.value = 0
            }
            setCurrentClipData()
        }
    }

    override fun upSelectedIndex() {
        ioScope.launch {
            mutex.withLock {
                if (_selectedIndex.value > 0) {
                    _selectedIndex.value--
                    setCurrentClipData()
                }
            }
        }
    }

    override fun downSelectedIndex() {
        ioScope.launch {
            mutex.withLock {
                if (_selectedIndex.value < searchResult.size - 1) {
                    _selectedIndex.value++
                    setCurrentClipData()
                }
            }
        }
    }

    override suspend fun activeWindow() {
        appWindowManager.activeSearchWindow()
        _inputSearch.value = ""
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
