package com.crosspaste.ui.tray

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.crosspaste.app.DesktopAppLaunch
import com.crosspaste.app.DesktopAppLaunchState
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.app.generated.resources.Res
import com.crosspaste.app.generated.resources.crosspaste_svg
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.notification.NotificationManager
import com.crosspaste.platform.macos.MacAppUtils
import com.crosspaste.platform.macos.MacAppUtils.useAll
import com.crosspaste.platform.macos.api.WindowInfo
import com.crosspaste.ui.LocalExitApplication
import com.crosspaste.ui.base.DesktopNotificationManager
import com.crosspaste.ui.base.MenuHelper
import com.crosspaste.utils.GlobalCoroutineScope.mainCoroutineDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import java.awt.Color
import java.awt.GraphicsEnvironment
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
        val copywriter = koinInject<GlobalCopywriter>()
        val menuHelper = koinInject<MenuHelper>()
        val notificationManager = koinInject<NotificationManager>() as DesktopNotificationManager

        val trayIcon = painterResource(Res.drawable.crosspaste_svg)

        var menu by remember {
            mutableStateOf(menuHelper.createMacPopupMenu(applicationExit))
        }
        val frame by remember { mutableStateOf(TransparentFrame()) }

        val firstLaunchCompleted by appLaunch.firstLaunchCompleted.collectAsState()
        val showSearchWindow by appWindowManager.showSearchWindow.collectAsState()

        DisposableEffect(copywriter.language()) {
            frame.removeAll()
            menu = menuHelper.createMacPopupMenu(applicationExit)
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
                    if (appLaunchState.firstLaunch && !firstLaunchCompleted) {
                        appWindowManager.showMainWindow()
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
                MacTrayMouseClicked(appLaunchState) { event, windowInfo ->
                    val isCtrlDown = (event.modifiersEx and InputEvent.CTRL_DOWN_MASK) != 0
                    if (event.button == MouseEvent.BUTTON1 && !isCtrlDown) {
                        mainCoroutineDispatcher.launch(CoroutineName("Switch CrossPaste")) {
                            appWindowManager.hideMainWindow()
                            if (showSearchWindow) {
                                appWindowManager.hideSearchWindow()
                            } else {
                                appWindowManager.recordActiveInfoAndShowSearchWindow(
                                    useShortcutKeys = false,
                                )
                            }
                        }
                    } else {
                        mainCoroutineDispatcher.launch(CoroutineName("Hide CrossPaste")) {
                            if (showSearchWindow) {
                                appWindowManager.hideSearchWindow()
                            }
                        }
                        frame.setLocation(
                            windowInfo.x.toInt(),
                            (windowInfo.y + windowInfo.height + 6).toInt(),
                        )
                        frame.isVisible = true
                        menu.show(frame, 0, 0)
                    }
                },
        )
    }

    class MacTrayMouseClicked(
        private val appLaunchState: DesktopAppLaunchState,
        private val mouseClickedAction: (MouseEvent, WindowInfo) -> Unit,
    ) : MouseAdapter() {

        override fun mouseClicked(e: MouseEvent) {
            val windowInfos = MacAppUtils.getTrayWindowInfos(appLaunchState.pid)
            windowInfos.useAll {
                windowInfos.first { it.contains(e.xOnScreen, e.yOnScreen) }.let {
                    mouseClickedAction(e, it)
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
