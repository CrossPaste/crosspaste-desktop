package com.crosspaste.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Push_pin
import com.composables.icons.materialsymbols.roundedfilled.Push_pin
import com.crosspaste.app.DesktopAppLaunchState
import com.crosspaste.app.DesktopAppSize
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.platform.Platform
import com.crosspaste.platform.windows.api.Dwmapi
import com.crosspaste.ui.DesktopContext.MainWindowContext
import com.crosspaste.ui.base.GeneralIconButton
import com.crosspaste.ui.settings.GrantAccessibilityDialog
import com.crosspaste.ui.theme.AppUISize.large2X
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny2XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.xxLarge
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.platform.win32.WinDef
import org.jetbrains.jewel.window.DecoratedWindow
import org.jetbrains.jewel.window.TitleBar
import org.koin.compose.koinInject
import java.awt.geom.RoundRectangle2D

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

            // Apply rounded corners on Windows and Linux
            var shapeListener: java.awt.event.ComponentAdapter? = null
            if (platform.isWindows()) {
                applyWindowsRoundedCorners(window)
            } else if (platform.isLinux()) {
                applyLinuxRoundedCorners(window)
                shapeListener =
                    object : java.awt.event.ComponentAdapter() {
                        override fun componentResized(e: java.awt.event.ComponentEvent) {
                            applyLinuxRoundedCorners(e.component as java.awt.Window)
                        }
                    }
                window.addComponentListener(shapeListener)
            }

            onDispose {
                shapeListener?.let { window.removeComponentListener(it) }
                appWindowManager.mainComposeWindow = null
            }
        }

        TitleBar {
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
                                MaterialSymbols.RoundedFilled.Push_pin
                            } else {
                                MaterialSymbols.Rounded.Push_pin
                            },
                        desc = "always_on_top",
                        colors =
                            IconButtonDefaults.iconButtonColors(
                                containerColor = Color.Transparent,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        buttonSize = xxLarge,
                        iconSize = large2X,
                        shape = tiny2XRoundedCornerShape,
                        onClick = {
                            appWindowManager.switchAlwaysOnTopMainWindow()
                        },
                    )
                }
            }
        }

        val isMacos = remember { platform.isMacos() }

        if (isMacos) {
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

private fun applyWindowsRoundedCorners(window: java.awt.Window) {
    val hwnd = WinDef.HWND(Native.getWindowPointer(window))
    val cornerPreference = Memory(4).apply { setInt(0, Dwmapi.DWMWCP_ROUND) }
    Dwmapi.INSTANCE.DwmSetWindowAttribute(
        hwnd,
        Dwmapi.DWMWA_WINDOW_CORNER_PREFERENCE,
        cornerPreference,
        4,
    )
}

private const val LINUX_CORNER_ARC = 20.0

private fun applyLinuxRoundedCorners(window: java.awt.Window) {
    val bounds = window.bounds
    window.shape =
        RoundRectangle2D.Double(
            0.0,
            0.0,
            bounds.width.toDouble(),
            bounds.height.toDouble(),
            LINUX_CORNER_ARC,
            LINUX_CORNER_ARC,
        )
}
