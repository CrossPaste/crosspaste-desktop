package com.crosspaste.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import com.crosspaste.app.DesktopAppLaunch
import com.crosspaste.app.DesktopAppLaunchState
import com.crosspaste.app.DesktopAppSize
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.app.ExitMode
import com.crosspaste.app.generated.resources.Res
import com.crosspaste.app.generated.resources.crosspaste_svg
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.notification.NotificationManager
import com.crosspaste.platform.macos.MacAppUtils
import com.crosspaste.platform.macos.MacAppUtils.useAll
import com.crosspaste.platform.macos.api.WindowInfo
import com.crosspaste.ui.base.DesktopNotificationManager
import com.crosspaste.ui.base.UISupport
import com.crosspaste.utils.GlobalCoroutineScope.mainCoroutineDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import java.awt.Color
import java.awt.GraphicsEnvironment
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.event.InputEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JFrame

object MacTrayView {

    val logger = KotlinLogging.logger {}

    @Composable
    fun Tray() {
        val applicationExit = LocalExitApplication.current

        val appLaunch = koinInject<DesktopAppLaunch>()
        val appLaunchState = koinInject<DesktopAppLaunchState>()
        val appWindowManager = koinInject<DesktopAppWindowManager>()
        val notificationManager = koinInject<NotificationManager>() as DesktopNotificationManager
        val copywriter = koinInject<GlobalCopywriter>()
        val uiSupport = koinInject<UISupport>()

        val trayIcon = painterResource(Res.drawable.crosspaste_svg)

        var menu by remember {
            mutableStateOf(
                createPopupMenu(
                    appWindowManager,
                    copywriter,
                    uiSupport,
                    applicationExit,
                ),
            )
        }
        val frame by remember { mutableStateOf(TransparentFrame()) }

        val firstLaunchCompleted by appLaunch.firstLaunchCompleted.collectAsState()
        val showMainWindow by appWindowManager.showMainWindow.collectAsState()

        DisposableEffect(copywriter.language()) {
            frame.removeAll()
            menu = createPopupMenu(appWindowManager, copywriter, uiSupport, applicationExit)
            frame.add(menu)
            onDispose {
                frame.dispose()
            }
        }

        LaunchedEffect(Unit) {
            // wait for the app to be launched
            delay(1000)
            val windowInfos = MacAppUtils.getTrayWindowInfos(appLaunchState.pid)
            windowInfos.useAll {
                val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
                val screenDevice = ge.defaultScreenDevice

                (windowInfos.firstOrNull { it.contained(screenDevice) } ?: windowInfos.firstOrNull())?.let {
                    logger.debug { "windowInfo: $it" }
                    refreshWindowPosition(appWindowManager, it)
                    if (appLaunchState.firstLaunch && !firstLaunchCompleted) {
                        appWindowManager.setShowMainWindow(true)
                        appLaunch.setFirstLaunchCompleted(true)
                    }
                }
            }
        }

        CrossPasteTray(
            icon = trayIcon,
            state = remember { notificationManager.trayState },
            tooltip = "CrossPaste",
            mouseListener =
                MacTrayMouseClicked(appWindowManager, appLaunchState) { event, windowInfo ->
                    val isCtrlDown = (event.modifiersEx and InputEvent.CTRL_DOWN_MASK) != 0
                    if (event.button == MouseEvent.BUTTON1 && !isCtrlDown) {
                        mainCoroutineDispatcher.launch(CoroutineName("Switch CrossPaste")) {
                            appWindowManager.switchMainWindow()
                        }
                    } else {
                        mainCoroutineDispatcher.launch(CoroutineName("Hide CrossPaste")) {
                            if (showMainWindow) {
                                appWindowManager.unActiveMainWindow()
                            }
                        }
                        frame.setLocation(windowInfo.x.toInt(), (windowInfo.y + windowInfo.height + 6).toInt())
                        frame.isVisible = true
                        menu.show(frame, 0, 0)
                    }
                },
        )
    }

    private fun createPopupMenu(
        appWindowManager: DesktopAppWindowManager,
        copywriter: GlobalCopywriter,
        uiSupport: UISupport,
        applicationExit: (ExitMode) -> Unit,
    ): PopupMenu {
        val popup = PopupMenu()

        popup.add(
            createMenuItem(copywriter.getText("settings")) {
                mainCoroutineDispatcher.launch(CoroutineName("Open settings")) {
                    appWindowManager.activeMainWindow()
                    appWindowManager.toScreen(Settings)
                }
            },
        )

        popup.add(
            createMenuItem(copywriter.getText("shortcut_keys")) {
                mainCoroutineDispatcher.launch(CoroutineName("Open shortcut keys")) {
                    appWindowManager.activeMainWindow()
                    appWindowManager.toScreen(ShortcutKeys)
                }
            },
        )

        popup.add(
            createMenuItem(copywriter.getText("export")) {
                mainCoroutineDispatcher.launch(CoroutineName("Open export")) {
                    appWindowManager.activeMainWindow()
                    appWindowManager.toScreen(Export)
                }
            },
        )

        popup.add(
            createMenuItem(copywriter.getText("import")) {
                mainCoroutineDispatcher.launch(CoroutineName("Open import")) {
                    appWindowManager.activeMainWindow()
                    appWindowManager.toScreen(Import)
                }
            },
        )

        popup.add(
            createMenuItem(copywriter.getText("about")) {
                mainCoroutineDispatcher.launch(CoroutineName("Open about")) {
                    appWindowManager.activeMainWindow()
                    appWindowManager.toScreen(About)
                }
            },
        )

        popup.add(
            createMenuItem(copywriter.getText("faq")) {
                uiSupport.openUrlInBrowser("https://www.crosspaste.com/FAQ")
            },
        )

        popup.addSeparator()

        popup.add(
            createMenuItem(copywriter.getText("quit")) {
                applicationExit(ExitMode.EXIT)
            },
        )
        return popup
    }

    private fun createMenuItem(
        text: String,
        action: () -> Unit,
    ): MenuItem {
        val menuItem = MenuItem(text)
        menuItem.addActionListener {
            action()
        }
        return menuItem
    }

    private fun refreshWindowPosition(
        appWindowManager: DesktopAppWindowManager,
        windowInfo: WindowInfo,
    ) {
        val appSize = appWindowManager.appSize as DesktopAppSize
        val windowPosition =
            WindowPosition.Absolute(
                x = windowInfo.x.dp + (windowInfo.width.dp / 2) - (appSize.mainWindowSize.width / 2),
                y = windowInfo.y.dp + appSize.mainWindowTopMargin,
            )
        appWindowManager.setMainWindowState(
            WindowState(
                size = appWindowManager.appSize.mainWindowSize,
                position = windowPosition,
            ),
        )
        logger.debug { "main position: $windowPosition" }
    }

    class MacTrayMouseClicked(
        private val appWindowManager: DesktopAppWindowManager,
        private val appLaunchState: DesktopAppLaunchState,
        private val mouseClickedAction: (MouseEvent, WindowInfo) -> Unit,
    ) : MouseAdapter() {

        override fun mouseClicked(e: MouseEvent) {
            val windowInfos = MacAppUtils.getTrayWindowInfos(appLaunchState.pid)
            windowInfos.useAll {
                windowInfos.first { it.contains(e.xOnScreen, e.yOnScreen) }.let {
                    mouseClickedAction(e, it)
                    refreshWindowPosition(appWindowManager, it)
                }
            }
        }
    }

    class TransparentFrame : JFrame() {
        init {
            isUndecorated = true
            background = Color(0, 0, 0, 0)
            isVisible = true
            setResizable(false)
        }
    }
}
