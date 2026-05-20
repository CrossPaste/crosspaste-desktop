package com.crosspaste.ui.contextmenu

import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ContextMenuState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.TextContextMenu
import androidx.compose.foundation.text.TextContextMenuArea
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLocalization

/**
 * 1.11-compatible replacement for `dzirbel`'s `MaterialTextContextMenu`. Routes the cut / copy /
 * paste / select-all actions produced by Compose's text selection layer through our own
 * [MaterialContextMenuRepresentation] so right-click on [SelectionContainer] and `BasicTextField`
 * shows the same styled popup as the rest of the app.
 *
 * Only [TextContextMenu.Action.enabled] items are emitted — keeping the menu small instead of
 * showing greyed-out items (matches JetBrains-style menus and the simpler dzirbel UX).
 */
@OptIn(ExperimentalFoundationApi::class)
object MaterialTextContextMenu : TextContextMenu {
    @Composable
    override fun Area(
        textManager: TextContextMenu.TextManager,
        state: ContextMenuState,
        content: @Composable () -> Unit,
    ) {
        val localization = LocalLocalization.current
        val items: () -> List<ContextMenuItem> = {
            listOfNotNull(
                textManager.cut.toMenuItem(localization.cut),
                textManager.copy.toMenuItem(localization.copy),
                textManager.paste.toMenuItem(localization.paste),
                textManager.selectAll.toMenuItem(localization.selectAll),
            )
        }
        TextContextMenuArea(
            textManager = textManager,
            items = items,
            state = state,
            content = content,
        )
    }

    @OptIn(ExperimentalFoundationApi::class)
    private fun TextContextMenu.Action?.toMenuItem(label: String): ContextMenuItem? {
        val action = this ?: return null
        if (!action.enabled) return null
        return ContextMenuItem(label = label, onClick = action.execute)
    }
}
