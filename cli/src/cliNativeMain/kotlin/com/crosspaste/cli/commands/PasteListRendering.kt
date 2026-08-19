package com.crosspaste.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.mordant.rendering.TextColors
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString

/** Joins embedded newlines and whitespace runs into single spaces. */
internal fun collapsePreviewWhitespace(text: String): String = text.trim().replace(whitespaceRun, " ")

private val whitespaceRun = Regex("\\s+")

/**
 * Approximate wcwidth. Codepoints with East_Asian_Width Wide/Fullwidth count
 * 2 (complete generated interval table, see [EAST_ASIAN_WIDE_RANGES]);
 * joiners/variation selectors count 0 — except VS16, which requests emoji
 * presentation and can widen its base by one, so it counts 1 to keep the
 * estimate biased toward overestimating (a preview truncated one cell early
 * is invisible, one cell late wraps the whole row). ZWJ sequences are counted
 * per component for the same reason. Ambiguous-width chars count 1, matching
 * the default terminal convention.
 */
internal fun approxCellWidth(codepoint: Int): Int =
    when (codepoint) {
        in 0x0300..0x036F, // combining diacritical marks
        0x200C, // zero width non-joiner
        0x200D, // zero width joiner
        in 0xFE00..0xFE0E, // variation selectors (text presentation)
        -> 0
        0xFE0F -> 1 // VS16: emoji presentation, may widen the base char
        else -> if (isEastAsianWide(codepoint)) 2 else 1
    }

private fun isEastAsianWide(codepoint: Int): Boolean {
    val table = EAST_ASIAN_WIDE_RANGES
    var low = 0
    var high = table.size / 2 - 1
    while (low <= high) {
        val mid = (low + high) / 2
        when {
            codepoint > table[mid * 2 + 1] -> low = mid + 1
            codepoint < table[mid * 2] -> high = mid - 1
            else -> return true
        }
    }
    return false
}

/**
 * Longest prefix of [text] that fits in [maxCells] display cells. Never
 * splits a surrogate pair, and keeps a base char together with its trailing
 * zero-width marks/joiners and VS16 (dropping only a variation selector
 * would silently change the glyph's presentation).
 */
internal fun truncateToCellWidth(
    text: String,
    maxCells: Int,
): String {
    var cells = 0
    var i = 0
    while (i < text.length) {
        // Consume one cluster: a base codepoint plus any zero-width
        // continuations (combining marks, ZWJ/ZWNJ, variation selectors).
        var j = i
        var clusterCells = 0
        do {
            val codepoint = codepointAt(text, j)
            clusterCells += approxCellWidth(codepoint)
            j += if (codepoint > 0xFFFF) 2 else 1
        } while (j < text.length && isClusterContinuation(codepointAt(text, j)))
        if (cells + clusterCells > maxCells) break
        cells += clusterCells
        i = j
    }
    return text.substring(0, i)
}

private fun codepointAt(
    text: String,
    index: Int,
): Int {
    val high = text[index]
    return if (high.isHighSurrogate() && index + 1 < text.length && text[index + 1].isLowSurrogate()) {
        0x10000 + ((high.code - 0xD800) shl 10) + (text[index + 1].code - 0xDC00)
    } else {
        high.code
    }
}

private fun isClusterContinuation(codepoint: Int): Boolean = codepoint == 0xFE0F || approxCellWidth(codepoint) == 0

/**
 * The row width budget. Interactive stdout uses the detected terminal size.
 * Non-interactive stdout (pipes, `./gradlew :cli:run`) can't be probed, and
 * mordant then ignores COLUMNS and falls back to 79 — so honor an explicit
 * COLUMNS from the caller before accepting that fallback.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun CliktCommand.rowWidthBudget(): Int =
    if (terminal.terminalInfo.outputInteractive) {
        terminal.size.width
    } else {
        platform.posix
            .getenv("COLUMNS")
            ?.toKString()
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
            ?: terminal.size.width
    }

/**
 * Row rendering for the `history`/`search` tables: the preview fills whatever
 * terminal width is left after the fixed columns instead of being cut at a
 * fixed char count, and truncation is by display cells so wide CJK/emoji text
 * never overflows the line and wraps.
 *
 * Mordant's table widget is deliberately not used: its cell truncation
 * (`Span.take`) counts chars while its budget is in cells, so a NOWRAP cell
 * full of wide chars renders wider than the column (mordant 3.0.2).
 */
internal fun CliktCommand.printPasteRows(
    items: List<PasteSummaryDto>,
    showRemote: Boolean,
    showSize: Boolean,
) {
    val width = rowWidthBudget()
    for (item in items) {
        val fav = if (item.tagged) "*" else " "
        val id = item.id.toString().padStart(8)
        val type = item.typeName.padEnd(6)
        val remote = if (item.remote) "R" else "L"
        val time = formatRelativeTime(item.createTime).padEnd(8)
        val size = formatSize(item.size).padEnd(6)
        // The fixed columns are all ASCII, so char count == cell count.
        val prefixCells =
            fav.length + 1 + id.length + 1 + type.length + 1 +
                (if (showRemote) remote.length + 1 else 0) +
                time.length + 1 +
                (if (showSize) size.length + 1 else 0)
        val preview =
            truncateToCellWidth(
                collapsePreviewWhitespace(item.preview),
                maxCells = width - prefixCells,
            )
        val line =
            buildString {
                append(if (item.tagged) TextColors.yellow(fav) else fav).append(' ')
                append(id).append(' ')
                append(TextColors.cyan(type)).append(' ')
                if (showRemote) append(remote).append(' ')
                append(TextColors.gray(time)).append(' ')
                if (showSize) append(TextColors.gray(size)).append(' ')
                append(preview)
            }
        echo(line)
    }
}
