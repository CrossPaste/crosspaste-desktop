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
import com.crosspaste.ui.DesktopContext.PastePanelWindowContext
import com.crosspaste.ui.model.PastePanelViewModel
import com.crosspaste.ui.paste.panel.PastePanelContent
import com.crosspaste.ui.paste.panel.PastePanelSurface
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
    val isWindows = remember { platform.isWindows() }

    // macOS blurs the desktop behind the window itself. Windows gets a per-pixel-alpha
    // window only so the panel can paint its own rounded card: DWM never rounds a layered
    // window and its system backdrop would fill the whole rectangle behind the corners,
    // so neither the corner preference nor the blur effect is usable here.
    val surface =
        when {
            isMac -> PastePanelSurface.ACRYLIC
            isWindows -> PastePanelSurface.CARD
            else -> PastePanelSurface.PLAIN
        }

    LaunchedEffect(windowInfo.show) {
        if (windowInfo.show) {
            pastePanelViewModel.onShown()
        }
    }

    NonActivatingWindow(
        visible = windowInfo.show,
        state = windowInfo.state,
        title = appWindowManager.pastePanelWindowTitle,
        transparent = surface != PastePanelSurface.PLAIN,
        onClosing = { appWindowManager.hidePastePanelWindow() },
    ) {
        if (isMac) {
            MacAcrylicEffect(
                window = this.window,
                isDark = isDarkTheme,
            )
        }

        PastePanelWindowContext {
            PastePanelContent(surface = surface)
        }
    }
}
