package com.crosspaste.ui.search

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties

/**
 * Keeps a clickable control in the search window from taking keyboard focus when clicked.
 *
 * On desktop, Compose focuses a clickable on mouse press, and a focused clickable consumes
 * Enter/Space as its own click. The search window is keyboard-driven (Enter pastes, typing
 * searches), so focus must stay on the search input or the paste list. Place this before
 * the clickable in the modifier chain.
 */
fun Modifier.skipKeyboardFocus(): Modifier = focusProperties { canFocus = false }
