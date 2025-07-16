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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.crosspaste.app.AppLaunchState
import com.crosspaste.app.AppUpdateService
import com.crosspaste.app.DesktopAppLaunch
import com.crosspaste.app.DesktopAppSize
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.app.WinAppWindowManager
import com.crosspaste.app.generated.resources.Res
import com.crosspaste.app.generated.resources.crosspaste
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.notification.NotificationManager
import com.crosspaste.ui.base.DesktopNotificationManager
import com.crosspaste.ui.base.measureTextWidth
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUIFont.getFontWidth
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny2XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.zero
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
        val appWindowManager = koinInject<DesktopAppWindowManager>() as WinAppWindowManager
        val notificationManager = koinInject<NotificationManager>() as DesktopNotificationManager

        val trayIcon = painterResource(Res.drawable.crosspaste)

        var showMenu by remember { mutableStateOf(false) }

        val firstLaunchCompleted by appLaunch.firstLaunchCompleted.collectAsState()
        val menuWidth by appSize.menuWindowWidth.collectAsState()
        val showSearchWindow by appWindowManager.showSearchWindow.collectAsState()

        LaunchedEffect(Unit) {
            delay(1000)
            if (appLaunchState.firstLaunch && !firstLaunchCompleted) {
                appWindowManager.showMainWindow()
                appLaunch.setFirstLaunchCompleted(true)
            }
        }

        val menuWindowState =
            rememberWindowState(
                placement = WindowPlacement.Floating,
                size = DpSize(menuWidth, appSize.getMenuWindowHeigh()),
            )

        CrossPasteTray(
            icon = trayIcon,
            state = remember { notificationManager.trayState },
            tooltip = "CrossPaste",
            mouseListener =
                WindowsTrayMouseClicked { event, gd, insets ->
                    if (event.button == MouseEvent.BUTTON1) {
                        mainCoroutineDispatcher.launch(CoroutineName("Switch CrossPaste")) {
                            appWindowManager.hideMainWindow()
                            if (showSearchWindow) {
                                appWindowManager.hideSearchWindow()
                            } else {
                                appWindowManager.recordActiveInfoAndShowSearchWindow(false)
                            }
                        }
                    } else {
                        showMenu = true
                        val bounds = gd.defaultConfiguration.bounds
                        val density: Float = gd.displayMode.width.toFloat() / bounds.width
                        menuWindowState.position =
                            WindowPosition(
                                x = ((event.x / density) - insets.left).dp - appSize.menuWindowXOffset,
                                y =
                                    (bounds.height - insets.bottom).dp -
                                        appSize.getMenuWindowHeigh() - medium,
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

                    appWindowManager.initMenuHWND()

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
        val appUpdateService = koinInject<AppUpdateService>()
        val copywriter = koinInject<GlobalCopywriter>()
        val menuHelper = koinInject<MenuHelper>()

        val existNewVersion by appUpdateService.existNewVersion().collectAsState(false)

        val applicationExit = LocalExitApplication.current

        val menuTexts = menuHelper.menuItems.map { it.title(copywriter) }

        val newWidth =
            measureTextWidth(
                "new!",
                MaterialTheme.typography.bodySmall
                    .copy(fontStyle = FontStyle.Italic),
            )

        val maxWidth =
            getFontWidth(menuTexts, extendFunction = {
                if (existNewVersion && it == 0) {
                    medium + newWidth
                } else {
                    zero
                }
            })

        LaunchedEffect(maxWidth) {
            appSize.updateMenuWindowWidth(maxWidth)
        }

        Theme {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(appSize.menuRoundedCornerShape)
                        .border(
                            appSize.appBorderSize,
                            AppUIColors.lightBorderColor,
                            tiny2XRoundedCornerShape,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                menuHelper.createWindowsMenu(
                    closeWindowMenu = hideMenu,
                    applicationExit = applicationExit,
                )
            }
        }
    }

    class WindowsTrayMouseClicked(
        private val mouseClickedAction: (MouseEvent, GraphicsDevice, Insets) -> Unit,
    ) : MouseAdapter() {

        override fun mouseClicked(event: MouseEvent) {
            val gd = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
            val insets = Toolkit.getDefaultToolkit().getScreenInsets(gd.defaultConfiguration)
            mouseClickedAction(event, gd, insets)
        }
    }
}
