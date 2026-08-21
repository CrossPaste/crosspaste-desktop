package com.crosspaste.cli.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv

/**
 * Everything the renderers need to know about inline image display.
 *
 * Sixel capability probing (design: ai/design/cli-sixel-support.md, issue
 * #4847): environment variables only nominate sixel CANDIDATES (see
 * [isSixelCandidate]); before SIXEL is ever selected, the terminal must
 * answer a DA1 query (`CSI c`) with attribute 4. This is what keeps an older
 * Windows Terminal — same `WT_SESSION`, no sixel — on the path fallback
 * instead of receiving escape garbage.
 */
internal class TerminalImageDisplay(
    val protocol: TerminalImageProtocol,
    /** Terminal cell size in pixels; only meaningful for SIXEL layout. */
    val cellWidthPx: Int = DEFAULT_CELL_WIDTH_PX,
    val cellHeightPx: Int = DEFAULT_CELL_HEIGHT_PX,
)

/** Fallback cell size (~Windows Terminal default font) when CSI 16 t goes unanswered. */
internal const val DEFAULT_CELL_WIDTH_PX = 10
internal const val DEFAULT_CELL_HEIGHT_PX = 20

/** Upper bound for a believable cell dimension reply, in pixels. */
internal const val MAX_SANE_CELL_PX = 512

/** Whole-transaction budget; a live terminal answers in single-digit millis. */
internal const val SIXEL_PROBE_TIMEOUT_MILLIS = 250

/**
 * One write, two queries: XTWINOPS `CSI 16 t` (cell pixel size, may be
 * ignored) then DA1 `CSI c` (universally answered). Replies preserve request
 * order, so the DA1 reply doubles as the end-of-transaction marker.
 */
internal val SIXEL_PROBE_PAYLOAD: String = "$TERM_ESC[16t$TERM_ESC[c"

/**
 * Resolves which inline-image protocol to use, running the sixel probe when
 * the environment nominates a candidate. Returns null when images should fall
 * back to printed file paths. All effects are injectable; production callers
 * pass Mordant's interactivity flags and the platform [queryTerminalRaw].
 */
@OptIn(ExperimentalForeignApi::class)
internal fun resolveTerminalImageDisplay(
    stdinInteractive: Boolean,
    stdoutInteractive: Boolean,
    env: (String) -> String? = { getenv(it)?.toKString() },
    query: (payload: String, timeoutMillis: Int, isComplete: (String) -> Boolean) -> String? =
        ::queryTerminalRaw,
): TerminalImageDisplay? {
    detectTerminalImageProtocol(env)?.let { return TerminalImageDisplay(it) }
    if (!isSixelCandidate(env)) return null
    // The probe writes to the terminal and reads the reply off stdin; with
    // either end redirected there is nothing safe to probe (and a pipe would
    // swallow the escape bytes anyway)
    if (!stdinInteractive || !stdoutInteractive) return null
    val reply =
        query(SIXEL_PROBE_PAYLOAD, SIXEL_PROBE_TIMEOUT_MILLIS, ::containsDa1Reply)
            ?: return null
    if (!parseDa1SixelSupport(reply)) return null
    val cellSize = parseCellSizeReply(reply)
    return TerminalImageDisplay(
        protocol = TerminalImageProtocol.SIXEL,
        cellWidthPx = cellSize?.first ?: DEFAULT_CELL_WIDTH_PX,
        cellHeightPx = cellSize?.second ?: DEFAULT_CELL_HEIGHT_PX,
    )
}

/** True once [buffer] holds a complete DA1 reply (`CSI ? ... c`). */
internal fun containsDa1Reply(buffer: String): Boolean {
    val start = buffer.indexOf("$TERM_ESC[?")
    if (start < 0) return false
    return buffer.indexOf('c', startIndex = start + 3) >= 0
}

/**
 * Whether a DA1 reply advertises sixel. Format: `CSI ? class ; attr ; ... c`
 * — the FIRST parameter is the device conformance class (62/63/...), not an
 * attribute, so only the parameters after it may claim attribute 4 (sixel).
 */
internal fun parseDa1SixelSupport(reply: String): Boolean {
    val start = reply.indexOf("$TERM_ESC[?")
    if (start < 0) return false
    val end = reply.indexOf('c', startIndex = start + 3)
    if (end < 0) return false
    return reply
        .substring(start + 3, end)
        .split(';')
        .drop(1)
        .any { it == "4" }
}

/**
 * Parses the XTWINOPS cell-size reply `CSI 6 ; heightPx ; widthPx t` (height
 * first!) into (widthPx, heightPx). Returns null when absent or implausible —
 * the caller then assumes the [DEFAULT_CELL_WIDTH_PX] x
 * [DEFAULT_CELL_HEIGHT_PX] cell.
 */
internal fun parseCellSizeReply(reply: String): Pair<Int, Int>? {
    val marker = "$TERM_ESC[6;"
    val start = reply.indexOf(marker)
    if (start < 0) return null
    val end = reply.indexOf('t', startIndex = start + marker.length)
    if (end < 0) return null
    val parts = reply.substring(start + marker.length, end).split(';')
    if (parts.size != 2) return null
    val height = parts[0].toIntOrNull() ?: return null
    val width = parts[1].toIntOrNull() ?: return null
    if (height !in 1..MAX_SANE_CELL_PX || width !in 1..MAX_SANE_CELL_PX) return null
    return width to height
}
