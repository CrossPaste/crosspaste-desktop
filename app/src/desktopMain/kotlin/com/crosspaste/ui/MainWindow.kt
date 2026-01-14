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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import com.crosspaste.app.DesktopAppLaunchState
import com.crosspaste.app.DesktopAppSize
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.platform.Platform
import com.crosspaste.ui.DesktopContext.MainWindowContext
import com.crosspaste.ui.base.GeneralIconButton
import com.crosspaste.ui.settings.GrantAccessibilityDialog
import com.crosspaste.ui.theme.AppUISize.large2X
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.xxLarge
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
    val appWindowManager = koinInject<DesktopAppWindowManager>()
    val configManager = koinInject<DesktopConfigManager>()
    val platform = koinInject<Platform>()

    val alwaysOnTop by appWindowManager.alwaysOnTopMainWindow.collectAsState()
    val mainWindowInfo by appWindowManager.mainWindowInfo.collectAsState()

    val pushpinPadding by remember {
        mutableStateOf(appSize.getPinPushEndPadding())
    }

    val appSizeValue = LocalDesktopAppSizeValueState.current

    val config by configManager.config.collectAsState()

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
                        iconColor = MaterialTheme.colorScheme.onSurface,
                        buttonSize = xxLarge,
                        iconSize = large2X,
                        onClick = {
                            appWindowManager.switchAlwaysOnTopMainWindow()
                        },
                    )
                }
            }
        }

        val isWindows = remember { platform.isWindows() }

        if (!isWindows) {
            DesktopMenuBar()
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
