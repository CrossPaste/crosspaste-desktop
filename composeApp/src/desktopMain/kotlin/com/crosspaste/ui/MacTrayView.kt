package com.crosspaste.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import com.crosspaste.LocalExitApplication
import com.crosspaste.LocalPageViewContent
import com.crosspaste.app.AppLaunchState
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.app.ExitMode
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.platform.macos.MacAppUtils
import com.crosspaste.platform.macos.MacAppUtils.useAll
import com.crosspaste.platform.macos.api.WindowInfo
import com.crosspaste.ui.base.DesktopNotificationManager
import com.crosspaste.ui.base.NotificationManager
import com.crosspaste.ui.base.UISupport
import com.crosspaste.utils.GlobalCoroutineScopeImpl.mainCoroutineDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
        val pageViewContext = LocalPageViewContent.current
        val applicationExit = LocalExitApplication.current

        val appLaunchState = koinInject<AppLaunchState>()
        val appWindowManager = koinInject<DesktopAppWindowManager>()
        val notificationManager = koinInject<NotificationManager>() as DesktopNotificationManager
        val copywriter = koinInject<GlobalCopywriter>()
        val uiSupport = koinInject<UISupport>()

        val trayIcon = painterResource("icon/crosspaste.tray.mac.png")

        var menu by remember {
            mutableStateOf(
                createPopupMenu(
                    appWindowManager,
                    copywriter,
                    uiSupport,
                    pageViewContext,
                    applicationExit,
                ),
            )
        }
        val frame by remember { mutableStateOf(TransparentFrame()) }

        DisposableEffect(copywriter.language()) {
            frame.removeAll()
            menu = createPopupMenu(appWindowManager, copywriter, uiSupport, pageViewContext, applicationExit)
            frame.add(menu)
            onDispose {
                frame.dispose()
            }
        }

        LaunchedEffect(Unit) {
            if (appLaunchState.firstLaunch && !appWindowManager.hasCompletedFirstLaunchShow) {
                delay(1000)
                val windowInfos = MacAppUtils.getTrayWindowInfos(appLaunchState.pid)
                windowInfos.useAll {
                    val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    val screenDevice = ge.defaultScreenDevice

                    (windowInfos.firstOrNull { it.contained(screenDevice) } ?: windowInfos.firstOrNull())?.let {
                        logger.debug { "windowInfo: $it" }
                        appWindowManager.mainWindowState.position =
                            WindowPosition.Absolute(
                                x = it.x.dp + (it.width.dp / 2) - (appWindowManager.mainWindowState.size.width / 2),
                                y = it.y.dp + 30.dp,
                            )
                        logger.debug { "main position: ${appWindowManager.mainWindowState.position}" }
                        appWindowManager.showMainWindow = true
                        appWindowManager.hasCompletedFirstLaunchShow = true
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
                            if (appWindowManager.showMainWindow) {
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

    fun createPopupMenu(
        appWindowManager: DesktopAppWindowManager,
        copywriter: GlobalCopywriter,
        uiSupport: UISupport,
        currentPage: MutableState<PageViewContext>,
        applicationExit: (ExitMode) -> Unit,
    ): PopupMenu {
        val popup = PopupMenu()

        popup.add(
            createMenuItem(copywriter.getText("settings")) {
                mainCoroutineDispatcher.launch(CoroutineName("Open settings")) {
                    appWindowManager.activeMainWindow()
                    currentPage.value = PageViewContext(PageViewType.SETTINGS, currentPage.value)
                }
            },
        )

        popup.add(
            createMenuItem(copywriter.getText("shortcut_keys")) {
                mainCoroutineDispatcher.launch(CoroutineName("Open shortcut keys")) {
                    appWindowManager.activeMainWindow()
                    currentPage.value = PageViewContext(PageViewType.SHORTCUT_KEYS, currentPage.value)
                }
            },
        )

        popup.add(
            createMenuItem(copywriter.getText("about")) {
                mainCoroutineDispatcher.launch(CoroutineName("Open about")) {
                    appWindowManager.activeMainWindow()
                    currentPage.value = PageViewContext(PageViewType.ABOUT, currentPage.value)
                }
            },
        )

        popup.add(
            createMenuItem(copywriter.getText("fqa")) {
                uiSupport.openUrlInBrowser("https://www.crosspaste.com/FQA")
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

    fun createMenuItem(
        text: String,
        action: () -> Unit,
    ): MenuItem {
        val menuItem = MenuItem(text)
        menuItem.addActionListener {
            action()
        }
        return menuItem
    }

    class MacTrayMouseClicked(
        private val appWindowManager: DesktopAppWindowManager,
        private val appLaunchState: AppLaunchState,
        private val mouseClickedAction: (MouseEvent, WindowInfo) -> Unit,
    ) : MouseAdapter() {

        override fun mouseClicked(e: MouseEvent) {
            val windowInfos = MacAppUtils.getTrayWindowInfos(appLaunchState.pid)
            windowInfos.useAll {
                windowInfos.first { it.contains(e.xOnScreen, e.yOnScreen) }.let {
                    mouseClickedAction(e, it)
                    appWindowManager.mainWindowState.position =
                        WindowPosition.Absolute(
                            x = it.x.dp + (it.width.dp / 2) - (appWindowManager.mainWindowState.size.width / 2),
                            y = it.y.dp + 30.dp,
                        )
                    logger.debug { "main position: ${appWindowManager.mainWindowState.position}" }
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
