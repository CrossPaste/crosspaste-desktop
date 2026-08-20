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
import com.crosspaste.cli.platform.kittyDeleteImages
import com.crosspaste.cli.platform.parsePngDimensions
import com.crosspaste.cli.platform.writeItermInlineImage
import com.crosspaste.cli.platform.writeKittyInlineImage
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
            val results = Channel<FetchMsg>(Channel.UNLIMITED)
            var fetchSeq = 0
            var latestWindowSeq = 0
            var latestSearchSeq = 0
            // One live job per kind: a rapid burst of filter keys must replace
            // the previous query, not stack dozens of them on the server
            var windowJob: kotlinx.coroutines.Job? = null
            var searchJob: kotlinx.coroutines.Job? = null
            var windowInFlight = false
            var searchInFlight = false
            // The last applied window payload: an unchanged quiet re-poll must
            // not repaint (iTerm would retransmit the inline image every time)
            var lastWindowItems: List<PasteSummaryDto>? = null
            var lastWindowTotal = -1L

            fun refreshSpinner() {
                state.searching = windowInFlight || searchInFlight
            }

            // A result or failure only counts while its request key still
            // describes what the user is looking at
            fun isCurrent(key: RequestKey): Boolean {
                val latest = if (key.isSearch) latestSearchSeq else latestWindowSeq
                return key.seq == latest &&
                    key.filters == state.filters &&
                    (!key.isSearch || key.query == state.query)
            }

            fun launchList(
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

            fun launchPreview(id: Long) {
                fetchScope.launch(Dispatchers.Default) {
                    runCatching { client.getBody("/cli/paste/$id", PasteDetailResponse.serializer()) }
                        .onSuccess { results.trySend(FetchMsg.Preview(id, it)) }
                    // Preview failures are cosmetic; the panel just keeps the summary
                }
            }

            launchList(isSearch = false)
            if (state.query.isNotBlank()) launchList(isSearch = true)

            var previewDetail: PasteDetailResponse? = null
            var previewRequestedId: Long? = null
            var searchDeadline: TimeMark? = null
            var previewDeadline: TimeMark? = null
            var spinnerAt = timeSource.markNow()
            var spinnerFrame = 0
            var dirty = true
            var imageDrawnId: Long? = null
            var imageFailedId: Long? = null
            var imageDeadline: TimeMark? = null
            var lastPanelRow: Int? = null
            var lastSize: Pair<Int, Int>? = null
            var lastRefreshAt = timeSource.markNow()

            fun imageCandidate(detail: PasteDetailResponse?): Pair<PasteSummaryDto, String>? {
                if (imageProtocol == null || !state.previewOpen || state.helpOpen) return null
                val item = state.selectedItem?.takeIf { it.typeName == "image" } ?: return null
                // A failed draw (unsupported format, too big, read error) is
                // not retried; the panel shows the path fallback instead
                if (item.id == imageFailedId) return null
                val path = detail?.takeIf { it.id == item.id }?.filePaths?.firstOrNull() ?: return null
                return item to path
            }

            terminal.rawPrint(ALT_SCREEN_ENTER + CURSOR_HIDE)
            try {
                terminal.enterRawMode().use { raw ->
                    while (true) {
                        // A resize repaints on the next tick even without input.
                        // terminal.size alone serves mordant's CACHED value —
                        // updateSize() re-detects. Kitty placements are anchored
                        // to cells, so they are dropped and re-placed at the
                        // panel's new position.
                        val sizeNow = terminal.updateSize()
                        if (lastSize != null && lastSize != (sizeNow.width to sizeNow.height)) {
                            dirty = true
                            if (imageDrawnId != null && imageProtocol == TerminalImageProtocol.KITTY) {
                                kittyDeleteImages { terminal.rawPrint(it) }
                            }
                            imageDrawnId = null
                        }
                        lastSize = sizeNow.width to sizeNow.height

                        while (true) {
                            val msg = results.tryReceive().getOrNull() ?: break
                            when (msg) {
                                is FetchMsg.ListResult -> {
                                    if (msg.key.seq == (if (msg.key.isSearch) latestSearchSeq else latestWindowSeq)) {
                                        if (msg.key.isSearch) searchInFlight = false else windowInFlight = false
                                    }
                                    if (isCurrent(msg.key)) {
                                        if (msg.key.isSearch) {
                                            state.setSearchExtra(msg.list.items)
                                            dirty = true
                                        } else if (msg.list.items != lastWindowItems ||
                                            msg.list.total != lastWindowTotal
                                        ) {
                                            lastWindowItems = msg.list.items
                                            lastWindowTotal = msg.list.total
                                            state.setWindow(msg.list.items, msg.list.total)
                                            dirty = true
                                        }
                                    } else {
                                        dirty = true
                                    }
                                }
                                is FetchMsg.Preview -> {
                                    if (msg.id == state.selectedItem?.id) {
                                        previewDetail = msg.detail
                                    }
                                    dirty = true
                                }
                                is FetchMsg.Failed -> {
                                    dirty = true
                                    if (msg.key.seq == (if (msg.key.isSearch) latestSearchSeq else latestWindowSeq)) {
                                        if (msg.key.isSearch) searchInFlight = false else windowInFlight = false
                                    }
                                    // Only a failure the user is still WAITING on
                                    // surfaces (torn down via finally-restore, then
                                    // runCli reports or retries AppNotRunning); a
                                    // failure for an outdated query/filters is moot
                                    if (isCurrent(msg.key)) throw msg.cause
                                }
                            }
                            refreshSpinner()
                        }

                        // Live refresh: pastes copied or synced in while the
                        // picker is open surface within a second. Skipped
                        // while a window fetch is running or the user is
                        // mid-keystroke (debounce pending).
                        if (lastRefreshAt.elapsedNow() >= LIST_REFRESH_INTERVAL) {
                            lastRefreshAt = timeSource.markNow()
                            if (windowJob?.isActive != true && searchDeadline == null) {
                                launchList(isSearch = false, quiet = true)
                            }
                        }

                        searchDeadline?.let { deadline ->
                            if (deadline.elapsedNow() >= SEARCH_DEBOUNCE) {
                                searchDeadline = null
                                if (state.query.isNotBlank()) {
                                    launchList(isSearch = true)
                                } else {
                                    // No keyword: nothing to search for, and a
                                    // still-flying old search must not survive
                                    searchJob?.cancel()
                                    searchInFlight = false
                                    refreshSpinner()
                                    state.setSearchExtra(emptyList())
                                }
                                dirty = true
                            }
                        }
                        previewDeadline?.let { deadline ->
                            if (deadline.elapsedNow() >= PREVIEW_DEBOUNCE) {
                                previewDeadline = null
                                state.selectedItem?.let { item ->
                                    if (item.id != previewRequestedId) {
                                        previewRequestedId = item.id
                                        launchPreview(item.id)
                                    }
                                }
                            }
                        }
                        if (state.searching && spinnerAt.elapsedNow() >= SPINNER_INTERVAL) {
                            spinnerAt = timeSource.markNow()
                            spinnerFrame++
                            dirty = true
                        }

                        if (dirty) {
                            dirty = false
                            if (state.previewOpen && state.selectedItem?.id != previewRequestedId) {
                                previewDeadline = previewDeadline ?: timeSource.markNow()
                            }
                            lastPanelRow =
                                paint(spinnerFrame, previewDetail, imageFailedId, flashSelected = false)
                                    .previewPanelRow
                            // Repaints erase iTerm images (they live in cells);
                            // kitty placements survive text redraws, so only a
                            // new selection needs a retransmit there
                            val candidate = imageCandidate(previewDetail)
                            if (candidate != null && lastPanelRow != null) {
                                val needsDraw =
                                    imageDrawnId != candidate.first.id ||
                                        imageProtocol == TerminalImageProtocol.ITERM
                                if (needsDraw) imageDeadline = timeSource.markNow()
                            } else {
                                if (imageDrawnId != null && imageProtocol == TerminalImageProtocol.KITTY) {
                                    kittyDeleteImages { terminal.rawPrint(it) }
                                }
                                imageDrawnId = null
                                imageDeadline = null
                            }
                        }
                        imageDeadline?.let { deadline ->
                            if (deadline.elapsedNow() >= IMAGE_DRAW_IDLE) {
                                imageDeadline = null
                                val candidate = imageCandidate(previewDetail)
                                val panelRow = lastPanelRow
                                if (candidate != null && panelRow != null) {
                                    if (drawInlineImage(candidate.second, panelRow)) {
                                        imageDrawnId = candidate.first.id
                                    } else {
                                        // Repaint with the path fallback instead
                                        // of leaving the reserved lines blank
                                        imageFailedId = candidate.first.id
                                        dirty = true
                                    }
                                }
                            }
                        }

                        val event = raw.readKeyOrNull(KEY_POLL_INTERVAL) ?: continue
                        val action = toPickAction(event, state.query.isEmpty()) ?: continue
                        val selectedBefore = state.selectedItem?.id
                        when (val effect = state.handle(action, pageSize())) {
                            PickEffect.None -> Unit
                            PickEffect.Redraw -> dirty = true
                            PickEffect.RefetchDebounced -> {
                                searchDeadline = timeSource.markNow()
                                dirty = true
                            }
                            PickEffect.RefetchNow -> {
                                state.clearForRefetch()
                                launchList(isSearch = false)
                                if (state.query.isNotBlank()) launchList(isSearch = true)
                                dirty = true
                            }
                            is PickEffect.Accept -> {
                                paint(spinnerFrame, previewDetail, imageFailedId, flashSelected = true)
                                delay(COPY_FLASH_DURATION)
                                return@coroutineScope PickOutcome.Copied(effect.item)
                            }
                            is PickEffect.Edit -> return@coroutineScope PickOutcome.Edit(effect.item)
                            PickEffect.Cancel -> return@coroutineScope PickOutcome.Cancelled
                        }
                        if (state.selectedItem?.id != selectedBefore) {
                            previewDetail = null
                            previewRequestedId = null
                            imageFailedId = null
                        }
                    }
                }
                // enterRawMode().use returns Unit; the loop above always
                // returns through an outcome, so this is unreachable
                error("pick loop ended without an outcome")
            } finally {
                fetchScope.cancel()
                if (imageDrawnId != null && imageProtocol == TerminalImageProtocol.KITTY) {
                    kittyDeleteImages { terminal.rawPrint(it) }
                }
                terminal.rawPrint(CURSOR_SHOW + ALT_SCREEN_EXIT)
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
        val protocol = imageProtocol ?: return false
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
