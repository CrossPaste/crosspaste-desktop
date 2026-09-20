package com.crosspaste.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.platform.Platform
import com.crosspaste.platform.macos.MacAppUtils
import com.crosspaste.platform.windows.WindowsFocusUtils
import com.crosspaste.platform.windows.WindowsVersionHelper
import com.crosspaste.ui.DesktopContext.PastePanelWindowContext
import com.crosspaste.ui.model.PastePanelViewModel
import com.crosspaste.ui.paste.panel.PastePanelContent
import com.crosspaste.ui.theme.ThemeDetector
import com.crosspaste.utils.cpuDispatcher
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.WindowConstants
import kotlin.math.roundToInt

/**
 * Floating paste panel (#4995): an always-on-top, non-activating window listing the
 * clipboard history. Clicking a row pastes into the app that currently has keyboard
 * focus; because the panel never becomes the active window, that focus is never lost.
 *
 * The window is created through the low-level [Window] overload so the native peer can
 * be configured before it exists. On macOS the only way a click can reach a window
 * without activating the app is an NSPanel carrying NSWindowStyleMaskNonactivatingPanel.
 * JBR does not expose that flag (Window.Type.POPUP is an ordinary NSWindow there), but it
 * does allocate an NSPanel for a root pane marked `Window.hidesOnDeactivate`; the flag is
 * then switched on natively once the peer exists, see [MacNonActivatingEffect].
 * Non-focusable state plus WS_EX_NOACTIVATE cover Windows; X11 honours the non-focusable
 * hint on its own.
 */
@Composable
fun PastePanelWindow(windowIcon: Painter?) {
    val appWindowManager = koinInject<DesktopAppWindowManager>()
    val pastePanelViewModel = koinInject<PastePanelViewModel>()
    val platform = koinInject<Platform>()
    val themeDetector = koinInject<ThemeDetector>()

    val windowInfo by appWindowManager.pastePanelWindowInfo.collectAsState()

    val themeConfig by themeDetector.themeConfig.collectAsState()
    val isDarkTheme = themeConfig.resolveIsDark(isSystemInDarkTheme())

    val isMac = remember { platform.isMacos() }
    val isWindows = remember { platform.isWindows() }
    val isWindowsAndSupportBlurEffect =
        remember {
            platform.isWindows() && WindowsVersionHelper.isWindows11_22H2OrGreater
        }
    val transparent = isMac || isWindowsAndSupportBlurEffect

    LaunchedEffect(windowInfo.show) {
        if (windowInfo.show) {
            pastePanelViewModel.onShown()
        }
    }

    Window(
        visible = windowInfo.show,
        create = {
            ComposeWindow().apply {
                if (isMac) {
                    // Makes AWT back the window with an NSPanel; the flag itself is
                    // reset in MacNonActivatingEffect.
                    rootPane.putClientProperty("Window.hidesOnDeactivate", true)
                }
                defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
                title = appWindowManager.pastePanelWindowTitle
                isUndecorated = true
                isTransparent = transparent
                isResizable = false
                focusableWindowState = false
                isAlwaysOnTop = true
                addWindowListener(
                    object : WindowAdapter() {
                        override fun windowClosing(e: WindowEvent) {
                            appWindowManager.hidePastePanelWindow()
                        }
                    },
                )
            }
        },
        dispose = { it.dispose() },
        update = { window ->
            val state = windowInfo.state
            window.setSize(
                state.size.width.value
                    .roundToInt(),
                state.size.height.value
                    .roundToInt(),
            )
            val position = state.position
            if (position is WindowPosition.Absolute) {
                window.setLocation(position.x.value.roundToInt(), position.y.value.roundToInt())
            }
        },
    ) {
        if (isMac) {
            MacNonActivatingEffect(window = this.window)
            MacAcrylicEffect(
                window = this.window,
                isDark = isDarkTheme,
            )
        } else if (isWindows) {
            WindowsNoActivateEffect(window = this.window)
            if (isWindowsAndSupportBlurEffect) {
                WindowsBlurEffect(
                    window = this.window,
                    isDark = isDarkTheme,
                )
            }
        }

        PastePanelWindowContext {
            PastePanelContent(
                transparent = transparent,
                onClose = { appWindowManager.hidePastePanelWindow() },
            )
        }
    }
}

@Composable
private fun MacNonActivatingEffect(window: ComposeWindow) {
    LaunchedEffect(window) {
        snapshotFlow { window.isDisplayable }.first { it }
        withContext(cpuDispatcher) {
            runCatching {
                MacAppUtils.makeWindowNonActivating(Pointer(window.windowHandle))
            }
        }
    }
}

@Composable
private fun WindowsNoActivateEffect(window: ComposeWindow) {
    LaunchedEffect(window) {
        snapshotFlow { window.isDisplayable }.first { it }
        WindowsFocusUtils.makeNonActivating(WinDef.HWND(Native.getWindowPointer(window)))
    }
}
