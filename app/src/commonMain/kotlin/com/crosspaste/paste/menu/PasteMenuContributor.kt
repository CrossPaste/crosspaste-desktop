package com.crosspaste.paste.menu

import com.crosspaste.paste.PasteData

/**
 * A context-menu action contributed for a paste item. Kept UI-agnostic so each
 * platform renders it with its own menu components.
 */
data class PasteMenuAction(
    val label: String,
    val onClick: suspend () -> Unit,
)

/**
 * Adds actions to a paste item's context menu, placed after the built-in
 * type-specific actions. Called on the UI thread each time a menu opens, so it
 * must not block (no disk or network I/O).
 */
fun interface PasteMenuContributor {
    fun contribute(pasteData: PasteData): List<PasteMenuAction>
}
