package com.crosspaste.cli.commands.pick

import com.crosspaste.cli.api.CliClient
import com.crosspaste.cli.commands.PasteDetailResponse
import com.crosspaste.cli.commands.PasteListResponse
import com.crosspaste.cli.commands.PasteSummaryDto
import com.crosspaste.cli.commands.buildListQuery
import com.crosspaste.cli.commands.formatSize
import com.crosspaste.cli.commands.readImageWithinBudget
import com.crosspaste.cli.platform.TerminalImageProtocol
import com.crosspaste.cli.platform.detectTerminalImageProtocol
import com.crosspaste.cli.platform.fitImageCellBox
import com.crosspaste.cli.platform.isRawModeReadTimeout
import com.crosspaste.cli.platform.kittyDeleteImages
import com.crosspaste.cli.platform.parsePngDimensions
import com.crosspaste.cli.platform.restoreConsoleModes
import com.crosspaste.cli.platform.writeItermInlineImage
import com.crosspaste.cli.platform.writeKittyInlineImage
import com.github.ajalt.mordant.input.RawModeScope
import com.github.ajalt.mordant.input.enterRawMode
import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okio.Path.Companion.toPath
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

internal sealed interface PickOutcome {
    data class Copied(
        val item: PasteSummaryDto,
    ) : PickOutcome

    data class Edit(
        val item: PasteSummaryDto,
    ) : PickOutcome

    data object Cancelled : PickOutcome
}

private val KEY_POLL_INTERVAL = 33L.milliseconds
private val SEARCH_DEBOUNCE = 150L.milliseconds
private val PREVIEW_DEBOUNCE = 200L.milliseconds
private val SPINNER_INTERVAL = 80L.milliseconds
private val COPY_FLASH_DURATION = 120L.milliseconds

/**
 * Idle delay before transmitting an inline image: transmission re-sends the
 * whole encoded file, so it must not ride every repaint while the user is
 * still typing or scrolling.
 */
private val IMAGE_DRAW_IDLE = 250L.milliseconds

/**
 * Quiet window re-poll while the picker is open, so pastes copied (or synced
 * in) meanwhile appear live. Cheap over the local socket; replaced by a push
 * subscription once the app exposes one (the `watch` feature).
 */
private val LIST_REFRESH_INTERVAL = 1000L.milliseconds

/**
 * Per-image byte ceiling for pick's panel. Deliberately much smaller than the
 * 20 MiB `paste` allows: transmission happens synchronously inside the event
 * loop (base64 inflates by ~4/3), and input must not freeze noticeably.
 * Oversized images fall back to the path listing.
 */
private const val MAX_PICK_IMAGE_BYTES = 5L * 1024 * 1024

private const val IMAGE_MAX_COLUMNS = 60

private const val MIN_TERMINAL_WIDTH = 20
private const val MIN_TERMINAL_HEIGHT = 6

private val ESC = 27.toChar()

// Raw control sequences the TUI needs beyond mordant's API: the alternate
// screen buffer, synchronized output (flicker-free repaints on terminals
// that support CSI 2026), and cursor visibility.
private val ALT_SCREEN_ENTER = "$ESC[?1049h"
private val ALT_SCREEN_EXIT = "$ESC[?1049l"
private val SYNC_BEGIN = "$ESC[?2026h"
private val SYNC_END = "$ESC[?2026l"
private val CURSOR_HIDE = "$ESC[?25l"
private val CURSOR_SHOW = "$ESC[?25h"
private val CURSOR_HOME = "$ESC[H"
private val CLEAR_TO_LINE_END = "$ESC[K"
private val CLEAR_TO_SCREEN_END = "$ESC[J"

/**
 * Identifies the exact request a result (or failure) answers: sequence AND
 * the query/filters snapshot it was issued for. Every outcome is judged
 * against the LIVE state — a bare sequence is not enough, because between a
 * query edit and the debounced re-request the latest sequence still names
 * the OLD query's request; its late response must not resurface as
 * authoritative results, and its late FAILURE must not tear down the picker.
 */
private data class RequestKey(
    val seq: Int,
    val isSearch: Boolean,
    /** Null for window requests (they carry no keyword). */
    val query: String?,
    val filters: PickFilters,
)

private sealed interface FetchMsg {
    data class ListResult(
        val key: RequestKey,
        val list: PasteListResponse,
    ) : FetchMsg

    data class Preview(
        val id: Long,
        val detail: PasteDetailResponse,
    ) : FetchMsg

    data class Failed(
        val key: RequestKey,
        val cause: Throwable,
    ) : FetchMsg
}

/**
 * The impure half of the picker: raw-mode key loop, repaints, and async
 * fetches. All list/selection logic lives in [PickState]; all layout in
 * [renderPickFrame] — this class only wires them to the terminal and the
 * local peer.
 */
internal class PickTui(
    private val terminal: Terminal,
    private val client: CliClient,
    private val state: PickState,
    private val limit: Int,
) {
    private val timeSource = TimeSource.Monotonic
    private val imageProtocol = detectTerminalImageProtocol()

    suspend fun run(): PickOutcome =
        coroutineScope {
            // Fetches get their own child scope so that producing an outcome
            // cancels whatever is still in flight — coroutineScope would
            // otherwise WAIT for those children, delaying Enter/Esc/Ctrl-E by
            // up to a full request timeout
            val fetchScope = CoroutineScope(coroutineContext + SupervisorJob(coroutineContext[Job]))
            val session = Session(fetchScope)
            terminal.rawPrint(ALT_SCREEN_ENTER + CURSOR_HIDE)
            try {
                terminal.enterRawMode().use { raw ->
                    session.launchInitialFetches()
                    while (true) {
                        session.tick()
                        session.readAndHandleKey(raw)?.let { return@coroutineScope it }
                    }
                }
                // enterRawMode().use returns Unit; the loop above always
                // returns through an outcome, so this is unreachable
                error("pick loop ended without an outcome")
            } finally {
                fetchScope.cancel()
                session.deleteDrawnKittyImage()
                terminal.rawPrint(CURSOR_SHOW + ALT_SCREEN_EXIT)
                // Undo Mordant's broken native-Windows raw-mode restore (it
                // writes console mode 0 instead of the saved value); no-op on
                // POSIX. Runs on every exit from the loop, so the Ctrl-E
                // editor hop also gets a sane console back.
                restoreConsoleModes()
            }
        }

    /**
     * One picker run's mutable state plus the per-tick steps of the event
     * loop, so each concern (resize, fetch results, timers, repaint, inline
     * image, keys) stays its own small method.
     */
    private inner class Session(
        private val fetchScope: CoroutineScope,
    ) {
        private val results = Channel<FetchMsg>(Channel.UNLIMITED)
        private var fetchSeq = 0
        private var latestWindowSeq = 0
        private var latestSearchSeq = 0

        // One live job per kind: a rapid burst of filter keys must replace
        // the previous query, not stack dozens of them on the server
        private var windowJob: Job? = null
        private var searchJob: Job? = null
        private var windowInFlight = false
        private var searchInFlight = false

        // The last applied window payload: an unchanged quiet re-poll must
        // not repaint (iTerm would retransmit the inline image every time)
        private var lastWindowItems: List<PasteSummaryDto>? = null
        private var lastWindowTotal = -1L

        private var previewDetail: PasteDetailResponse? = null
        private var previewRequestedId: Long? = null
        private var searchDeadline: TimeMark? = null
        private var previewDeadline: TimeMark? = null
        private var spinnerAt = timeSource.markNow()
        private var spinnerFrame = 0
        private var dirty = true
        private var imageDrawnId: Long? = null
        private var imageFailedId: Long? = null
        private var imageDeadline: TimeMark? = null
        private var lastPanelRow: Int? = null
        private var lastSize: Pair<Int, Int>? = null
        private var lastRefreshAt = timeSource.markNow()

        fun launchInitialFetches() {
            launchList(isSearch = false)
            if (state.query.isNotBlank()) launchList(isSearch = true)
        }

        /** Everything one loop iteration does before waiting on a key. */
        fun tick() {
            checkResize()
            drainResults()
            tickTimers()
            repaintIfDirty()
            drawImageIfDue()
        }

        /**
         * Waits up to [KEY_POLL_INTERVAL] for a key and applies it; a
         * non-null return is the picker's final outcome.
         */
        suspend fun readAndHandleKey(raw: RawModeScope): PickOutcome? {
            // Mordant 3.0.2's native-Windows readRawEvent throws a plain
            // RuntimeException("Timeout reading from console input") when the
            // wait expires instead of returning null like the other platforms
            // (readKeyOrNull only swallows TimeoutException), so every idle
            // poll tick would crash the picker. Only that known signature is
            // treated as "no key this tick" (isRawModeReadTimeout is
            // platform-split: always false on POSIX); real console errors
            // still propagate and end the picker.
            val event =
                try {
                    raw.readKeyOrNull(KEY_POLL_INTERVAL)
                } catch (e: RuntimeException) {
                    if (isRawModeReadTimeout(e)) null else throw e
                } ?: return null
            val action = toPickAction(event, state.query.isEmpty()) ?: return null
            val selectedBefore = state.selectedItem?.id
            applyEffect(state.handle(action, pageSize()))?.let { return it }
            if (state.selectedItem?.id != selectedBefore) {
                previewDetail = null
                previewRequestedId = null
                imageFailedId = null
            }
            return null
        }

        fun deleteDrawnKittyImage() {
            if (imageDrawnId != null && imageProtocol == TerminalImageProtocol.KITTY) {
                kittyDeleteImages { terminal.rawPrint(it) }
            }
        }

        private fun refreshSpinner() {
            state.searching = windowInFlight || searchInFlight
        }

        // A result or failure only counts while its request key still
        // describes what the user is looking at
        private fun isCurrent(key: RequestKey): Boolean {
            val latest = if (key.isSearch) latestSearchSeq else latestWindowSeq
            return key.seq == latest &&
                key.filters == state.filters &&
                (!key.isSearch || key.query == state.query)
        }

        private fun launchList(
            isSearch: Boolean,
            quiet: Boolean = false,
        ) {
            val seq = ++fetchSeq
            if (isSearch) latestSearchSeq = seq else latestWindowSeq = seq
            val key =
                RequestKey(
                    seq = seq,
                    isSearch = isSearch,
                    query = if (isSearch) state.query else null,
                    filters = state.filters,
                )
            // A quiet background refresh must not blink the spinner
            if (!quiet) {
                if (isSearch) searchInFlight = true else windowInFlight = true
                refreshSpinner()
                if (!isSearch) {
                    // The change-detection baseline only dedupes QUIET
                    // re-polls; a user-initiated fetch (state was cleared
                    // for new filters) must always apply, even when the
                    // fresh result happens to equal the previous one
                    lastWindowItems = null
                    lastWindowTotal = -1L
                }
            }
            val job =
                fetchScope.launch(Dispatchers.Default) {
                    val path =
                        "/cli/history" +
                            buildListQuery(
                                limit,
                                key.filters.types,
                                key.filters.tag,
                                key.filters.sortParam,
                                key.query,
                            )
                    try {
                        val list = client.getBody(path, PasteListResponse.serializer())
                        results.trySend(FetchMsg.ListResult(key, list))
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        results.trySend(FetchMsg.Failed(key, e))
                    }
                }
            if (isSearch) {
                searchJob?.cancel()
                searchJob = job
            } else {
                windowJob?.cancel()
                windowJob = job
            }
        }

        private fun launchPreview(id: Long) {
            fetchScope.launch(Dispatchers.Default) {
                runCatching { client.getBody("/cli/paste/$id", PasteDetailResponse.serializer()) }
                    .onSuccess { results.trySend(FetchMsg.Preview(id, it)) }
                // Preview failures are cosmetic; the panel just keeps the summary
            }
        }

        private fun imageCandidate(detail: PasteDetailResponse?): Pair<PasteSummaryDto, String>? {
            if (imageProtocol == null || !state.previewOpen || state.helpOpen) return null
            val item = state.selectedItem?.takeIf { it.typeName == "image" } ?: return null
            // A failed draw (unsupported format, too big, read error) is
            // not retried; the panel shows the path fallback instead
            if (item.id == imageFailedId) return null
            val path = detail?.takeIf { it.id == item.id }?.filePaths?.firstOrNull() ?: return null
            return item to path
        }

        // A resize repaints on the next tick even without input.
        // terminal.size alone serves mordant's CACHED value — updateSize()
        // re-detects. Kitty placements are anchored to cells, so they are
        // dropped and re-placed at the panel's new position.
        private fun checkResize() {
            val sizeNow = terminal.updateSize()
            if (lastSize != null && lastSize != (sizeNow.width to sizeNow.height)) {
                dirty = true
                deleteDrawnKittyImage()
                imageDrawnId = null
            }
            lastSize = sizeNow.width to sizeNow.height
        }

        private fun drainResults() {
            while (true) {
                when (val msg = results.tryReceive().getOrNull() ?: break) {
                    is FetchMsg.ListResult -> onListResult(msg)
                    is FetchMsg.Preview -> onPreview(msg)
                    is FetchMsg.Failed -> onFailed(msg)
                }
                refreshSpinner()
            }
        }

        private fun clearInFlightIfLatest(key: RequestKey) {
            if (key.seq == (if (key.isSearch) latestSearchSeq else latestWindowSeq)) {
                if (key.isSearch) searchInFlight = false else windowInFlight = false
            }
        }

        private fun onListResult(msg: FetchMsg.ListResult) {
            clearInFlightIfLatest(msg.key)
            if (!isCurrent(msg.key)) {
                dirty = true
                return
            }
            if (msg.key.isSearch) {
                state.setSearchExtra(msg.list.items)
                dirty = true
            } else if (msg.list.items != lastWindowItems || msg.list.total != lastWindowTotal) {
                lastWindowItems = msg.list.items
                lastWindowTotal = msg.list.total
                state.setWindow(msg.list.items, msg.list.total)
                dirty = true
            }
        }

        private fun onPreview(msg: FetchMsg.Preview) {
            if (msg.id == state.selectedItem?.id) {
                previewDetail = msg.detail
            }
            dirty = true
        }

        private fun onFailed(msg: FetchMsg.Failed) {
            dirty = true
            clearInFlightIfLatest(msg.key)
            // Only a failure the user is still WAITING on surfaces (torn down
            // via finally-restore, then runCli reports or retries
            // AppNotRunning); a failure for an outdated query/filters is moot
            if (isCurrent(msg.key)) throw msg.cause
        }

        private fun tickTimers() {
            // Live refresh: pastes copied or synced in while the picker is
            // open surface within a second. Skipped while a window fetch is
            // running or the user is mid-keystroke (debounce pending).
            if (lastRefreshAt.elapsedNow() >= LIST_REFRESH_INTERVAL) {
                lastRefreshAt = timeSource.markNow()
                if (windowJob?.isActive != true && searchDeadline == null) {
                    launchList(isSearch = false, quiet = true)
                }
            }
            searchDeadline?.let { deadline ->
                if (deadline.elapsedNow() >= SEARCH_DEBOUNCE) {
                    searchDeadline = null
                    fireDebouncedSearch()
                    dirty = true
                }
            }
            previewDeadline?.let { deadline ->
                if (deadline.elapsedNow() >= PREVIEW_DEBOUNCE) {
                    previewDeadline = null
                    requestPreviewForSelection()
                }
            }
            if (state.searching && spinnerAt.elapsedNow() >= SPINNER_INTERVAL) {
                spinnerAt = timeSource.markNow()
                spinnerFrame++
                dirty = true
            }
        }

        private fun fireDebouncedSearch() {
            if (state.query.isNotBlank()) {
                launchList(isSearch = true)
            } else {
                // No keyword: nothing to search for, and a still-flying old
                // search must not survive
                searchJob?.cancel()
                searchInFlight = false
                refreshSpinner()
                state.setSearchExtra(emptyList())
            }
        }

        private fun requestPreviewForSelection() {
            state.selectedItem?.let { item ->
                if (item.id != previewRequestedId) {
                    previewRequestedId = item.id
                    launchPreview(item.id)
                }
            }
        }

        private fun repaintIfDirty() {
            if (!dirty) return
            dirty = false
            if (state.previewOpen && state.selectedItem?.id != previewRequestedId) {
                previewDeadline = previewDeadline ?: timeSource.markNow()
            }
            lastPanelRow =
                paint(spinnerFrame, previewDetail, imageFailedId, flashSelected = false)
                    .previewPanelRow
            // Repaints erase iTerm images (they live in cells); kitty
            // placements survive text redraws, so only a new selection
            // needs a retransmit there
            val candidate = imageCandidate(previewDetail)
            if (candidate != null && lastPanelRow != null) {
                val needsDraw =
                    imageDrawnId != candidate.first.id ||
                        imageProtocol == TerminalImageProtocol.ITERM
                if (needsDraw) imageDeadline = timeSource.markNow()
            } else {
                deleteDrawnKittyImage()
                imageDrawnId = null
                imageDeadline = null
            }
        }

        private fun drawImageIfDue() {
            val deadline = imageDeadline ?: return
            if (deadline.elapsedNow() < IMAGE_DRAW_IDLE) return
            imageDeadline = null
            val candidate = imageCandidate(previewDetail) ?: return
            val panelRow = lastPanelRow ?: return
            if (drawInlineImage(candidate.second, panelRow)) {
                imageDrawnId = candidate.first.id
            } else {
                // Repaint with the path fallback instead of leaving the
                // reserved lines blank
                imageFailedId = candidate.first.id
                dirty = true
            }
        }

        private suspend fun applyEffect(effect: PickEffect): PickOutcome? =
            when (effect) {
                PickEffect.None -> null
                PickEffect.Redraw -> {
                    dirty = true
                    null
                }
                PickEffect.RefetchDebounced -> {
                    searchDeadline = timeSource.markNow()
                    dirty = true
                    null
                }
                PickEffect.RefetchNow -> {
                    state.clearForRefetch()
                    launchInitialFetches()
                    dirty = true
                    null
                }
                is PickEffect.Accept -> {
                    paint(spinnerFrame, previewDetail, imageFailedId, flashSelected = true)
                    delay(COPY_FLASH_DURATION)
                    PickOutcome.Copied(effect.item)
                }
                is PickEffect.Edit -> PickOutcome.Edit(effect.item)
                PickEffect.Cancel -> PickOutcome.Cancelled
            }
    }

    private fun pageSize(): Int =
        pickListHeight(
            height = terminal.size.height,
            previewOpen = state.previewOpen,
            helpOpen = state.helpOpen,
            filterBarOpen = state.filterBar != null,
        )

    /**
     * Transmits the first stored image into the preview panel, display-bounded
     * to a cell box (the terminal scales; the CLI still never decodes pixels).
     * Runs only after [IMAGE_DRAW_IDLE] of quiet — the payload is the whole
     * encoded file.
     */
    private fun drawInlineImage(
        path: String,
        panelRow: Int,
    ): Boolean {
        // SIXEL renders from app-transcoded pixels, not stored files; it is
        // wired into this preview in #4848 and never selected until then
        val protocol =
            imageProtocol?.takeIf { it != TerminalImageProtocol.SIXEL } ?: return false
        val bytes =
            readImageWithinBudget(
                path = path,
                budget = MAX_PICK_IMAGE_BYTES,
                requirePng = protocol == TerminalImageProtocol.KITTY,
            ) ?: return false
        val maxColumns = (terminal.size.width - 6).coerceIn(10, IMAGE_MAX_COLUMNS)
        val maxRows = PREVIEW_PANEL_LINES - 1
        // PNG headers carry the pixel size; other formats (iTerm only) fall
        // back to the full box, which iTerm letterboxes itself
        val box =
            parsePngDimensions(bytes)?.let { (w, h) -> fitImageCellBox(w, h, maxColumns, maxRows) }
                ?: (maxColumns to maxRows)
        val write: (String) -> Unit = { terminal.rawPrint(it) }
        // Save the cursor, draw at the panel's first content row, restore
        terminal.rawPrint("${ESC}7$ESC[${panelRow + 2};3H")
        when (protocol) {
            TerminalImageProtocol.KITTY -> {
                kittyDeleteImages(write)
                writeKittyInlineImage(bytes, write, columns = box.first, rows = box.second)
            }
            TerminalImageProtocol.ITERM ->
                writeItermInlineImage(
                    name = path.toPath().name,
                    bytes = bytes,
                    write = write,
                    widthCells = box.first,
                    heightCells = box.second,
                )
            TerminalImageProtocol.SIXEL -> {} // unreachable: filtered out above
        }
        terminal.rawPrint("${ESC}8")
        return true
    }

    private fun paint(
        spinnerFrame: Int,
        previewDetail: PasteDetailResponse?,
        imageFailedId: Long?,
        flashSelected: Boolean,
    ): PickFrame {
        // Below the minimum (including a pty reporting 0×0) a full frame
        // would wrap and scramble; show a short notice instead and keep the
        // key loop alive so a resize or Esc still works
        val size = terminal.size
        if (size.width < MIN_TERMINAL_WIDTH || size.height < MIN_TERMINAL_HEIGHT) {
            val notice = "pick: terminal too small".take(size.width.coerceAtLeast(1))
            terminal.rawPrint(SYNC_BEGIN + CURSOR_HIDE + CURSOR_HOME + notice + CLEAR_TO_SCREEN_END + SYNC_END)
            return PickFrame(lines = emptyList(), cursorRow = 1, cursorCol = 1)
        }
        val width = size.width
        val height = size.height
        val selected = state.selectedItem
        val frame =
            renderPickFrame(
                data =
                    PickFrameData(
                        query = state.query,
                        rows = state.rows,
                        selected = state.rows.indexOfFirst { it.item.id == selected?.id }.coerceAtLeast(0),
                        total = state.total,
                        filters = state.filters,
                        searching = state.searching,
                        spinnerFrame = spinnerFrame,
                        previewOpen = state.previewOpen,
                        helpOpen = state.helpOpen,
                        filterBar = state.filterBar,
                        tagNames = state.tagNames,
                        previewLines = previewLines(selected, previewDetail, imageFailedId),
                        previewLink = selected?.takeIf { it.typeName == "link" }?.preview?.trim(),
                        flashSelected = flashSelected,
                    ),
                width = width,
                height = height,
                level = terminal.terminalInfo.ansiLevel,
            )
        val body =
            frame.lines.joinToString("\r\n") { it + CLEAR_TO_LINE_END } +
                CLEAR_TO_SCREEN_END +
                "$ESC[${frame.cursorRow};${frame.cursorCol}H" +
                CURSOR_SHOW
        terminal.rawPrint(SYNC_BEGIN + CURSOR_HIDE + CURSOR_HOME + body + SYNC_END)
        return frame
    }

    /**
     * Preview content: the fetched detail's full text when available, else
     * the row summary. Image pastes on a graphics-capable terminal reserve
     * blank panel lines under the metadata for the inline image (only the
     * FIRST image; the metadata line says so when there are more); without a
     * protocol — and for file pastes — the stored paths are listed instead.
     */
    private fun previewLines(
        item: PasteSummaryDto?,
        detail: PasteDetailResponse?,
        imageFailedId: Long?,
    ): List<String> {
        item ?: return emptyList()
        val fetched = detail?.takeIf { it.id == item.id }
        return when (item.typeName) {
            "image", "file" -> {
                val paths = fetched?.filePaths.orEmpty()
                val counter = if (paths.size > 1) " · showing 1/${paths.size}" else ""
                val metadata = "${item.typeName} · ${formatSize(item.size)} · ${item.source ?: "unknown source"}"
                val canInline =
                    item.typeName == "image" &&
                        imageProtocol != null &&
                        paths.isNotEmpty() &&
                        item.id != imageFailedId
                if (canInline) {
                    listOf(metadata + counter) + List(PREVIEW_PANEL_LINES - 1) { "" }
                } else {
                    listOf(metadata) + paths.take(PREVIEW_PANEL_LINES - 1)
                }
            }
            else -> {
                val content = fetched?.content ?: item.preview
                content.lineSequence().take(PREVIEW_PANEL_LINES).toList()
            }
        }
    }
}
