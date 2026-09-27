package com.crosspaste.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.app.ExitMode
import com.crosspaste.app.WindowTrigger
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.platform.Platform
import com.crosspaste.platform.windows.WindowsPopupMenu
import com.crosspaste.ui.DesktopContext.PastePanelWindowContext
import com.crosspaste.ui.base.MenuHelper
import com.crosspaste.ui.base.NativeMenuEntry
import com.crosspaste.ui.paste.panel.PastePanelButtonContent
import com.crosspaste.utils.GlobalCoroutineScope.mainCoroutineDispatcher
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.awt.MenuItem
import java.awt.PopupMenu

/**
 * Floating round button that opens and closes [PastePanelWindow]. It is toggled by the
 * `show_paste_panel` shortcut, can be dragged anywhere, and like the panel never
 * activates CrossPaste when clicked. A right click opens a native menu: Compose popups
 * would be clipped to this tiny window, while a system menu works even though the window
 * never activates. On Windows that is a Win32 menu, see [WindowsPopupMenu] for why the
 * AWT one cannot be used there.
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
        val popupMenu =
            if (isWindows) {
                null
            } else {
                remember(window) { PopupMenu().also { window.add(it) } }
            }
        DisposableEffect(window) {
            onDispose {
                popupMenu?.let { window.remove(it) }
            }
        }

        // Built on every open so the labels follow the current language
        fun menuEntries(): List<NativeMenuEntry> =
            listOf(
                NativeMenuEntry.Item(copywriter.getText("show_main")) {
                    appWindowManager.showMainWindow(WindowTrigger.MENU)
                },
                NativeMenuEntry.Item(menuHelper.settings.title(copywriter), menuHelper.settings.action),
                NativeMenuEntry.Item(menuHelper.shortcutKeys.title(copywriter), menuHelper.shortcutKeys.action),
                NativeMenuEntry.Separator,
                NativeMenuEntry.Item(copywriter.getText("hide_paste_panel_button")) {
                    configManager.updateConfig("showPastePanelButton", false)
                },
                NativeMenuEntry.Separator,
                NativeMenuEntry.Item(copywriter.getText("quit")) { applicationExit(ExitMode.EXIT) },
            )

        fun showMenu(
            x: Int,
            y: Int,
            surface: Color,
        ) {
            if (isWindows) {
                WindowsPopupMenu.show(menuEntries()) { action ->
                    mainCoroutineDispatcher.launch { action() }
                }
                return
            }
            val menu = popupMenu ?: return
            menu.removeAll()
            menuEntries().forEach { entry ->
                when (entry) {
                    is NativeMenuEntry.Item ->
                        menu.add(MenuItem(entry.label).apply { addActionListener { entry.action() } })
                    NativeMenuEntry.Separator -> menu.addSeparator()
                }
            }
            // XAWT paints the menu in the background colour of the component it is shown
            // over, and a transparent window's own background is fully transparent, which left the
            // Linux menu see-through. The content pane never paints inside a transparent
            // window, so its colour is free to carry the theme surface for the menu; XAWT
            // derives a readable text colour from it in either theme.
            val contentPane = window.contentPane
            contentPane.background = java.awt.Color(surface.toArgb())
            menu.show(contentPane, x, y)
        }

        PastePanelWindowContext {
            val menuSurface = MaterialTheme.colorScheme.surface
            PastePanelButtonContent(
                window = window,
                panelOpen = panelInfo.show,
                onClick = { appWindowManager.switchPastePanelWindow(WindowTrigger.SYSTEM) },
                onSecondaryClick = { x, y -> showMenu(x, y, menuSurface) },
                onMoved = { x, y ->
                    appWindowManager.movePastePanelButton(WindowPosition(x.dp, y.dp))
                },
            )
        }
    }
}
