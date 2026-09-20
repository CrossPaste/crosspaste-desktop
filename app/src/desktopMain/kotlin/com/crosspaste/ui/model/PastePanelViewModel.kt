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
 * whatever app currently has focus (the panel never takes focus itself) and moves the
 * "next" marker to the row copied right after it, so a batch copied as A, B, C is
 * pasted back in the same order with one click per field. The marker is tracked by
 * paste id rather than index so that new copies arriving while the panel is open do
 * not shift it. A double click pastes once: presses that land while a paste is still
 * running, or shortly after it, are dropped.
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

    /** Id of the row to paste next; null means the newest row. */
    private val _nextId = MutableStateFlow<Long?>(null)

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

    val nextIndex: StateFlow<Int> =
        combine(items, _nextId) { list, nextId ->
            nextId
                ?.let { id -> list.indexOfFirst { it.id == id } }
                ?.takeIf { it >= 0 }
                ?: 0
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0,
        )

    /** Every open starts fresh: newest row is next, first page only. */
    fun onShown() {
        _nextId.value = null
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
        val list = items.value
        val index = list.indexOfFirst { it.id == pasteData.id }
        // The row copied right after this one; stays put when there is nothing newer.
        val next = if (index > 0) list[index - 1] else pasteData
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
        _nextId.value = next.id
    }
}
