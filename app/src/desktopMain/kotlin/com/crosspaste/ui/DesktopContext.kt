package com.crosspaste.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalContextMenuRepresentation
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import com.crosspaste.app.WindowInfo
import com.crosspaste.ui.contextmenu.ContextMenuColors
import com.crosspaste.ui.contextmenu.ContextMenuMeasurements
import com.crosspaste.ui.contextmenu.MaterialContextMenuRepresentation
import com.crosspaste.ui.theme.AppUISize
import com.crosspaste.ui.theme.CrossPasteTheme.Theme

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
                                itemMinHeight = AppUISize.xLarge,
                                itemPadding = PaddingValues(AppUISize.medium, AppUISize.tiny3X),
                                menuTopPadding = AppUISize.tiny3X,
                                menuBottomPadding = AppUISize.tiny3X,
                                iconPadding = AppUISize.tiny,
                                dividerHeight = AppUISize.small2X,
                            ),
                        colors =
                            ContextMenuColors(
                                surface = MaterialTheme.colorScheme.surface,
                                text = MaterialTheme.colorScheme.onSurface,
                                itemHover = MaterialTheme.colorScheme.primary,
                                itemHoverText = MaterialTheme.colorScheme.onPrimary,
                            ),
                    ),
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

    @Composable
    fun BubbleWindowContext(content: @Composable () -> Unit) {
        BaseContext {
            content()
        }
    }
}
