package com.crosspaste.ui.base

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties

/**
 * Makes the next focus target in the chain (here: a clickable's) unfocusable, so clicking it
 * leaves focus where it was.
 *
 * On desktop, Compose focuses a clickable on mouse press, and a focused clickable consumes
 * Enter/Space as its own click. Keyboard-driven windows (the search window: Enter pastes,
 * typing searches) use this on their controls so focus stays on the input or list.
 *
 * Trade-off: the control also drops out of Tab traversal. Place this before the clickable.
 * A focus target in between (e.g. a text field inside a chip) is not affected.
 */
fun Modifier.nonFocusable(): Modifier = focusProperties { canFocus = false }
