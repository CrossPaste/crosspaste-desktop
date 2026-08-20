package com.crosspaste.cli.commands.pick

import com.crosspaste.cli.commands.EDITABLE_PASTE_TYPES
import com.crosspaste.cli.commands.PasteSummaryDto
import com.crosspaste.cli.commands.SORT_NEWEST
import com.crosspaste.cli.commands.SORT_OLDEST
import com.crosspaste.cli.commands.collapsePreviewWhitespace
import com.github.ajalt.mordant.input.KeyboardEvent

/** Filter state mirroring the search window (and the unified history command). */
data class PickFilters(
    /** Active type filter; empty = all types. */
    val types: List<String>,
    val tag: String?,
    val sortNewest: Boolean = true,
) {
    val sortParam: String get() = if (sortNewest) SORT_NEWEST else SORT_OLDEST
}

/** Terminals at least this tall start with the preview panel open. */
internal const val PREVIEW_AUTO_OPEN_MIN_HEIGHT = 30

/** The type stages Ctrl-T cycles through (empty list = all types first). */
internal val TYPE_CYCLE: List<List<String>> =
    listOf(emptyList<String>()) +
        listOf("text", "link", "image", "file", "html", "rtf", "color").map { listOf(it) }

sealed interface PickAction {
    data class Insert(
        val text: String,
    ) : PickAction

    data object Backspace : PickAction

    data object MoveUp : PickAction

    data object MoveDown : PickAction

    data object MoveLeft : PickAction

    data object MoveRight : PickAction

    data object PageUp : PickAction

    data object PageDown : PickAction

    data object Home : PickAction

    data object End : PickAction

    data object Accept : PickAction

    data object EditSelected : PickAction

    data object ToggleTypeBar : PickAction

    data object ToggleTagBar : PickAction

    data object ToggleSort : PickAction

    data object TogglePreview : PickAction

    data object ToggleHelp : PickAction

    data object ClearOrCancel : PickAction

    data object Cancel : PickAction
}

/**
 * Pure key-to-action mapping. `?` opens help only while the query is empty —
 * once the user is typing, it is a literal search character.
 */
internal fun toPickAction(
    event: KeyboardEvent,
    queryEmpty: Boolean,
): PickAction? {
    if (event.ctrl) {
        return when (event.key.lowercase()) {
            "p" -> PickAction.MoveUp
            "n" -> PickAction.MoveDown
            "e" -> PickAction.EditSelected
            "t" -> PickAction.ToggleTypeBar
            "g" -> PickAction.ToggleTagBar
            "s" -> PickAction.ToggleSort
            "c" -> PickAction.Cancel
            else -> null
        }
    }
    if (event.alt) return null
    return when (event.key) {
        "ArrowUp" -> PickAction.MoveUp
        "ArrowDown" -> PickAction.MoveDown
        "ArrowLeft" -> PickAction.MoveLeft
        "ArrowRight" -> PickAction.MoveRight
        "PageUp" -> PickAction.PageUp
        "PageDown" -> PickAction.PageDown
        "Home" -> PickAction.Home
        "End" -> PickAction.End
        "Enter" -> PickAction.Accept
        "Tab" -> PickAction.TogglePreview
        "Escape" -> PickAction.ClearOrCancel
        "Backspace" -> PickAction.Backspace
        "?" -> if (queryEmpty) PickAction.ToggleHelp else PickAction.Insert("?")
        else -> event.takeIf { isPrintableKey(it.key) }?.let { PickAction.Insert(it.key) }
    }
}

/**
 * Printable input is a single char (not a control char) or a surrogate pair
 * (emoji); everything longer is a named special key ("ArrowLeft", "F1", ...).
 */
private fun isPrintableKey(key: String): Boolean =
    when (key.length) {
        1 -> !key[0].isISOControl()
        2 -> key[0].isHighSurrogate() && key[1].isLowSurrogate()
        else -> false
    }

/** One display row: the item, its collapsed preview text, and the match. */
data class PickRow(
    val item: PasteSummaryDto,
    val displayText: String,
    val match: FuzzyMatch,
)

/** What the event loop must do after an action was applied. */
sealed interface PickEffect {
    data object None : PickEffect

    data object Redraw : PickEffect

    /** Query changed: repaint now, re-query the server after the debounce. */
    data object RefetchDebounced : PickEffect

    /** Filters changed: re-query the server immediately. */
    data object RefetchNow : PickEffect

    data class Accept(
        val item: PasteSummaryDto,
    ) : PickEffect

    data class Edit(
        val item: PasteSummaryDto,
    ) : PickEffect

    data object Cancel : PickEffect
}

/**
 * The picker's mutable state plus the pure action reducer. No I/O here —
 * fetching lives in the event loop, which feeds results back via [setWindow]
 * and [setSearchExtra].
 */
class PickState(
    query: String,
    filters: PickFilters,
    val tagNames: List<String>,
) {
    var query: String = query
        private set
    var filters: PickFilters = filters
        private set
    var previewOpen: Boolean = false
    var helpOpen: Boolean = false
        private set

    /** The open filter selector bar (←/→ choose live), or null when closed. */
    var filterBar: FilterBarKind? = null
        private set
    var searching: Boolean = false
    var total: Long = 0
        private set
    var rows: List<PickRow> = emptyList()
        private set
    var selected: Int = 0
        private set

    /** The plain window for the current filters (no keyword). */
    private var window: List<PasteSummaryDto> = emptyList()

    /** The latest server keyword-search results, merged in by id. */
    private var searchExtra: List<PasteSummaryDto> = emptyList()

    val selectedItem: PasteSummaryDto? get() = rows.getOrNull(selected)?.item

    fun setWindow(
        items: List<PasteSummaryDto>,
        total: Long,
    ) {
        window = items
        this.total = total
        refilter()
    }

    fun setSearchExtra(items: List<PasteSummaryDto>) {
        searchExtra = items
        refilter()
    }

    /**
     * Filters changed: everything on screen belongs to the old filter set, so
     * drop it before the fresh window arrives — a stale row must not be
     * copyable under the new filters.
     */
    fun clearForRefetch() {
        window = emptyList()
        searchExtra = emptyList()
        refilter()
    }

    /**
     * Server results are authoritative only for the query they answered;
     * once the query changes they must not linger (they bypass the local
     * fuzzy filter, so a stale set would show plainly wrong rows).
     */
    private fun onQueryEdited() {
        searchExtra = emptyList()
        refilter()
    }

    fun handle(
        action: PickAction,
        pageSize: Int,
    ): PickEffect =
        when (action) {
            is PickAction.Insert -> {
                // Typing returns focus to the query; the bar closes quietly
                filterBar = null
                query += action.text
                onQueryEdited()
                PickEffect.RefetchDebounced
            }
            PickAction.Backspace ->
                if (query.isEmpty()) {
                    PickEffect.None
                } else {
                    query = dropLastCodepoint(query)
                    onQueryEdited()
                    PickEffect.RefetchDebounced
                }
            PickAction.MoveUp -> moveSelection(-1)
            PickAction.MoveDown -> moveSelection(1)
            PickAction.MoveLeft -> shiftFilterBar(-1)
            PickAction.MoveRight -> shiftFilterBar(1)
            PickAction.PageUp -> moveSelection(-pageSize.coerceAtLeast(1))
            PickAction.PageDown -> moveSelection(pageSize.coerceAtLeast(1))
            PickAction.Home -> moveSelectionTo(0)
            PickAction.End -> moveSelectionTo(rows.lastIndex)
            PickAction.Accept ->
                if (filterBar != null) {
                    // Enter confirms the selector; the next Enter copies
                    filterBar = null
                    PickEffect.Redraw
                } else {
                    selectedItem?.let { PickEffect.Accept(it) } ?: PickEffect.None
                }
            PickAction.EditSelected ->
                selectedItem
                    ?.takeIf { it.typeName in EDITABLE_PASTE_TYPES }
                    ?.let { PickEffect.Edit(it) }
                    ?: PickEffect.None
            PickAction.ToggleTypeBar -> toggleFilterBar(FilterBarKind.TYPE)
            PickAction.ToggleTagBar -> toggleFilterBar(FilterBarKind.TAG)
            PickAction.ToggleSort -> {
                filters = filters.copy(sortNewest = !filters.sortNewest)
                PickEffect.RefetchNow
            }
            PickAction.TogglePreview -> {
                previewOpen = !previewOpen
                PickEffect.Redraw
            }
            PickAction.ToggleHelp -> {
                helpOpen = !helpOpen
                PickEffect.Redraw
            }
            PickAction.ClearOrCancel ->
                when {
                    filterBar != null -> {
                        filterBar = null
                        PickEffect.Redraw
                    }
                    helpOpen -> {
                        helpOpen = false
                        PickEffect.Redraw
                    }
                    query.isNotEmpty() -> {
                        query = ""
                        onQueryEdited()
                        PickEffect.RefetchDebounced
                    }
                    else -> PickEffect.Cancel
                }
            PickAction.Cancel -> PickEffect.Cancel
        }

    private fun toggleFilterBar(kind: FilterBarKind): PickEffect {
        filterBar = if (filterBar == kind) null else kind
        return PickEffect.Redraw
    }

    /** ←/→ in an open bar apply the neighboring filter value LIVE. */
    private fun shiftFilterBar(delta: Int): PickEffect =
        when (filterBar) {
            null -> PickEffect.None
            FilterBarKind.TYPE -> {
                filters = filters.copy(types = shiftTypeStage(filters.types, delta))
                PickEffect.RefetchNow
            }
            FilterBarKind.TAG ->
                if (tagNames.isEmpty()) {
                    PickEffect.None
                } else {
                    filters = filters.copy(tag = shiftTag(filters.tag, tagNames, delta))
                    PickEffect.RefetchNow
                }
        }

    private fun moveSelection(delta: Int): PickEffect = moveSelectionTo(selected + delta)

    private fun moveSelectionTo(index: Int): PickEffect {
        val clamped = index.coerceIn(0, (rows.size - 1).coerceAtLeast(0))
        if (clamped == selected) return PickEffect.None
        selected = clamped
        return PickEffect.Redraw
    }

    /**
     * Rebuild the visible rows. The window is fuzzy-filtered against the
     * collapsed preview for instant feedback; server keyword results are
     * AUTHORITATIVE — the server also matches source and normalizes
     * punctuation, so an item it returned stays visible even when the local
     * matcher cannot re-derive the hit (it just renders unhighlighted).
     * With a query, rows re-rank by local score (authoritative-but-unmatched
     * rows sink to the end); without one the server order is kept as-is.
     *
     * The selection follows the paste ID, not the row index: rows shift when
     * results insert or re-rank (or an edit puts a new paste on top), and a
     * bare index would silently land Enter on a different paste. Only when
     * the selected paste vanished does the nearby index take over.
     */
    private fun refilter() {
        val selectedId = rows.getOrNull(selected)?.item?.id
        val serverMatchedIds = searchExtra.map { it.id }.toSet()
        val merged = (window + searchExtra).distinctBy { it.id }
        val matched =
            merged.mapNotNull { item ->
                val displayText = collapsePreviewWhitespace(item.preview)
                val match = fuzzyMatch(query, displayText)
                when {
                    match != null -> PickRow(item, displayText, match)
                    item.id in serverMatchedIds -> PickRow(item, displayText, FuzzyMatch(0, emptyList()))
                    else -> null
                }
            }
        rows =
            if (query.isBlank()) {
                matched
            } else {
                matched.sortedByDescending { it.match.score }
            }
        val restored = selectedId?.let { id -> rows.indexOfFirst { it.item.id == id } } ?: -1
        selected =
            if (restored >= 0) restored else selected.coerceIn(0, (rows.size - 1).coerceAtLeast(0))
    }
}

enum class FilterBarKind { TYPE, TAG }

internal fun shiftTypeStage(
    current: List<String>,
    delta: Int,
): List<String> {
    val index = TYPE_CYCLE.indexOfFirst { it == current }
    // An initial multi-type filter from --type options is not a bar stage;
    // the first step lands on "all types"
    if (index < 0) return TYPE_CYCLE.first()
    return TYPE_CYCLE[(index + delta).mod(TYPE_CYCLE.size)]
}

internal fun shiftTag(
    current: String?,
    tagNames: List<String>,
    delta: Int,
): String? {
    // Candidate row: (no tag) followed by every tag, wrapping at both ends
    val candidates = listOf<String?>(null) + tagNames
    val index = candidates.indexOf(current).coerceAtLeast(0)
    return candidates[(index + delta).mod(candidates.size)]
}

/** Drops the last user-visible char, keeping surrogate pairs intact. */
internal fun dropLastCodepoint(text: String): String {
    if (text.isEmpty()) return text
    val cut =
        if (text.length >= 2 && text[text.length - 2].isHighSurrogate() && text[text.length - 1].isLowSurrogate()) {
            2
        } else {
            1
        }
    return text.dropLast(cut)
}
