package com.crosspaste.cli.commands.pick

import com.crosspaste.cli.commands.approxCellWidth
import com.crosspaste.cli.commands.formatRelativeTime
import com.crosspaste.cli.commands.formatSize
import com.crosspaste.cli.commands.sanitizeTerminalText
import com.crosspaste.cli.commands.truncateToCellWidth
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyle
import com.github.ajalt.mordant.rendering.TextStyles

/** Everything one frame needs; a pure snapshot so rendering is unit-testable. */
data class PickFrameData(
    val query: String,
    val rows: List<PickRow>,
    val selected: Int,
    val total: Long,
    val filters: PickFilters,
    val searching: Boolean,
    val spinnerFrame: Int,
    val previewOpen: Boolean,
    val helpOpen: Boolean,
    /** The open ←/→ filter selector bar, or null. */
    val filterBar: FilterBarKind?,
    /** All known tag names, for the tag selector bar. */
    val tagNames: List<String>,
    /** Pre-computed preview panel lines (detail content or metadata). */
    val previewLines: List<String>,
    /** URL to hyperlink (OSC 8) on the first preview line, for link pastes. */
    val previewLink: String?,
    /** Render the selected row in the copy-confirmation flash style. */
    val flashSelected: Boolean = false,
)

/** A rendered frame: exactly `height` lines plus the query-line cursor cell. */
data class PickFrame(
    val lines: List<String>,
    /** 1-based cursor position for the terminal CUP sequence. */
    val cursorRow: Int,
    val cursorCol: Int,
    /** 1-based row of the preview panel's separator line, null when closed. */
    val previewPanelRow: Int? = null,
)

internal val SPINNER_FRAMES = listOf("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏")

// Brand gradient endpoints (CrossPaste blue → cyan)
private const val BRAND_START = 0x1E80FF
private const val BRAND_END = 0x00E5C7

private const val BRAND_TEXT = "◤ CrossPaste"

internal fun typeStyle(typeName: String): TextStyle =
    when (typeName) {
        "text" -> TextColors.cyan
        "link" -> TextColors.brightBlue
        "image" -> TextColors.magenta
        "file" -> TextColors.yellow
        "html" -> TextColors.green
        "rtf" -> TextColors.green + TextStyles.dim
        "color" -> TextColors.white
        else -> TextColors.gray
    }

internal fun renderPickFrame(
    data: PickFrameData,
    width: Int,
    height: Int,
    level: AnsiLevel,
): PickFrame {
    val style = StylePalette(level)
    val lines = ArrayList<String>(height)
    lines += headerLine(data, width, style)

    // Long queries show their TAIL (like a shell input field): the cursor
    // always sits after the last visible char, clamped inside the frame —
    // positioning from the full query would park it off-screen
    val visibleQuery = tailToCellWidth(data.query, (width - 4).coerceAtLeast(1))
    lines += style.apply(style.accent, "❯ ") + visibleQuery
    data.filterBar?.let { lines += filterBarLine(it, data, width, style) }

    val footer = footerLine(width, style)
    val previewBlock =
        if (previewPanelVisible(height, data.previewOpen, data.helpOpen)) {
            previewBlock(data, width, style)
        } else {
            emptyList()
        }
    val listHeight =
        pickListHeight(height, data.previewOpen, data.helpOpen, filterBarOpen = data.filterBar != null)

    if (data.helpOpen) {
        lines += helpBlock(listHeight, width, style)
    } else {
        lines += listBlock(data, listHeight, width, style)
    }
    val previewPanelRow = if (previewBlock.isEmpty()) null else lines.size + 1
    lines += previewBlock
    lines += footer

    val cursorCol = (3 + displayCells(visibleQuery)).coerceAtMost(width.coerceAtLeast(1))
    return PickFrame(
        lines = lines.take(height),
        cursorRow = 2,
        cursorCol = cursorCol,
        previewPanelRow = previewPanelRow,
    )
}

/** Sum of display cells, iterating by code point. */
internal fun displayCells(text: String): Int {
    var cells = 0
    var i = 0
    while (i < text.length) {
        val codepoint = codepointAt(text, i)
        cells += approxCellWidth(codepoint)
        i += if (codepoint > 0xFFFF) 2 else 1
    }
    return cells
}

/**
 * Longest SUFFIX of [text] fitting in [maxCells] — the input-field
 * counterpart of [truncateToCellWidth]. Never splits a surrogate pair.
 */
internal fun tailToCellWidth(
    text: String,
    maxCells: Int,
): String {
    var start = 0
    var current = text
    while (displayCells(current) > maxCells && start < text.length) {
        start += if (codepointAt(text, start) > 0xFFFF) 2 else 1
        current = text.substring(start)
    }
    return current
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

/** Downgrade-aware styling: NONE renders plain text, 16/256 skip the gradient. */
internal class StylePalette(
    private val level: AnsiLevel,
) {
    val accent: TextStyle = TextColors.cyan
    val dim: TextStyle = TextStyles.dim.style
    val highlight: TextStyle = TextColors.cyan + TextStyles.bold
    val selectedStyle: TextStyle = TextStyles.inverse.style
    val flashStyle: TextStyle = TextColors.black + TextColors.green.bg

    fun apply(
        style: TextStyle,
        text: String,
    ): String = if (level == AnsiLevel.NONE) text else style(text)

    fun brand(): String {
        if (level != AnsiLevel.TRUECOLOR) return apply(TextColors.cyan + TextStyles.bold, BRAND_TEXT)
        val chars = BRAND_TEXT.toCharArray()
        val last = (chars.size - 1).coerceAtLeast(1)
        return buildString {
            chars.forEachIndexed { i, ch ->
                append(gradientColor(i, last)(ch.toString()))
            }
        }
    }

    private fun gradientColor(
        index: Int,
        last: Int,
    ): TextStyle {
        val t = index.toFloat() / last
        val r = lerpChannel(BRAND_START shr 16, BRAND_END shr 16, t)
        val g = lerpChannel(BRAND_START shr 8, BRAND_END shr 8, t)
        val b = lerpChannel(BRAND_START, BRAND_END, t)
        return TextColors.rgb(r / 255f, g / 255f, b / 255f) + TextStyles.bold
    }

    private fun lerpChannel(
        from: Int,
        to: Int,
        t: Float,
    ): Int = ((from and 0xFF) + (((to and 0xFF) - (from and 0xFF)) * t)).toInt().coerceIn(0, 255)
}

/**
 * Header segments are appended under a shared cell budget so a narrow
 * terminal truncates the tail instead of overflowing the line; the brand
 * loses its gradient when it cannot fit whole.
 */
private fun headerLine(
    data: PickFrameData,
    width: Int,
    style: StylePalette,
): String {
    val counts = "${data.rows.size} / ${data.total}"
    val badges =
        buildList {
            if (data.filters.types.isNotEmpty()) add("[${data.filters.types.joinToString("+")}]")
            // Tag names are user data and could carry control chars
            data.filters.tag?.let { add(sanitizeTerminalText("[#$it]")) }
            add(if (data.filters.sortNewest) "↓new" else "↑old")
        }.joinToString(" ")
    val spinner = if (data.searching) " ${SPINNER_FRAMES[data.spinnerFrame % SPINNER_FRAMES.size]}" else ""

    var remaining = width

    fun fit(text: String): String {
        val fitted = truncateToCellWidth(text, remaining.coerceAtLeast(0))
        remaining -= displayCells(fitted)
        return fitted
    }
    val brandFit = fit(BRAND_TEXT)
    val brandStyled =
        if (brandFit == BRAND_TEXT) style.brand() else style.apply(style.accent + TextStyles.bold, brandFit)
    return brandStyled +
        style.apply(style.dim, fit("  $counts")) +
        style.apply(style.accent, fit("  $badges")) +
        style.apply(style.accent, fit(spinner))
}

/**
 * The ←/→ selector bar: every candidate laid out horizontally with the
 * active one inverted. Selection applies live, so the bar is feedback, not
 * a pending choice.
 */
private fun filterBarLine(
    kind: FilterBarKind,
    data: PickFrameData,
    width: Int,
    style: StylePalette,
): String {
    val (label, candidates, activeIndex) =
        when (kind) {
            FilterBarKind.TYPE -> {
                // A foreign stage (initial --type multi-set) highlights NOTHING
                // rather than falsely lighting "all"
                val index = TYPE_CYCLE.indexOfFirst { it == data.filters.types }
                Triple("type", TYPE_CYCLE.map { if (it.isEmpty()) "all" else it.joinToString("+") }, index)
            }
            FilterBarKind.TAG -> {
                if (data.tagNames.isEmpty()) {
                    return style.apply(style.dim, truncateToCellWidth("  tag ▸ (no tags yet)", width))
                }
                val names = listOf("(none)") + data.tagNames.map { sanitizeTerminalText(it) }
                val index =
                    if (data.filters.tag == null) {
                        0
                    } else {
                        val tagIndex =
                            data.tagNames.indexOfFirst { it.equals(data.filters.tag, ignoreCase = true) }
                        if (tagIndex < 0) -1 else tagIndex + 1
                    }
                Triple("tag", names, index)
            }
        }
    var remaining = width

    fun fit(text: String): String {
        val fitted = truncateToCellWidth(text, remaining.coerceAtLeast(0))
        remaining -= displayCells(fitted)
        return fitted
    }
    return buildString {
        append(style.apply(style.accent, fit("  $label ▸ ")))
        candidates.forEachIndexed { i, candidate ->
            val plain = fit(" $candidate ")
            if (plain.isEmpty()) return@forEachIndexed
            append(if (i == activeIndex) style.apply(style.selectedStyle, plain) else style.apply(style.dim, plain))
        }
        append(style.apply(style.dim, fit("  ←/→ choose · esc close")))
    }
}

private fun listBlock(
    data: PickFrameData,
    listHeight: Int,
    width: Int,
    style: StylePalette,
): List<String> {
    if (data.rows.isEmpty()) {
        val message = if (data.query.isEmpty()) "No pastes." else "No matches — esc clears the query."
        return List(listHeight) { if (it == 0) style.apply(style.dim, "  $message") else "" }
    }
    val first = scrollOffset(data.selected, data.rows.size, listHeight)
    return (0 until listHeight).map { i ->
        val index = first + i
        val row = data.rows.getOrNull(index) ?: return@map ""
        renderRow(
            row,
            selected = index == data.selected,
            flash = data.flashSelected && index == data.selected,
            width,
            style,
        )
    }
}

/** Deterministic centered-ish scrolling keeps the renderer stateless. */
internal fun scrollOffset(
    selected: Int,
    rowCount: Int,
    listHeight: Int,
): Int {
    if (rowCount <= listHeight) return 0
    val centered = selected - listHeight / 2
    return centered.coerceIn(0, rowCount - listHeight)
}

/** Below this width the time and size columns give way to the preview. */
internal const val COMPACT_WIDTH_THRESHOLD = 60

/** One row's column strings, computed once for both render treatments. */
private data class RowCells(
    val fav: String,
    val id: String,
    val type: String,
    val origin: String,
    val time: String,
    val size: String,
    val compact: Boolean,
    val preview: String,
)

private fun rowCells(
    row: PickRow,
    width: Int,
): RowCells {
    val item = row.item
    val compact = width < COMPACT_WIDTH_THRESHOLD
    val fav = if (item.tagged) "*" else " "
    val id = item.id.toString().padStart(8)
    val type = item.typeName.padEnd(6)
    val origin = if (item.remote) "R" else "L"
    val time = if (compact) "" else formatRelativeTime(item.createTime).padEnd(8)
    val size = if (compact) "" else formatSize(item.size).padEnd(6)
    // Prefix cells from the actual strings (all ASCII, so chars == cells):
    // padded columns can outgrow their pad width (e.g. "696mo ago")
    val prefixCells =
        1 + fav.length + 1 + id.length + 1 + type.length + 1 +
            (if (compact) 0 else origin.length + 1 + time.length + 1 + size.length + 1)
    val previewBudget = (width - prefixCells).coerceAtLeast(1)
    val preview = truncateToCellWidth(row.displayText, previewBudget)
    return RowCells(fav, id, type, origin, time, size, compact, preview)
}

private fun renderRow(
    row: PickRow,
    selected: Boolean,
    flash: Boolean,
    width: Int,
    style: StylePalette,
): String {
    val cells = rowCells(row, width)
    return if (selected) {
        selectedRowLine(cells, flash, style)
    } else {
        plainRowLine(row, cells, style)
    }
}

private fun selectedRowLine(
    cells: RowCells,
    flash: Boolean,
    style: StylePalette,
): String {
    // The selected row keeps a single strong treatment (accent bar +
    // inverse); inner colors are dropped so the inversion stays readable
    val columns =
        with(cells) {
            if (compact) "$fav $id $type" else "$fav $id $type $origin $time $size"
        }
    val rowStyle = if (flash) style.flashStyle else style.selectedStyle
    return style.apply(style.accent, "▌") + style.apply(rowStyle, "$columns ${cells.preview}")
}

private fun plainRowLine(
    row: PickRow,
    cells: RowCells,
    style: StylePalette,
): String {
    val item = row.item
    val highlighted = highlightMatches(cells.preview, row.match.positions.toSet(), style)
    val typeColored =
        if (item.typeName == "color") {
            colorSwatch(row.displayText, style) ?: style.apply(typeStyle(item.typeName), cells.type)
        } else {
            style.apply(typeStyle(item.typeName), cells.type)
        }
    val tail =
        if (cells.compact) {
            ""
        } else {
            cells.origin + " " + style.apply(style.dim, cells.time) + " " + style.apply(style.dim, cells.size) + " "
        }
    return " ${cells.fav} ${cells.id} $typeColored $tail$highlighted"
}

/**
 * Styles the matched characters as contiguous RUNS advancing by code point:
 * styling each UTF-16 char separately would split a surrogate pair with ANSI
 * codes and render an emoji hit as two replacement chars.
 */
internal fun highlightMatches(
    text: String,
    positions: Set<Int>,
    style: StylePalette,
): String {
    if (positions.isEmpty()) return text
    return buildString {
        var runStart = 0
        var runHighlighted = false

        fun flush(end: Int) {
            if (end > runStart) {
                val segment = text.substring(runStart, end)
                append(if (runHighlighted) style.apply(style.highlight, segment) else segment)
            }
        }
        var i = 0
        while (i < text.length) {
            val pairLength =
                if (text[i].isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate()) 2 else 1
            val highlighted = i in positions || (pairLength == 2 && (i + 1) in positions)
            if (highlighted != runHighlighted) {
                flush(i)
                runStart = i
                runHighlighted = highlighted
            }
            i += pairLength
        }
        flush(text.length)
    }
}

/**
 * A color paste's row shows its own color as a swatch — the preview text is
 * the color value itself (e.g. "#1E80FF").
 */
internal fun colorSwatch(
    previewText: String,
    style: StylePalette,
): String? {
    val hex = Regex("#?([0-9a-fA-F]{6})\\b").find(previewText)?.groupValues?.get(1) ?: return null
    val value = hex.toInt(16)
    val color =
        TextColors.rgb(
            (value shr 16 and 0xFF) / 255f,
            (value shr 8 and 0xFF) / 255f,
            (value and 0xFF) / 255f,
        )
    return style.apply(color, "██") + "    "
}

private fun previewBlock(
    data: PickFrameData,
    width: Int,
    style: StylePalette,
): List<String> {
    val title = "─ preview "
    val separator = title + "─".repeat((width - title.length).coerceAtLeast(0))
    val lines = ArrayList<String>()
    lines += style.apply(style.dim, truncateToCellWidth(separator, width))
    // Always exactly PREVIEW_PANEL_LINES content lines: a variable-height
    // panel would make the layout jump per selection and desync the list
    // height from pickListHeight
    for (i in 0 until PREVIEW_PANEL_LINES) {
        val line = data.previewLines.getOrNull(i) ?: ""
        // Detail content, source names, and file paths are pasteboard data
        val truncated = truncateToCellWidth(sanitizeTerminalText(line), (width - 2).coerceAtLeast(1))
        lines += "  " +
            if (i == 0 && data.previewLink != null) {
                hyperlink(data.previewLink, truncated)
            } else {
                truncated
            }
    }
    return lines
}

internal const val PREVIEW_PANEL_LINES = 6

/** Header + query + at least 3 list rows + separator + panel + footer. */
internal const val MIN_HEIGHT_FOR_PREVIEW_PANEL = PREVIEW_PANEL_LINES + 1 + 6

/**
 * On short terminals the panel would push the footer (and any inline image)
 * past the bottom; the list keeps the space instead.
 */
internal fun previewPanelVisible(
    height: Int,
    previewOpen: Boolean,
    helpOpen: Boolean,
): Boolean = previewOpen && !helpOpen && height >= MIN_HEIGHT_FOR_PREVIEW_PANEL

/**
 * The single source of truth for how many list rows a frame shows — the
 * event loop's page size MUST match what the renderer lays out (header +
 * query + optional filter bar + list + optional preview block + footer).
 */
internal fun pickListHeight(
    height: Int,
    previewOpen: Boolean,
    helpOpen: Boolean,
    filterBarOpen: Boolean,
): Int {
    val previewBlock = if (previewPanelVisible(height, previewOpen, helpOpen)) PREVIEW_PANEL_LINES + 1 else 0
    val filterBar = if (filterBarOpen) 1 else 0
    return (height - 2 - filterBar - previewBlock - 1).coerceAtLeast(1)
}

/**
 * OSC 8 hyperlink; terminals without support render the plain text. A URL
 * containing control characters (ESC, BEL, C1) could terminate the OSC early
 * and inject arbitrary sequences, so such URLs are not linked at all —
 * stripping could silently change where the link points.
 */
internal fun hyperlink(
    url: String,
    text: String,
): String {
    if (url.any { it.isISOControl() || it.code in 0x80..0x9F }) return text
    val esc = 27.toChar()
    return "$esc]8;;$url$esc\\$text$esc]8;;$esc\\"
}

private fun helpBlock(
    listHeight: Int,
    width: Int,
    style: StylePalette,
): List<String> {
    val entries =
        listOf(
            "enter" to "copy the selected paste and exit",
            "ctrl-e" to "edit the selected paste in \$EDITOR",
            "ctrl-t" to "open the type selector (←/→ choose)",
            "ctrl-g" to "open the tag selector (←/→ choose)",
            "ctrl-s" to "toggle newest/oldest order",
            "tab" to "toggle the preview panel",
            "up/down, ctrl-p/n" to "move the selection",
            "pgup/pgdn, home/end" to "jump through the list",
            "esc" to "close a selector or help, clear the query, or cancel",
            "?" to "toggle this help (when the query is empty)",
        )
    val lines =
        listOf("", style.apply(style.accent + TextStyles.bold, "  Keys")) +
            entries.map { (key, description) ->
                "  " + style.apply(style.highlight, key.padEnd(20)) +
                    style.apply(style.dim, truncateToCellWidth(description, (width - 24).coerceAtLeast(1)))
            }
    return (0 until listHeight).map { lines.getOrNull(it) ?: "" }
}

private fun footerLine(
    width: Int,
    style: StylePalette,
): String {
    val hints = "↵ copy   ^e edit   ^t type   ^g tag   ^s sort   ⇥ preview   ? help   esc quit"
    return style.apply(style.dim, truncateToCellWidth(hints, width))
}
