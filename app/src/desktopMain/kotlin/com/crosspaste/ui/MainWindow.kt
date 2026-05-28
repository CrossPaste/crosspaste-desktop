package com.crosspaste.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
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
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUISize.large2X
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny2XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.xxLarge
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.platform.win32.WinDef
import org.koin.compose.koinInject

// macOS 交通灯按钮组（红/黄/绿）占用的水平宽度
private val MAC_TRAFFIC_LIGHT_INSET = 78.dp

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

    val isMacos = remember { platform.isMacos() }
    val isWindows = remember { platform.isWindows() }

    Window(
        onCloseRequest = {
            appWindowManager.hideMainWindow()
        },
        visible = mainWindowInfo.show,
        state = mainWindowInfo.state,
        title = appWindowManager.mainWindowTitle,
        icon = windowIcon,
        alwaysOnTop = alwaysOnTop,
        resizable = false,
        // macOS 保留原生窗口装饰以拿到系统交通灯；其他平台完全自绘
        undecorated = !isMacos,
    ) {
        DisposableEffect(Unit) {
            // Set window reference for manager
            appWindowManager.mainComposeWindow = window

            when {
                isMacos -> {
                    // JBR/Apple JDK 客户端属性：
                    // - fullWindowContent: 内容延伸到标题栏区域
                    // - transparentTitleBar: 标题栏背景透明
                    // - windowTitleVisible: 隐藏系统画的标题文本
                    window.rootPane.apply {
                        putClientProperty("apple.awt.fullWindowContent", true)
                        putClientProperty("apple.awt.transparentTitleBar", true)
                        putClientProperty("apple.awt.windowTitleVisible", false)
                    }
                }
                isWindows -> {
                    // Apply rounded corners on Windows (Win11 Build 22000+, silently ignored on older versions)
                    applyWindowsRoundedCorners(window)
                    // Remove minimize button — as a tray app, minimize is unnecessary
                    // and its hit area overlaps with the notification popup close button.
                    removeWindowsMinimizeButton(window)
                }
            }

            onDispose {
                appWindowManager.mainComposeWindow = null
            }
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            val titleBarModifier =
                Modifier
                    .fillMaxWidth()
                    .height(appSizeValue.windowDecorationHeight)
                    .background(AppUIColors.appBackground)
                    .padding(top = medium)
                    .padding(
                        start = if (isMacos) MAC_TRAFFIC_LIGHT_INSET else 0.dp,
                        end = pushpinPadding,
                    )

            val titleBarContent: @Composable () -> Unit = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
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

            if (isMacos) {
                // macOS：系统已提供标题栏拖拽，直接画内容
                Box(modifier = titleBarModifier) { titleBarContent() }
            } else {
                // Windows/Linux：完全无装饰窗口，由 Compose 提供拖拽区
                WindowDraggableArea {
                    Box(modifier = titleBarModifier) { titleBarContent() }
                }
            }

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

private fun removeWindowsMinimizeButton(window: java.awt.Window) {
    val hwnd = WinDef.HWND(Native.getWindowPointer(window))
    val user32 = com.sun.jna.platform.win32.User32.INSTANCE
    val style = user32.GetWindowLong(hwnd, com.sun.jna.platform.win32.User32.GWL_STYLE)
    user32.SetWindowLong(hwnd, com.sun.jna.platform.win32.User32.GWL_STYLE, style and WS_MINIMIZEBOX.inv())
}

private const val WS_MINIMIZEBOX = 0x00020000
