package com.crosspaste.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.platform.Platform
import com.crosspaste.platform.windows.WindowsVersionHelper
import com.crosspaste.ui.DesktopContext.PastePanelWindowContext
import com.crosspaste.ui.model.PastePanelViewModel
import com.crosspaste.ui.paste.panel.PastePanelContent
import com.crosspaste.ui.theme.ThemeDetector
import org.koin.compose.koinInject

/**
 * Floating paste panel (#4995): a non-activating window listing the clipboard history,
 * opened and closed from [PastePanelButtonWindow] and placed beside it. Clicking a row
 * pastes into the app that currently has keyboard focus; because the panel never becomes
 * the active window, that focus is never lost.
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

    NonActivatingWindow(
        visible = windowInfo.show,
        state = windowInfo.state,
        title = appWindowManager.pastePanelWindowTitle,
        transparent = transparent,
        onClosing = { appWindowManager.hidePastePanelWindow() },
    ) {
        if (isMac) {
            MacAcrylicEffect(
                window = this.window,
                isDark = isDarkTheme,
            )
        } else if (isWindowsAndSupportBlurEffect) {
            WindowsBlurEffect(
                window = this.window,
                isDark = isDarkTheme,
            )
        }

        PastePanelWindowContext {
            PastePanelContent(transparent = transparent)
        }
    }
}
