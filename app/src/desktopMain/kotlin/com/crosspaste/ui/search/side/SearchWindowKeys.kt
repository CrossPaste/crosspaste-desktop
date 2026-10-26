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
    FOCUS_SEARCH_INPUT,
    FOCUS_PASTE_LIST,
}

/** Every search window shortcut, keyed by phase and the zone that holds focus. */
fun resolveSearchWindowKey(
    phase: KeyPhase,
    zone: FocusedElement,
    key: Key,
    isCtrlPressed: Boolean,
): SearchWindowKeyAction? =
    when (phase) {
        KeyPhase.PREVIEW -> resolvePreviewKey(zone, key)
        KeyPhase.BUBBLE -> resolveBubbleKey(key, isCtrlPressed)
    }

private fun resolvePreviewKey(
    zone: FocusedElement,
    key: Key,
): SearchWindowKeyAction? =
    when {
        zone == FocusedElement.SEARCH_INPUT && key == Key.DirectionDown ->
            SearchWindowKeyAction.FOCUS_PASTE_LIST
        else -> null
    }

private fun resolveBubbleKey(
    key: Key,
    isCtrlPressed: Boolean,
): SearchWindowKeyAction? =
    when (key) {
        Key.Enter -> SearchWindowKeyAction.PASTE
        Key.DirectionUp -> SearchWindowKeyAction.FOCUS_SEARCH_INPUT
        Key.DirectionLeft -> SearchWindowKeyAction.SELECT_PREV
        Key.DirectionRight -> SearchWindowKeyAction.SELECT_NEXT
        Key.N -> SearchWindowKeyAction.SELECT_NEXT.takeIf { isCtrlPressed }
        Key.P -> SearchWindowKeyAction.SELECT_PREV.takeIf { isCtrlPressed }
        else -> null
    }
