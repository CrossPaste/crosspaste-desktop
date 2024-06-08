package com.clipevery.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.WindowState
import com.clipevery.LocalKoinApplication
import com.clipevery.app.AppWindowManager
import com.clipevery.ui.base.NotificationManager
import java.awt.event.MouseEvent

@Composable
fun ApplicationScope.WindowsTray(windowState: WindowState) {
    val current = LocalKoinApplication.current

    val appWindowManager = current.koin.get<AppWindowManager>()
    val notificationManager = current.koin.get<NotificationManager>()

    val trayIcon = painterResource("icon/clipevery.tray.win.png")

    var showMenu by remember { mutableStateOf(false) }

    Tray(
        state = remember { notificationManager.trayState },
        icon = trayIcon,
        tooltip = "Clipevery",
        mouseListener =
            getTrayMouseAdapter(appWindowManager, windowState) { event, insets ->
                if (event.button == MouseEvent.BUTTON1) {
                    appWindowManager.switchMainWindow()
                } else {
                }
            },
    )

    if (showMenu) {
    }
}
