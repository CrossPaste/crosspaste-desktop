package com.clipevery.clip

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.clipevery.app.AppUI
import com.clipevery.dao.clip.ClipData
import com.clipevery.os.macos.api.MacosApi
import com.clipevery.platform.currentPlatform
import io.github.oshai.kotlinlogging.KotlinLogging

class DesktopClipSearchService(private val appUI: AppUI) : ClipSearchService {

    private val logger = KotlinLogging.logger {}

    private var start: Boolean = false

    override val selectedIndex: State<Int> get() = _selectedIndex

    override val searchResult: MutableList<ClipData> = mutableStateListOf()

    private var _selectedIndex = mutableStateOf(0)

    override val currentClipData: State<ClipData?> get() = _currentClipData

    private var _currentClipData = mutableStateOf<ClipData?>(null)

    private var prevAppName: String? = null

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

    override fun getAppUI(): AppUI {
        return appUI
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

    override fun activeWindow() {
        val currentPlatform = currentPlatform()
        if (currentPlatform.isMacos()) {
            prevAppName = MacosApi.INSTANCE.bringToFront("Clipevery Search")
            logger.info { "save prevAppName is ${prevAppName ?: "null"}" }
        } else if (currentPlatform.isWindows()) {
            // todo windows
        } else if (currentPlatform.isLinux()) {
            // todo linux
        }
    }

    override fun unActiveWindow() {
        appUI.showSearchWindow = false
        prevAppName?.let {
            MacosApi.INSTANCE.activeApp(it)
            logger.info { "unActiveWindow return to app $it" }
        }
    }
}
