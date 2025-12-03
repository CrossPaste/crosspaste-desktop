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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.window.MenuBar
import com.crosspaste.app.AppUpdateService
import com.crosspaste.app.DesktopAppSize
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.app.ExitMode
import com.crosspaste.app.WindowTrigger
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.platform.Platform
import com.crosspaste.ui.base.DesktopMenu.ProvidesMenuContext
import com.crosspaste.ui.base.PasteTooltipIconView
import com.crosspaste.ui.base.pushpinActive
import com.crosspaste.ui.base.pushpinInactive
import kotlinx.coroutines.launch
import org.jetbrains.jewel.window.DecoratedWindow
import org.jetbrains.jewel.window.TitleBar
import org.koin.compose.koinInject

@Composable
fun MainWindow(windowIcon: Painter?) {
    val appSize = koinInject<DesktopAppSize>()
    val appUpdateService = koinInject<AppUpdateService>()
    val appWindowManager = koinInject<DesktopAppWindowManager>()
    val copywriter = koinInject<GlobalCopywriter>()
    val platform = koinInject<Platform>()
    val navigateManage = koinInject<NavigationManager>()

    val alwaysOnTop by appWindowManager.alwaysOnTopMainWindow.collectAsState()
    val mainWindowInfo by appWindowManager.mainWindowInfo.collectAsState()

    val pushpinPadding by remember {
        mutableStateOf(appSize.getPinPushEndPadding())
    }

    val applicationExit = LocalExitApplication.current

    val scope = rememberCoroutineScope()

    LaunchedEffect(mainWindowInfo.show) {
        if (mainWindowInfo.show) {
            appWindowManager.focusMainWindow(mainWindowInfo.trigger)
        }
    }

    DecoratedWindow(
        onCloseRequest = {
            appWindowManager.hideMainWindow()
        },
        visible = mainWindowInfo.show,
        state = mainWindowInfo.state,
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
                        scope.launch {
                            navigateManage.navigateAndClearStack(Devices)
                            appWindowManager.showMainWindow(WindowTrigger.MENU)
                        }
                    }
                    Item(copywriter.getText("scan")) {
                        scope.launch {
                            navigateManage.navigateAndClearStack(QrCode)
                            appWindowManager.showMainWindow(WindowTrigger.MENU)
                        }
                    }
                }
                Menu(copywriter.getText("action")) {
                    Item(copywriter.getText("settings")) {
                        scope.launch {
                            navigateManage.navigateAndClearStack(Settings)
                            appWindowManager.showMainWindow(WindowTrigger.MENU)
                        }
                    }
                    Item(copywriter.getText("extension")) {
                        scope.launch {
                            navigateManage.navigateAndClearStack(Extension)
                            appWindowManager.showMainWindow(WindowTrigger.MENU)
                        }
                    }
                    Item(copywriter.getText("import")) {
                        scope.launch {
                            navigateManage.navigateAndClearStack(Import)
                            appWindowManager.showMainWindow(WindowTrigger.MENU)
                        }
                    }
                    Item(copywriter.getText("export")) {
                        scope.launch {
                            navigateManage.navigateAndClearStack(Export)
                            appWindowManager.showMainWindow(WindowTrigger.MENU)
                        }
                    }
                }
                Menu(copywriter.getText("help")) {
                    Item(copywriter.getText("shortcut_keys")) {
                        scope.launch {
                            navigateManage.navigateAndClearStack(ShortcutKeys)
                            appWindowManager.showMainWindow(WindowTrigger.MENU)
                        }
                    }
                    Item(copywriter.getText("about")) {
                        scope.launch {
                            navigateManage.navigateAndClearStack(About)
                            appWindowManager.showMainWindow(WindowTrigger.MENU)
                        }
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

        ProvidesMenuContext {
            CrossPasteMainWindowContent()
        }
    }
}
