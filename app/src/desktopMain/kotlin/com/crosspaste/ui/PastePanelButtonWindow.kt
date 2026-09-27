package com.crosspaste.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.app.ExitMode
import com.crosspaste.app.WindowTrigger
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.i18n.SupportedLanguages
import com.crosspaste.platform.Platform
import com.crosspaste.ui.DesktopContext.PastePanelWindowContext
import com.crosspaste.ui.base.MenuHelper
import com.crosspaste.ui.paste.panel.PastePanelButtonContent
import org.koin.compose.koinInject
import java.awt.Font
import java.awt.PopupMenu

/**
 * Floating round button that opens and closes [PastePanelWindow]. It is toggled by the
 * `show_paste_panel` shortcut, can be dragged anywhere, and like the panel never
 * activates CrossPaste when clicked. A right click opens a native menu: Compose popups
 * would be clipped to this tiny window, while the AWT menu is a system menu that works
 * even though the window never activates.
 */
@Composable
fun PastePanelButtonWindow(windowIcon: Painter?) {
    val appWindowManager = koinInject<DesktopAppWindowManager>()
    val configManager = koinInject<DesktopConfigManager>()
    val copywriter = koinInject<GlobalCopywriter>()
    val menuHelper = koinInject<MenuHelper>()
    val platform = koinInject<Platform>()

    val applicationExit = LocalExitApplication.current

    val buttonSize = LocalDesktopAppSizeValueState.current.pastePanelButtonSize
    LaunchedEffect(buttonSize) {
        appWindowManager.resizePastePanelButton(buttonSize)
    }

    val buttonInfo by appWindowManager.pastePanelButtonInfo.collectAsState()
    val panelInfo by appWindowManager.pastePanelWindowInfo.collectAsState()

    val isMac = remember { platform.isMacos() }
    val isWindows = remember { platform.isWindows() }

    NonActivatingWindow(
        visible = buttonInfo.show,
        state = buttonInfo.state,
        title = appWindowManager.pastePanelButtonWindowTitle,
        transparent = true,
        onClosing = { appWindowManager.hidePastePanelButton() },
    ) {
        if (isMac) {
            // Pop-up-menu level only, so the button floats above full-screen apps
            MacAcrylicEffect(window = this.window)
        }

        val window = this.window
        val popupMenu = remember(window) { PopupMenu().also { window.add(it) } }
        DisposableEffect(window) {
            onDispose { window.remove(popupMenu) }
        }

        fun menuItem(
            label: String,
            action: () -> Unit,
        ) = java.awt.MenuItem(label).apply { addActionListener { action() } }

        // Rebuilt on every open so the labels follow the current language
        fun showMenu(
            x: Int,
            y: Int,
        ) {
            popupMenu.removeAll()
            if (isWindows) {
                popupMenu.font = windowsMenuFont(copywriter.language())
            }
            popupMenu.add(
                menuItem(copywriter.getText("show_main")) {
                    appWindowManager.showMainWindow(WindowTrigger.MENU)
                },
            )
            popupMenu.add(menuItem(menuHelper.settings.title(copywriter), menuHelper.settings.action))
            popupMenu.add(menuItem(menuHelper.shortcutKeys.title(copywriter), menuHelper.shortcutKeys.action))
            popupMenu.addSeparator()
            popupMenu.add(
                menuItem(copywriter.getText("hide_paste_panel_button")) {
                    configManager.updateConfig("showPastePanelButton", false)
                },
            )
            popupMenu.addSeparator()
            popupMenu.add(menuItem(copywriter.getText("quit")) { applicationExit(ExitMode.EXIT) })
            popupMenu.show(window, x, y)
        }

        PastePanelWindowContext {
            PastePanelButtonContent(
                window = window,
                panelOpen = panelInfo.show,
                onClick = { appWindowManager.switchPastePanelWindow(WindowTrigger.SYSTEM) },
                onSecondaryClick = ::showMenu,
                onMoved = { x, y ->
                    appWindowManager.movePastePanelButton(WindowPosition(x.dp, y.dp))
                },
            )
        }
    }
}

private const val WINDOWS_MENU_FONT_SIZE = 12

/**
 * AWT owner-draws heavyweight menus on Windows and can only render the glyphs the menu's
 * own font carries; its default logical font lacks the CJK ranges, so every label in those
 * languages comes out as boxes. A system UI font that covers the current language fixes
 * that, and the items inherit it from the menu.
 */
private fun windowsMenuFont(language: String): Font {
    val family =
        when (language) {
            SupportedLanguages.ZH -> "Microsoft YaHei UI"
            SupportedLanguages.ZH_HANT -> "Microsoft JhengHei UI"
            SupportedLanguages.JA -> "Yu Gothic UI"
            SupportedLanguages.KO -> "Malgun Gothic"
            else -> "Segoe UI"
        }
    return Font(family, Font.PLAIN, WINDOWS_MENU_FONT_SIZE)
}
