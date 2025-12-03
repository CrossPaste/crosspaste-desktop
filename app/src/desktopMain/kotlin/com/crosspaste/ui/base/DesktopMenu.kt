package com.crosspaste.ui.base

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalContextMenuRepresentation
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.text.LocalTextContextMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import com.crosspaste.ui.theme.AppUISize.giant
import com.crosspaste.ui.theme.AppUISize.gigantic
import com.crosspaste.ui.theme.AppUISize.large2X
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny2X
import com.crosspaste.ui.theme.CrossPasteTheme.Theme
import com.dzirbel.contextmenu.ContextMenuColors
import com.dzirbel.contextmenu.ContextMenuMeasurements
import com.dzirbel.contextmenu.MaterialContextMenuRepresentation
import com.dzirbel.contextmenu.MaterialTextContextMenu

object DesktopMenu {

    @OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
    @Composable
    fun ProvidesMenuContext(content: @Composable () -> Unit) {
        Theme {
            CompositionLocalProvider(
                LocalContextMenuRepresentation provides
                    MaterialContextMenuRepresentation(
                        measurements =
                            ContextMenuMeasurements(
                                minWidth = giant,
                                maxWidth = gigantic,
                                itemMinHeight = large2X,
                                itemPadding = PaddingValues(medium, tiny2X),
                            ),
                        colors =
                            ContextMenuColors(
                                surface = MaterialTheme.colorScheme.surface,
                                text = MaterialTheme.colorScheme.onSurface,
                            ),
                    ),
                LocalTextContextMenu provides MaterialTextContextMenu,
            ) {
                content()
            }
        }
    }
}
