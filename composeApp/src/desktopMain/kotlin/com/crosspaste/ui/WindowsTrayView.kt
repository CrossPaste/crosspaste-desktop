package com.crosspaste.ui

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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.crosspaste.app.AppLaunchState
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.app.WinAppWindowManager
import com.crosspaste.ui.base.DesktopNotificationManager
import com.crosspaste.ui.base.NotificationManager
import com.crosspaste.utils.GlobalCoroutineScopeImpl.mainCoroutineDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.awt.GraphicsDevice
import java.awt.GraphicsEnvironment
import java.awt.Insets
import java.awt.Toolkit
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

object WindowsTrayView {

    val logger = KotlinLogging.logger {}

    @Composable
    fun Tray() {
        val appLaunchState = koinInject<AppLaunchState>()
        val appWindowManager = koinInject<DesktopAppWindowManager>()
        val notificationManager = koinInject<NotificationManager>() as DesktopNotificationManager

        val trayIcon = painterResource("icon/crosspaste.png")

        var showMenu by remember { mutableStateOf(false) }

        val menuWindowState =
            rememberWindowState(
                placement = WindowPlacement.Floating,
                size = DpSize(170.dp, 204.dp),
            )

        LaunchedEffect(Unit) {
            if (appLaunchState.firstLaunch && !appWindowManager.hasCompletedFirstLaunchShow) {
                delay(1000)
                refreshWindowPosition(appWindowManager, null) { _, _, _ -> }
                appWindowManager.showMainWindow = true
                appWindowManager.hasCompletedFirstLaunchShow = true
            }
        }

        CrossPasteTray(
            icon = trayIcon,
            state = remember { notificationManager.trayState },
            tooltip = "CrossPaste",
            mouseListener =
                WindowsTrayMouseClicked(appWindowManager) { event, gd, insets ->
                    if (event.button == MouseEvent.BUTTON1) {
                        mainCoroutineDispatcher.launch(CoroutineName("Switch CrossPaste")) {
                            appWindowManager.switchMainWindow()
                        }
                    } else {
                        showMenu = true
                        val bounds = gd.defaultConfiguration.bounds
                        val density: Float = gd.displayMode.width.toFloat() / bounds.width
                        menuWindowState.position =
                            WindowPosition(
                                x = ((event.x / density) - insets.left).dp - 32.dp,
                                y = (bounds.height - insets.bottom).dp - 204.dp,
                            )
                    }
                },
        )

        if (showMenu) {
            Window(
                onCloseRequest = { },
                visible = true,
                state = menuWindowState,
                title = "CrossPaste Menu",
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

                    (appWindowManager as WinAppWindowManager).initMenuHWND()

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
        val appWindowManager = koinInject<DesktopAppWindowManager>()

        CrossPasteTheme {
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
                        .clip(RoundedCornerShape(5.dp))
                        .fillMaxSize()
                        .padding(10.dp, 0.dp, 10.dp, 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .shadow(5.dp, RoundedCornerShape(5.dp), false)
                            .fillMaxSize()
                            .border(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.12f), RoundedCornerShape(5.dp))
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
                    HomeMenuView(
                        openMainWindow = {
                            mainCoroutineDispatcher.launch(CoroutineName("Open Menu")) {
                                appWindowManager.activeMainWindow()
                            }
                        },
                        close = { hideMenu() },
                    )
                }
            }
        }
    }

    fun refreshWindowPosition(
        appWindowManager: DesktopAppWindowManager,
        event: MouseEvent?,
        eventAction: (MouseEvent, GraphicsDevice, Insets) -> Unit,
    ) {
        val gd = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
        val insets = Toolkit.getDefaultToolkit().getScreenInsets(gd.defaultConfiguration)
        event?.let { eventAction(it, gd, insets) }

        val bounds = gd.defaultConfiguration.bounds
        val usableWidth = bounds.width - insets.right
        val usableHeight = bounds.height - insets.bottom

        val windowWidth = appWindowManager.mainWindowState.size.width
        val windowHeight = appWindowManager.mainWindowState.size.height

        appWindowManager.mainWindowState.position =
            WindowPosition.Absolute(
                x = usableWidth.dp - windowWidth + 8.dp,
                y = usableHeight.dp - windowHeight + 8.dp,
            )
        logger.debug { "main position: ${appWindowManager.mainWindowState.position}" }
    }

    class WindowsTrayMouseClicked(
        private val appWindowManager: DesktopAppWindowManager,
        private val mouseClickedAction: (MouseEvent, GraphicsDevice, Insets) -> Unit,
    ) : MouseAdapter() {

        override fun mouseClicked(e: MouseEvent) {
            refreshWindowPosition(appWindowManager, e, mouseClickedAction)
        }
    }
}
