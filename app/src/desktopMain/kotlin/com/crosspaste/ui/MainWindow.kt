package com.crosspaste.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults.rememberTooltipPositionProvider
import androidx.compose.material3.rememberTooltipState
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
import com.composables.icons.materialsymbols.rounded.Close
import com.composables.icons.materialsymbols.rounded.Push_pin
import com.composables.icons.materialsymbols.roundedfilled.Push_pin
import com.crosspaste.app.DesktopAppLaunchState
import com.crosspaste.app.DesktopAppSize
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.i18n.GlobalCopywriter
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

// Windows/Linux 自绘关闭按钮的 hot zone 宽度
private val CLOSE_BUTTON_WIDTH = 48.dp

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
            val titleBarOuterModifier =
                Modifier
                    .fillMaxWidth()
                    .height(appSizeValue.windowDecorationHeight)
                    .background(AppUIColors.appBackground)

            val titleBarContent: @Composable () -> Unit = {
                Box(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                    // 图钉按钮：受 pushpinPadding 控制距右距离；Windows/Linux 还要额外腾出关闭按钮宽度
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = medium)
                                .padding(
                                    start = if (isMacos) MAC_TRAFFIC_LIGHT_INSET else 0.dp,
                                    end =
                                        if (isMacos) {
                                            pushpinPadding
                                        } else {
                                            CLOSE_BUTTON_WIDTH + pushpinPadding
                                        },
                                ),
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

                    // macOS 由系统交通灯负责关闭；Windows/Linux 自绘关闭按钮占据右上角
                    if (!isMacos) {
                        TitleBarCloseButton(
                            modifier =
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .width(CLOSE_BUTTON_WIDTH)
                                    .fillMaxHeight(),
                            onClick = { appWindowManager.hideMainWindow() },
                        )
                    }
                }
            }

            if (isMacos) {
                // macOS：系统已提供标题栏拖拽，直接画内容
                Box(modifier = titleBarOuterModifier) { titleBarContent() }
            } else {
                // Windows/Linux：完全无装饰窗口，由 Compose 提供拖拽区
                WindowDraggableArea {
                    Box(modifier = titleBarOuterModifier) { titleBarContent() }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TitleBarCloseButton(
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val copywriter = koinInject<GlobalCopywriter>()

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val containerColor by animateColorAsState(
        targetValue =
            if (isHovered) {
                MaterialTheme.colorScheme.error
            } else {
                Color.Transparent
            },
        label = "closeButtonBackground",
    )
    val iconColor by animateColorAsState(
        targetValue =
            if (isHovered) {
                MaterialTheme.colorScheme.onError
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        label = "closeButtonTint",
    )

    val tooltipState = rememberTooltipState()
    val positionProvider = rememberTooltipPositionProvider(TooltipAnchorPosition.Below)

    TooltipBox(
        positionProvider = positionProvider,
        tooltip = {
            PlainTooltip {
                Text(copywriter.getText("hide_window"))
            }
        },
        state = tooltipState,
    ) {
        Box(
            modifier =
                modifier
                    .background(containerColor)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = MaterialSymbols.Rounded.Close,
                contentDescription = copywriter.getText("hide_window"),
                modifier = Modifier.size(large2X),
                tint = iconColor,
            )
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
