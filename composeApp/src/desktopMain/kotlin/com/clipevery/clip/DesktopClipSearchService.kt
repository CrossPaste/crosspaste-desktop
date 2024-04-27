package com.clipevery.clip

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.clipevery.app.AppWindowManager
import com.clipevery.dao.clip.ClipData

class DesktopClipSearchService(
    override val appWindowManager: AppWindowManager,
    private val clipboardService: ClipboardService,
) : ClipSearchService {

    private var start: Boolean = false

    override val selectedIndex: State<Int> get() = _selectedIndex

    override val searchResult: MutableList<ClipData> = mutableStateListOf()

    private var _selectedIndex = mutableStateOf(0)

    override val currentClipData: State<ClipData?> get() = _currentClipData

    private var _currentClipData = mutableStateOf<ClipData?>(null)

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
        _selectedIndex.value = selectedIndex
        setCurrentClipData()
    }

    override fun updateSearchResult(searchResult: List<ClipData>) {
        this.searchResult.clear()
        this.searchResult.addAll(searchResult)
        _selectedIndex.value = 0
        setCurrentClipData()
    }

    override fun upSelectedIndex() {
        if (_selectedIndex.value > 0) {
            _selectedIndex.value--
            setCurrentClipData()
        }
    }

    override fun downSelectedIndex() {
        if (_selectedIndex.value < searchResult.size - 1) {
            _selectedIndex.value++
            setCurrentClipData()
        }
    }

    override suspend fun activeWindow() {
        appWindowManager.activeSearchWindow()
    }

    override suspend fun unActiveWindow() {
        appWindowManager.unActiveSearchWindow { false }
    }

    override suspend fun toPaste() {
        appWindowManager.unActiveSearchWindow {
            _currentClipData.value?.let { clipData ->
                clipboardService.tryWriteClipboard(clipData, localOnly = true)
                true
            } ?: false
        }
    }
}
