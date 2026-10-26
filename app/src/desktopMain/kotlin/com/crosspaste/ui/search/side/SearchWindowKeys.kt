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
    HIGHLIGHT_PREV_TAG,
    HIGHLIGHT_NEXT_TAG,
    ACTIVATE_TAG,
    FOCUS_SEARCH_INPUT,
    FOCUS_TAG_BAR,
    FOCUS_PASTE_LIST,
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
 * Every search window shortcut, keyed by phase and the zone that holds focus.
 * [isSearchCaretAtEnd] lets Right leave the search input only once there is nothing left to move past.
 */
fun resolveSearchWindowKey(
    phase: KeyPhase,
    zone: FocusedElement,
    stroke: SearchKeyStroke,
    isSearchCaretAtEnd: Boolean = true,
): SearchWindowKeyAction? =
    when (phase) {
        KeyPhase.PREVIEW -> resolveTabKey(zone, stroke) ?: resolvePreviewKey(zone, stroke, isSearchCaretAtEnd)
        KeyPhase.BUBBLE -> resolveBubbleKey(zone, stroke)
    }

// Tab cycles search input -> tag bar -> paste list, Shift+Tab goes back
private val TAB_ORDER =
    listOf(
        FocusedElement.SEARCH_INPUT to SearchWindowKeyAction.FOCUS_SEARCH_INPUT,
        FocusedElement.TAG_BAR to SearchWindowKeyAction.FOCUS_TAG_BAR,
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
    zone: FocusedElement,
    stroke: SearchKeyStroke,
    isSearchCaretAtEnd: Boolean,
): SearchWindowKeyAction? =
    when (zone) {
        FocusedElement.SEARCH_INPUT ->
            when {
                stroke.key == Key.DirectionDown -> SearchWindowKeyAction.FOCUS_PASTE_LIST
                stroke.key == Key.DirectionRight && stroke.hasNoModifier && isSearchCaretAtEnd ->
                    SearchWindowKeyAction.FOCUS_TAG_BAR
                else -> null
            }
        FocusedElement.TAG_BAR ->
            when (stroke.key) {
                Key.DirectionLeft -> SearchWindowKeyAction.HIGHLIGHT_PREV_TAG
                Key.DirectionRight -> SearchWindowKeyAction.HIGHLIGHT_NEXT_TAG
                Key.Enter, Key.NumPadEnter, Key.Spacebar -> SearchWindowKeyAction.ACTIVATE_TAG
                Key.DirectionDown -> SearchWindowKeyAction.FOCUS_PASTE_LIST
                else -> null
            }
        else -> null
    }

private fun resolveBubbleKey(
    zone: FocusedElement,
    stroke: SearchKeyStroke,
): SearchWindowKeyAction? {
    // The tag bar owns its keys in the preview pass; nothing it declines should reach the list
    if (zone == FocusedElement.TAG_BAR) return null
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
