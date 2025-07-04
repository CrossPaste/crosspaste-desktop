package com.crosspaste.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberWindowState
import com.crosspaste.app.AppLaunchState
import com.crosspaste.app.DesktopAppLaunch
import com.crosspaste.app.DesktopAppSize
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.app.WinAppWindowManager
import com.crosspaste.app.generated.resources.Res
import com.crosspaste.app.generated.resources.crosspaste
import com.crosspaste.notification.NotificationManager
import com.crosspaste.ui.base.DesktopNotificationManager
import com.crosspaste.ui.theme.AppUISize.tiny2XRoundedCornerShape
import com.crosspaste.ui.theme.CrossPasteTheme.Theme
import com.crosspaste.utils.GlobalCoroutineScope.mainCoroutineDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
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
        val appLaunch = koinInject<DesktopAppLaunch>()
        val appSize = koinInject<DesktopAppSize>()
        val appLaunchState = koinInject<AppLaunchState>()
        val appWindowManager = koinInject<DesktopAppWindowManager>()
        val notificationManager = koinInject<NotificationManager>() as DesktopNotificationManager

        val trayIcon = painterResource(Res.drawable.crosspaste)

        var showMenu by remember { mutableStateOf(false) }

        val firstLaunchCompleted by appLaunch.firstLaunchCompleted.collectAsState()

        val menuWindowState =
            rememberWindowState(
                placement = WindowPlacement.Floating,
                size = appSize.menuWindowDpSize,
            )

        LaunchedEffect(Unit) {
            delay(1000)
            refreshWindowPosition(appWindowManager, null) { _, _, _ -> }
            if (appLaunchState.firstLaunch && !firstLaunchCompleted) {
                appWindowManager.setShowMainWindow(true)
                appLaunch.setFirstLaunchCompleted(true)
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
                            appWindowManager.switchSearchWindow()
                        }
                    } else {
                        showMenu = true
                        val bounds = gd.defaultConfiguration.bounds
                        val density: Float = gd.displayMode.width.toFloat() / bounds.width
                        menuWindowState.position =
                            WindowPosition(
                                x = ((event.x / density) - insets.left).dp - appSize.menuWindowXOffset,
                                y = (bounds.height - insets.bottom).dp - appSize.menuWindowDpSize.height,
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
        val appSize = koinInject<DesktopAppSize>()
        val appWindowManager = koinInject<DesktopAppWindowManager>()

        Theme {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(appSize.menuRoundedCornerShape)
                        .border(
                            appSize.appBorderSize,
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            tiny2XRoundedCornerShape,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                HomeMenuView(
                    openMainWindow = {
                        mainCoroutineDispatcher.launch(CoroutineName("Open Search Window")) {
                            appWindowManager.activeMainWindow()
                        }
                    },
                    close = { hideMenu() },
                )
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

        val appSize = appWindowManager.appSize

        val windowWidth = appSize.mainWindowSize.width
        val windowHeight = appSize.mainWindowSize.height

        val xOffset = appSize.mainHorizontalShadowPadding - appSize.edgePadding
        val yOffset = appSize.mainBottomShadowPadding - appSize.edgePadding

        val windowPosition =
            WindowPosition.Absolute(
                x = usableWidth.dp - windowWidth + xOffset,
                y = usableHeight.dp - windowHeight + yOffset,
            )

        appWindowManager.setMainWindowState(
            WindowState(
                size = appWindowManager.appSize.mainWindowSize,
                position = windowPosition,
            ),
        )

        logger.debug { "main position: $windowPosition" }
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
