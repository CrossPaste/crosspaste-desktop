package com.clipevery.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberWindowState
import com.clipevery.LocalKoinApplication
import com.clipevery.app.AppWindowManager
import com.clipevery.ui.base.NotificationManager
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

@Composable
fun ApplicationScope.WindowsTray(windowState: WindowState) {
    val current = LocalKoinApplication.current

    val appWindowManager = current.koin.get<AppWindowManager>()
    val notificationManager = current.koin.get<NotificationManager>()

    val trayIcon = painterResource("icon/clipevery.tray.win.png")

    var showMenu by remember { mutableStateOf(false) }

    val menuWindowState =
        rememberWindowState(
            placement = WindowPlacement.Floating,
            size = DpSize(150.dp, 168.dp),
        )

    Tray(
        state = remember { notificationManager.trayState },
        icon = trayIcon,
        tooltip = "Clipevery",
        mouseListener =
            getTrayMouseAdapter(appWindowManager, windowState) { event, insets ->
                if (event.button == MouseEvent.BUTTON1) {
                    appWindowManager.switchMainWindow()
                } else {
                    showMenu = true
                    menuWindowState.position =
                        WindowPosition(
                            x = (event.x - insets.left).dp,
                            y = 30.dp,
                        )
                }
            },
    )

    Window(
        onCloseRequest = ::exitApplication,
        visible = showMenu,
        state = menuWindowState,
        title = "Clipevery Menu",
        alwaysOnTop = true,
        undecorated = true,
        transparent = true,
        resizable = false,
    ) {
        DisposableEffect(Unit) {
            val windowListener =
                object : WindowAdapter() {
                    override fun windowGainedFocus(e: WindowEvent?) {
                        showMenu = true
                    }

                    override fun windowLostFocus(e: WindowEvent?) {
                        showMenu = false
                    }
                }

            window.addWindowFocusListener(windowListener)

            onDispose {
                window.removeWindowFocusListener(windowListener)
            }
        }

        WindowTrayMenu {
            showMenu = false
        }
    }
}

@Composable
fun WindowTrayMenu(hideMenu: () -> Unit) {
    val density = LocalDensity.current

    Popup(
        alignment = Alignment.TopEnd,
        offset =
            IntOffset(
                with(density) { ((-14).dp).roundToPx() },
                with(density) { (30.dp).roundToPx() },
            ),
        onDismissRequest = {
        },
        properties =
            PopupProperties(
                focusable = true,
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
            ),
    ) {
        MenuView {
            hideMenu()
        }
    }
}
