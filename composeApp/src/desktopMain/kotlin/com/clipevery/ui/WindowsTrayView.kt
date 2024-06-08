package com.clipevery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
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
    val density = LocalDensity.current

    val appWindowManager = current.koin.get<AppWindowManager>()
    val notificationManager = current.koin.get<NotificationManager>()

    val trayIcon = painterResource("icon/clipevery.tray.win.png")

    var showMenu by remember { mutableStateOf(false) }

    val menuWindowState =
        rememberWindowState(
            placement = WindowPlacement.Floating,
            size = DpSize(170.dp, 204.dp),
        )

    val densityFloat = density.density

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
                            x = ((event.x - insets.left) / densityFloat).dp,
                            y = ((event.y - insets.bottom) / densityFloat).dp - 204.dp,
                        )
                }
            },
    )

    if (showMenu) {
        Window(
            onCloseRequest = ::exitApplication,
            visible = true,
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
}

@Composable
fun WindowTrayMenu(hideMenu: () -> Unit) {
    val current = LocalKoinApplication.current
    val appWindowManager = current.koin.get<AppWindowManager>()

    ClipeveryTheme {
        Box(
            modifier =
                Modifier
                    .background(Color.Transparent)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                hideMenu()
                            },
                            onTap = {
                                hideMenu()
                            },
                            onLongPress = {
                                hideMenu()
                            },
                            onPress = {},
                        )
                    }
                    .clip(RoundedCornerShape(10.dp))
                    .fillMaxSize()
                    .padding(10.dp, 0.dp, 10.dp, 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .shadow(5.dp, RoundedCornerShape(10.dp), false)
                        .fillMaxSize()
                        .border(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {},
                                onTap = {},
                                onLongPress = {},
                                onPress = {},
                            )
                        },
                contentAlignment = Alignment.Center,
            ) {
                MenuView {
                    appWindowManager.activeMainWindow()
                    hideMenu()
                }
            }
        }
    }
}
