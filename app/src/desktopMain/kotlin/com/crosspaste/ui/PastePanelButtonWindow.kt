package com.crosspaste.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.app.WindowTrigger
import com.crosspaste.platform.Platform
import com.crosspaste.ui.DesktopContext.PastePanelWindowContext
import com.crosspaste.ui.paste.panel.PastePanelButtonContent
import org.koin.compose.koinInject

/**
 * Floating round button that opens and closes [PastePanelWindow]. It is toggled by the
 * `show_paste_panel` shortcut, can be dragged anywhere, and like the panel never
 * activates CrossPaste when clicked.
 */
@Composable
fun PastePanelButtonWindow(windowIcon: Painter?) {
    val appWindowManager = koinInject<DesktopAppWindowManager>()
    val platform = koinInject<Platform>()

    val buttonInfo by appWindowManager.pastePanelButtonInfo.collectAsState()
    val panelInfo by appWindowManager.pastePanelWindowInfo.collectAsState()

    val isMac = remember { platform.isMacos() }

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

        PastePanelWindowContext {
            PastePanelButtonContent(
                window = this.window,
                panelOpen = panelInfo.show,
                onClick = { appWindowManager.switchPastePanelWindow(WindowTrigger.SYSTEM) },
                onMoved = { x, y ->
                    appWindowManager.movePastePanelButton(WindowPosition(x.dp, y.dp))
                },
            )
        }
    }
}
