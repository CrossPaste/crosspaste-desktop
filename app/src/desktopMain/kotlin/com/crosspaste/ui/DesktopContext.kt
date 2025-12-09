package com.crosspaste.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalContextMenuRepresentation
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.text.LocalTextContextMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import com.crosspaste.app.WindowInfo
import com.crosspaste.ui.theme.AppUISize
import com.crosspaste.ui.theme.CrossPasteTheme.Theme
import com.dzirbel.contextmenu.ContextMenuColors
import com.dzirbel.contextmenu.ContextMenuMeasurements
import com.dzirbel.contextmenu.MaterialContextMenuRepresentation
import com.dzirbel.contextmenu.MaterialTextContextMenu

object DesktopContext {

    @OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
    @Composable
    private fun BaseContext(content: @Composable () -> Unit) {
        Theme {
            CompositionLocalProvider(
                LocalContextMenuRepresentation provides
                    MaterialContextMenuRepresentation(
                        measurements =
                            ContextMenuMeasurements(
                                minWidth = AppUISize.giant,
                                maxWidth = AppUISize.gigantic,
                                itemMinHeight = AppUISize.large2X,
                                itemPadding = PaddingValues(AppUISize.medium, AppUISize.tiny2X),
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

    @OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
    @Composable
    fun MainWindowContext(
        windowInfo: WindowInfo,
        content: @Composable () -> Unit,
    ) {
        BaseContext {
            CompositionLocalProvider(
                LocalMainWindowInfoState provides windowInfo,
            ) {
                content()
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
    @Composable
    fun SearchWindowContext(
        windowInfo: WindowInfo,
        content: @Composable () -> Unit,
    ) {
        BaseContext {
            CompositionLocalProvider(
                LocalSearchWindowInfoState provides windowInfo,
            ) {
                content()
            }
        }
    }
}
