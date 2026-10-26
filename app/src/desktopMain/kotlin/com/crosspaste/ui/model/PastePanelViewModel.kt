package com.crosspaste.ui.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.db.paste.SearchPasteData
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteboardService
import com.crosspaste.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

/**
 * State for the floating paste panel (#4995).
 *
 * The panel lists the clipboard history newest-first. Clicking a row pastes it into
 * whatever app currently has focus (the panel never takes focus itself) and highlights
 * that row. The highlight is tracked by paste id rather than index so that new copies
 * arriving while the panel is open do not shift it. A double click pastes once: presses
 * that land while a paste is still running, or shortly after it, are dropped.
 */
class PastePanelViewModel(
    private val appWindowManager: DesktopAppWindowManager,
    private val pasteboardService: PasteboardService,
    private val searchPasteData: SearchPasteData,
) : ViewModel() {

    companion object {
        const val PAGE_SIZE = 50

        /** How long after a paste starts that further presses are still treated as the same click. */
        val REPEAT_PRESS_GUARD = 500.milliseconds
    }

    private val logger = KotlinLogging.logger {}

    private val _limit = MutableStateFlow(PAGE_SIZE)

    private val _loadAll = MutableStateFlow(false)
    val loadAll: StateFlow<Boolean> = _loadAll.asStateFlow()

    /** Id of the row pasted last; null until a row is clicked. */
    private val _highlightedId = MutableStateFlow<Long?>(null)

    private val pasteInFlight = Mutex()

    @OptIn(ExperimentalCoroutinesApi::class)
    val items: StateFlow<List<PasteData>> =
        _limit
            .flatMapLatest { limit ->
                searchPasteData
                    .searchPasteDataFlow(searchTerms = emptyList(), limit = limit)
                    .map { pasteDataList ->
                        _loadAll.value = pasteDataList.size < limit
                        pasteDataList.filter { it.isValid() }.distinctBy { it.id }
                    }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )

    /** Index of the highlighted row, -1 when none is. */
    val highlightedIndex: StateFlow<Int> =
        combine(items, _highlightedId) { list, highlightedId ->
            highlightedId?.let { id -> list.indexOfFirst { it.id == id } } ?: -1
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = -1,
        )

    /** Every open starts fresh: nothing highlighted, first page only. */
    fun onShown() {
        _highlightedId.value = null
        _limit.value = PAGE_SIZE
    }

    fun loadMore() {
        if (!_loadAll.value) {
            _limit.value += PAGE_SIZE
        }
    }

    suspend fun paste(pasteData: PasteData) {
        // The second press of a double click arrives while the first paste is still
        // writing the clipboard or sending the keystroke; running both breaks the paste.
        if (!pasteInFlight.tryLock()) return
        try {
            pasteOnce(pasteData)
            delay(REPEAT_PRESS_GUARD)
        } finally {
            pasteInFlight.unlock()
        }
    }

    private suspend fun pasteOnce(pasteData: PasteData) {
        val written =
            withContext(ioDispatcher) {
                pasteboardService
                    .tryWritePasteboard(
                        pasteData = pasteData,
                        localOnly = true,
                        // Keep history order stable while the user walks through it
                        updateCreateTime = false,
                    ).isSuccess
            }
        if (!written) {
            logger.warn { "Paste panel failed to write paste ${pasteData.id} to the clipboard" }
            return
        }
        appWindowManager.toPaste()
        _highlightedId.value = pasteData.id
    }
}
