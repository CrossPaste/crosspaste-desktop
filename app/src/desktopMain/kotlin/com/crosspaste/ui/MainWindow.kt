package com.crosspaste.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.window.MenuBar
import com.crosspaste.app.DesktopAppSize
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.listener.GlobalListener
import com.crosspaste.ui.base.PasteTooltipIconView
import com.crosspaste.ui.base.pushpinActive
import com.crosspaste.ui.base.pushpinInactive
import org.jetbrains.jewel.window.DecoratedWindow
import org.jetbrains.jewel.window.TitleBar
import org.koin.compose.koinInject

@Composable
fun MainWindow(windowIcon: Painter?) {
    val appSize = koinInject<DesktopAppSize>()
    val appWindowManager = koinInject<DesktopAppWindowManager>()
    val copywriter = koinInject<GlobalCopywriter>()
    val globalListener = koinInject<GlobalListener>()

    val alwaysOnTop by appWindowManager.alwaysOnTopMainWindow.collectAsState()
    val mainWindowState by appWindowManager.mainWindowState.collectAsState()
    val showMainWindow by appWindowManager.showMainWindow.collectAsState()

    val pushpinPadding by remember {
        mutableStateOf(appSize.getPinPushEndPadding())
    }

    // Initialize global listener only once
    LaunchedEffect(Unit) {
        globalListener.start()
    }

    DecoratedWindow(
        onCloseRequest = {
            appWindowManager.hideMainWindow()
        },
        visible = showMainWindow,
        state = mainWindowState,
        title = appWindowManager.mainWindowTitle,
        icon = windowIcon,
        alwaysOnTop = alwaysOnTop,
        resizable = false,
    ) {
        DisposableEffect(Unit) {
            // Set window reference for manager
            appWindowManager.mainComposeWindow = window

            onDispose {
                // Clean up window reference and listener
                appWindowManager.mainComposeWindow = null
            }
        }

        TitleBar {
            WindowDraggableArea {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .padding(horizontal = pushpinPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    PasteTooltipIconView(
                        painter = if (alwaysOnTop) pushpinActive() else pushpinInactive(),
                        text = "CrossPaste",
                        contentDescription = "alwaysOnTop",
                        onClick = {
                            appWindowManager.switchAlwaysOnTopMainWindow()
                        },
                    )
                }
            }
        }

        var action by remember { mutableStateOf("Last action: None") }
        var isOpen by remember { mutableStateOf(true) }

        var isSubmenuShowing by remember { mutableStateOf(false) }

        MenuBar {
            Menu("sync") {
                Item("devices", onClick = { })
                Item("Paste", onClick = { action = "Last action: Paste" }, shortcut = KeyShortcut(Key.V, ctrl = true))
            }
            Menu("Actions", mnemonic = 'A') {
                CheckboxItem(
                    "Advanced settings",
                    checked = isSubmenuShowing,
                    onCheckedChange = {
                        isSubmenuShowing = !isSubmenuShowing
                    },
                )
                if (isSubmenuShowing) {
                    Menu("Settings") {
                        Item("Setting 1", onClick = { action = "Last action: Setting 1" })
                        Item("Setting 2", onClick = { action = "Last action: Setting 2" })
                    }
                }
                Separator()
                Item("Exit", onClick = { isOpen = false }, shortcut = KeyShortcut(Key.Escape), mnemonic = 'E')
            }
        }

        CrossPasteMainWindowContent()
    }
}
