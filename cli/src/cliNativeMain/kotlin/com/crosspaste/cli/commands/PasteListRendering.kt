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
 * Approximate wcwidth. Deliberately biased toward overestimating: a preview
 * truncated one cell early is invisible, one cell late wraps the whole row.
 * Wide CJK blocks and emoji count 2; joiners/variation selectors count 0
 * (except VS16, which requests emoji presentation and can widen its base by
 * one); everything else counts 1.
 */
internal fun approxCellWidth(codepoint: Int): Int =
    when (codepoint) {
        in 0x0300..0x036F, // combining diacritical marks
        0x200C, // zero width non-joiner
        0x200D, // zero width joiner
        in 0xFE00..0xFE0E, // variation selectors (text presentation)
        -> 0
        0xFE0F -> 1 // VS16: emoji presentation, may widen the base char
        in 0x1100..0x115F, // Hangul Jamo
        in 0x2329..0x232A,
        in 0x2600..0x27BF, // misc symbols/dingbats, often emoji presentation
        in 0x2E80..0x303E, // CJK radicals, punctuation
        in 0x3041..0x33FF, // kana, CJK symbols
        in 0x3400..0x4DBF, // CJK ext A
        in 0x4E00..0x9FFF, // CJK unified
        in 0xA000..0xA4CF, // Yi
        in 0xA960..0xA97F, // Hangul Jamo ext A
        in 0xAC00..0xD7A3, // Hangul syllables
        in 0xF900..0xFAFF, // CJK compat
        in 0xFE10..0xFE19, // vertical forms
        in 0xFE30..0xFE6F, // CJK compat forms
        in 0xFF00..0xFF60, // fullwidth forms
        in 0xFFE0..0xFFE6,
        in 0x1F1E6..0x1F1FF, // regional indicators (flags)
        in 0x1F300..0x1FAFF, // emoji
        in 0x20000..0x3FFFD, // CJK ext B+
        -> 2
        else -> 1
    }

/**
 * Longest prefix of [text] that fits in [maxCells] display cells. Never
 * splits a surrogate pair.
 */
internal fun truncateToCellWidth(
    text: String,
    maxCells: Int,
): String {
    var cells = 0
    var i = 0
    while (i < text.length) {
        val high = text[i]
        val pair = high.isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate()
        val codepoint =
            if (pair) {
                0x10000 + ((high.code - 0xD800) shl 10) + (text[i + 1].code - 0xDC00)
            } else {
                high.code
            }
        cells += approxCellWidth(codepoint)
        if (cells > maxCells) break
        i += if (pair) 2 else 1
    }
    return text.substring(0, i)
}

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
