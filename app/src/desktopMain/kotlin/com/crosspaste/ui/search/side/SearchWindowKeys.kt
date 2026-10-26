package com.crosspaste.ui.search.side

import androidx.compose.ui.input.key.Key
import com.crosspaste.ui.model.FocusedElement

/**
 * When a search window shortcut is looked up. [PREVIEW] runs before the focused child sees the
 * key, so its entries win over the search text field's own caret keys; [BUBBLE] only sees keys
 * the focused child left unconsumed.
 */
enum class KeyPhase {
    PREVIEW,
    BUBBLE,
}

enum class SearchWindowKeyAction {
    PASTE,
    SELECT_PREV,
    SELECT_NEXT,
    OPEN_TYPE_MENU,
    HIGHLIGHT_PREV_TYPE,
    HIGHLIGHT_NEXT_TYPE,
    CONFIRM_TYPE,
    HIGHLIGHT_PREV_TAG,
    HIGHLIGHT_NEXT_TAG,
    ACTIVATE_TAG,
    OPEN_SETTINGS,
    FOCUS_SEARCH_INPUT,
    FOCUS_TYPE_FILTER,
    FOCUS_TAG_BAR,

    /** Enter the tag bar from its right end, on the add chip. */
    FOCUS_TAG_BAR_END,
    FOCUS_SETTINGS,
    FOCUS_PASTE_LIST,

    /** Swallow the key without doing anything. */
    CONSUME,
}

data class SearchKeyStroke(
    val key: Key,
    val isCtrlPressed: Boolean = false,
    val isShiftPressed: Boolean = false,
    val isAltPressed: Boolean = false,
    val isMetaPressed: Boolean = false,
) {
    val hasNoModifier: Boolean
        get() = !isCtrlPressed && !isShiftPressed && !isAltPressed && !isMetaPressed
}

/**
 * What the key map needs to know about the window besides the key itself.
 * [isSearchCaretAtEnd] lets Right leave the search input only once there is nothing left to move past.
 */
data class SearchKeyState(
    val zone: FocusedElement,
    val isSearchCaretAtEnd: Boolean = true,
    val isTypeMenuOpen: Boolean = false,
)

/** Every search window shortcut, keyed by phase and the zone that holds focus. */
fun resolveSearchWindowKey(
    phase: KeyPhase,
    state: SearchKeyState,
    stroke: SearchKeyStroke,
): SearchWindowKeyAction? =
    when (phase) {
        KeyPhase.PREVIEW -> resolveTabKey(state.zone, stroke) ?: resolvePreviewKey(state, stroke)
        KeyPhase.BUBBLE -> resolveBubbleKey(state.zone, stroke)
    }

// Tab walks the top bar left to right, then the list; Shift+Tab goes back
private val TAB_ORDER =
    listOf(
        FocusedElement.SEARCH_INPUT to SearchWindowKeyAction.FOCUS_SEARCH_INPUT,
        FocusedElement.TYPE_FILTER to SearchWindowKeyAction.FOCUS_TYPE_FILTER,
        FocusedElement.TAG_BAR to SearchWindowKeyAction.FOCUS_TAG_BAR,
        FocusedElement.SETTINGS to SearchWindowKeyAction.FOCUS_SETTINGS,
        FocusedElement.PASTE_LIST to SearchWindowKeyAction.FOCUS_PASTE_LIST,
    )

private fun resolveTabKey(
    zone: FocusedElement,
    stroke: SearchKeyStroke,
): SearchWindowKeyAction? {
    if (stroke.key != Key.Tab || stroke.isCtrlPressed || stroke.isAltPressed || stroke.isMetaPressed) return null
    val index = TAB_ORDER.indexOfFirst { it.first == zone }
    if (index == -1) return null
    val step = if (stroke.isShiftPressed) TAB_ORDER.size - 1 else 1
    return TAB_ORDER[(index + step) % TAB_ORDER.size].second
}

private fun resolvePreviewKey(
    state: SearchKeyState,
    stroke: SearchKeyStroke,
): SearchWindowKeyAction? =
    when (state.zone) {
        FocusedElement.SEARCH_INPUT ->
            when {
                stroke.key == Key.DirectionDown -> SearchWindowKeyAction.FOCUS_PASTE_LIST
                stroke.key == Key.DirectionRight && stroke.hasNoModifier && state.isSearchCaretAtEnd ->
                    SearchWindowKeyAction.FOCUS_TYPE_FILTER
                else -> null
            }
        // The type filter sits inside the search text field, whose key handler would otherwise
        // edit the query with keys bubbling up from it, so this zone keeps every key to itself
        FocusedElement.TYPE_FILTER ->
            if (state.isTypeMenuOpen) resolveTypeMenuKey(stroke) else resolveTypeFilterKey(stroke)
        FocusedElement.TAG_BAR ->
            when (stroke.key) {
                Key.DirectionLeft -> SearchWindowKeyAction.HIGHLIGHT_PREV_TAG
                Key.DirectionRight -> SearchWindowKeyAction.HIGHLIGHT_NEXT_TAG
                Key.Enter, Key.NumPadEnter, Key.Spacebar -> SearchWindowKeyAction.ACTIVATE_TAG
                Key.DirectionDown -> SearchWindowKeyAction.FOCUS_PASTE_LIST
                else -> null
            }
        FocusedElement.SETTINGS ->
            when (stroke.key) {
                Key.DirectionLeft -> SearchWindowKeyAction.FOCUS_TAG_BAR_END
                Key.Enter, Key.NumPadEnter, Key.Spacebar -> SearchWindowKeyAction.OPEN_SETTINGS
                Key.DirectionDown -> SearchWindowKeyAction.FOCUS_PASTE_LIST
                else -> null
            }
        else -> null
    }

private fun resolveTypeFilterKey(stroke: SearchKeyStroke): SearchWindowKeyAction =
    when (stroke.key) {
        Key.Enter, Key.NumPadEnter, Key.Spacebar -> SearchWindowKeyAction.OPEN_TYPE_MENU
        Key.DirectionLeft -> SearchWindowKeyAction.FOCUS_SEARCH_INPUT
        Key.DirectionRight -> SearchWindowKeyAction.FOCUS_TAG_BAR
        Key.DirectionDown -> SearchWindowKeyAction.FOCUS_PASTE_LIST
        else -> SearchWindowKeyAction.CONSUME
    }

private fun resolveTypeMenuKey(stroke: SearchKeyStroke): SearchWindowKeyAction =
    when (stroke.key) {
        Key.DirectionUp -> SearchWindowKeyAction.HIGHLIGHT_PREV_TYPE
        Key.DirectionDown -> SearchWindowKeyAction.HIGHLIGHT_NEXT_TYPE
        Key.Enter, Key.NumPadEnter, Key.Spacebar -> SearchWindowKeyAction.CONFIRM_TYPE
        else -> SearchWindowKeyAction.CONSUME
    }

// Zones that own Enter themselves; nothing they decline should reach the list shortcuts
private val SELF_CONTAINED_ZONES = setOf(FocusedElement.TYPE_FILTER, FocusedElement.TAG_BAR, FocusedElement.SETTINGS)

private fun resolveBubbleKey(
    zone: FocusedElement,
    stroke: SearchKeyStroke,
): SearchWindowKeyAction? {
    if (zone in SELF_CONTAINED_ZONES) return null
    return when (stroke.key) {
        Key.Enter -> SearchWindowKeyAction.PASTE
        Key.DirectionUp -> SearchWindowKeyAction.FOCUS_SEARCH_INPUT
        Key.DirectionLeft -> SearchWindowKeyAction.SELECT_PREV
        Key.DirectionRight -> SearchWindowKeyAction.SELECT_NEXT
        Key.N -> SearchWindowKeyAction.SELECT_NEXT.takeIf { stroke.isCtrlPressed }
        Key.P -> SearchWindowKeyAction.SELECT_PREV.takeIf { stroke.isCtrlPressed }
        else -> null
    }
}
