package com.crosspaste.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.awt.SwingWindow
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import com.crosspaste.platform.Platform
import com.crosspaste.platform.macos.MacAppUtils
import com.crosspaste.platform.windows.WindowsFocusUtils
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
 * An undecorated, always-on-top window that never takes keyboard focus and never
 * activates CrossPaste when clicked, so the app the user is working in keeps its focus.
 *
 * The window is created through the low-level [SwingWindow] overload so the native peer can
 * be configured before it exists. On macOS the only way a click can reach a window
 * without activating the app is an NSPanel carrying NSWindowStyleMaskNonactivatingPanel.
 * JBR does not expose that flag (Window.Type.POPUP is an ordinary NSWindow there), but it
 * does allocate an NSPanel for a root pane marked `Window.hidesOnDeactivate`; the flag is
 * then switched on natively once the peer exists, see [MacNonActivatingEffect].
 * Non-focusable state plus WS_EX_NOACTIVATE cover Windows; X11 honours the non-focusable
 * hint on its own.
 */
@Composable
fun NonActivatingWindow(
    visible: Boolean,
    state: WindowState,
    title: String,
    transparent: Boolean,
    onClosing: () -> Unit,
    content: @Composable FrameWindowScope.() -> Unit,
) {
    val platform = koinInject<Platform>()
    val isMac = remember { platform.isMacos() }
    val isWindows = remember { platform.isWindows() }

    SwingWindow(
        visible = visible,
        create = {
            ComposeWindow().apply {
                if (isMac) {
                    // Makes AWT back the window with an NSPanel; the flag itself is
                    // reset in MacNonActivatingEffect.
                    rootPane.putClientProperty("Window.hidesOnDeactivate", true)
                }
                defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
                this.title = title
                isUndecorated = true
                isTransparent = transparent
                isResizable = false
                focusableWindowState = false
                isAlwaysOnTop = true
                addWindowListener(
                    object : WindowAdapter() {
                        override fun windowClosing(e: WindowEvent) {
                            onClosing()
                        }
                    },
                )
            }
        },
        dispose = { it.dispose() },
        update = { window ->
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
        } else if (isWindows) {
            WindowsNoActivateEffect(window = this.window)
        }
        content()
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
