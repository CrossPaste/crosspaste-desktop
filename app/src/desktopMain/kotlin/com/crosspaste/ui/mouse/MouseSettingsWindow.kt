package com.crosspaste.ui.mouse

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.DesktopContext
import org.koin.compose.koinInject
import java.awt.Dimension

private val INITIAL_SIZE = DpSize(720.dp, 640.dp)
private val MIN_SIZE = Dimension(600, 500)

@Composable
fun MouseSettingsWindow(windowIcon: Painter?) {
    val appWindowManager = koinInject<DesktopAppWindowManager>()
    val copywriter = koinInject<GlobalCopywriter>()

    val windowState =
        rememberWindowState(
            size = INITIAL_SIZE,
            position = WindowPosition(Alignment.Center),
        )

    Window(
        onCloseRequest = { appWindowManager.hideMouseSettingsWindow() },
        state = windowState,
        title = copywriter.getText("mouse_settings"),
        icon = windowIcon,
        resizable = true,
    ) {
        DisposableEffect(window) {
            window.minimumSize = MIN_SIZE
            onDispose {}
        }

        // Wrap content in the app theme (like Main/Search/Bubble) so colors track
        // the real light/dark scheme. Use a Surface, not a bare Box: Surface sets
        // LocalContentColor to onBackground, so the header text is readable. A
        // plain Box only paints the background and leaves text at the default
        // Color.Black — black-on-black in dark mode.
        DesktopContext.MouseSettingsWindowContext {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                MouseSettingsScreen()
            }
        }
    }
}
