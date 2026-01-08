package com.crosspaste.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.window.MenuBar
import com.crosspaste.app.AppUpdateService
import com.crosspaste.app.DesktopAppLaunchState
import com.crosspaste.app.DesktopAppSize
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.app.ExitMode
import com.crosspaste.app.WindowTrigger
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.platform.Platform
import com.crosspaste.ui.DesktopContext.MainWindowContext
import com.crosspaste.ui.base.GeneralIconButton
import com.crosspaste.ui.settings.GrantAccessibilityDialog
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.xLarge
import com.crosspaste.ui.theme.AppUISize.xxxLarge
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.window.DecoratedWindowIconKeys
import org.jetbrains.jewel.ui.icon.PathIconKey
import org.jetbrains.jewel.window.DecoratedWindow
import org.jetbrains.jewel.window.TitleBar
import org.jetbrains.jewel.window.defaultTitleBarStyle
import org.jetbrains.jewel.window.styling.TitleBarIcons
import org.jetbrains.jewel.window.styling.TitleBarStyle
import org.koin.compose.koinInject

val CorrectMinimizeIcon = PathIconKey("window/minimize.svg", DecoratedWindowIconKeys::class.java)
val CorrectMaximizeIcon = PathIconKey("window/maximize.svg", DecoratedWindowIconKeys::class.java)
val CorrectRestoreIcon = PathIconKey("window/restore.svg", DecoratedWindowIconKeys::class.java)
val CorrectCloseIcon = PathIconKey("window/close.svg", DecoratedWindowIconKeys::class.java)

@Composable
fun MainWindow(windowIcon: Painter?) {
    val appLaunchState = koinInject<DesktopAppLaunchState>()
    val appSize = koinInject<DesktopAppSize>()
    val appUpdateService = koinInject<AppUpdateService>()
    val appWindowManager = koinInject<DesktopAppWindowManager>()
    val configManager = koinInject<DesktopConfigManager>()
    val copywriter = koinInject<GlobalCopywriter>()
    val platform = koinInject<Platform>()
    val navigateManage = koinInject<NavigationManager>()

    val alwaysOnTop by appWindowManager.alwaysOnTopMainWindow.collectAsState()
    val mainWindowInfo by appWindowManager.mainWindowInfo.collectAsState()

    val pushpinPadding by remember {
        mutableStateOf(appSize.getPinPushEndPadding())
    }

    val applicationExit = LocalExitApplication.current
    val appSizeValue = LocalDesktopAppSizeValueState.current

    val config by configManager.config.collectAsState()

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

        val style =
            if (platform.isLinux()) {
                val defaultStyle = JewelTheme.defaultTitleBarStyle

                val patchedIcons =
                    TitleBarIcons(
                        minimizeButton = CorrectMinimizeIcon,
                        maximizeButton = CorrectMaximizeIcon,
                        restoreButton = CorrectRestoreIcon,
                        closeButton = CorrectCloseIcon,
                    )

                TitleBarStyle(
                    colors = defaultStyle.colors,
                    metrics = defaultStyle.metrics,
                    icons = patchedIcons,
                    dropdownStyle = defaultStyle.dropdownStyle,
                    iconButtonStyle = defaultStyle.iconButtonStyle,
                    paneButtonStyle = defaultStyle.paneButtonStyle,
                    paneCloseButtonStyle = defaultStyle.paneCloseButtonStyle,
                )
            } else {
                JewelTheme.defaultTitleBarStyle
            }

        TitleBar(
            style = style,
        ) {
            WindowDraggableArea {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(appSizeValue.windowDecorationHeight)
                            .padding(top = medium)
                            .padding(end = pushpinPadding),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.End,
                ) {
                    GeneralIconButton(
                        imageVector =
                            if (alwaysOnTop) {
                                Icons.Filled.PushPin
                            } else {
                                Icons.Outlined.PushPin
                            },
                        desc = "always_on_top",
                        buttonSize = xxxLarge,
                        iconSize = xLarge,
                        onClick = {
                            appWindowManager.switchAlwaysOnTopMainWindow()
                        },
                    )
                }
            }
        }

        val isMac = remember { platform.isMacos() }

        if (isMac) {
            MenuBar {
                Menu(copywriter.getText("sync")) {
                    Item(copywriter.getText("devices")) {
                        scope.launch {
                            navigateManage.navigateAndClearStack(Devices)
                            appWindowManager.showMainWindow(WindowTrigger.MENU)
                        }
                    }
                    Item(copywriter.getText("pairing_code")) {
                        scope.launch {
                            navigateManage.navigateAndClearStack(PairingCode)
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

        MainWindowContext(mainWindowInfo) {
            CrossPasteMainWindowContent()
            if (config.showGrantAccessibility && !appLaunchState.accessibilityPermissions) {
                GrantAccessibilityDialog {
                    configManager.updateConfig("showGrantAccessibility", false)
                }
            }
        }
    }
}
