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
import androidx.compose.ui.window.MenuBar
import com.crosspaste.app.AppUpdateService
import com.crosspaste.app.DesktopAppSize
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.app.ExitMode
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.listener.GlobalListener
import com.crosspaste.platform.Platform
import com.crosspaste.ui.base.PasteTooltipIconView
import com.crosspaste.ui.base.pushpinActive
import com.crosspaste.ui.base.pushpinInactive
import org.jetbrains.jewel.window.DecoratedWindow
import org.jetbrains.jewel.window.TitleBar
import org.koin.compose.koinInject

@Composable
fun MainWindow(windowIcon: Painter?) {
    val appSize = koinInject<DesktopAppSize>()
    val appUpdateService = koinInject<AppUpdateService>()
    val appWindowManager = koinInject<DesktopAppWindowManager>()
    val copywriter = koinInject<GlobalCopywriter>()
    val globalListener = koinInject<GlobalListener>()
    val platform = koinInject<Platform>()

    val alwaysOnTop by appWindowManager.alwaysOnTopMainWindow.collectAsState()
    val mainWindowState by appWindowManager.mainWindowState.collectAsState()
    val showMainWindow by appWindowManager.showMainWindow.collectAsState()

    val pushpinPadding by remember {
        mutableStateOf(appSize.getPinPushEndPadding())
    }

    val applicationExit = LocalExitApplication.current

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

        var isMac by remember { mutableStateOf(platform.isMacos()) }

        if (isMac) {
            MenuBar {
                Menu(copywriter.getText("sync")) {
                    Item(copywriter.getText("devices")) {
                        appWindowManager.toScreen(Devices)
                        appWindowManager.showMainWindow()
                    }
                    Item(copywriter.getText("scan")) {
                        appWindowManager.toScreen(QrCode)
                        appWindowManager.showMainWindow()
                    }
                }
                Menu(copywriter.getText("action")) {
                    Item(copywriter.getText("settings")) {
                        appWindowManager.toScreen(Settings)
                        appWindowManager.showMainWindow()

                    }
                    Item(copywriter.getText("import")) {
                        appWindowManager.toScreen(Import)
                        appWindowManager.showMainWindow()
                    }
                    Item(copywriter.getText("export")) {
                        appWindowManager.toScreen(Export)
                        appWindowManager.showMainWindow()
                    }
                }
                Menu(copywriter.getText("help")) {
                    Item(copywriter.getText("shortcut_keys")) {
                        appWindowManager.toScreen(ShortcutKeys)
                        appWindowManager.showMainWindow()
                    }
                    Item(copywriter.getText("about")) {
                        appWindowManager.toScreen(About)
                        appWindowManager.showMainWindow()
                    }
                    Item(copywriter.getText("check_for_updates")) {
                        appUpdateService.tryTriggerUpdate()
                    }
                    Separator()
                    Item(copywriter.getText("quit")) {
                        applicationExit(ExitMode.EXIT)
                    }
                }
            }
        }

        CrossPasteMainWindowContent()
    }
}
