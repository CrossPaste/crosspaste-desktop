package com.crosspaste.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import com.crosspaste.LocalExitApplication
import com.crosspaste.LocalKoinApplication
import com.crosspaste.LocalPageViewContent
import com.crosspaste.app.AppLaunchState
import com.crosspaste.app.AppWindowManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.os.macos.MacAppUtils
import com.crosspaste.os.macos.MacAppUtils.useAll
import com.crosspaste.os.macos.api.WindowInfo
import com.crosspaste.ui.base.DesktopNotificationManager
import com.crosspaste.ui.base.NotificationManager
import com.crosspaste.ui.base.UISupport
import com.crosspaste.utils.GlobalCoroutineScopeImpl.mainCoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.launch
import org.koin.core.KoinApplication
import java.awt.Color
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.event.InputEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JFrame

@Composable
fun MacTray() {
    val current = LocalKoinApplication.current
    val pageViewContext = LocalPageViewContent.current
    val applicationExit = LocalExitApplication.current

    val appWindowManager = current.koin.get<AppWindowManager>()
    val notificationManager = current.koin.get<NotificationManager>() as DesktopNotificationManager
    val copywriter = current.koin.get<GlobalCopywriter>()
    val appLaunchState = current.koin.get<AppLaunchState>()

    val trayIcon = painterResource("icon/crosspaste.tray.mac.png")

    var menu by remember { mutableStateOf(createPopupMenu(current, pageViewContext, applicationExit)) }
    val frame by remember { mutableStateOf(TransparentFrame()) }

    DisposableEffect(copywriter.language()) {
        frame.removeAll()
        menu = createPopupMenu(current, pageViewContext, applicationExit)
        frame.add(menu)
        onDispose {
            frame.dispose()
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
    koinApplication: KoinApplication,
    currentPage: MutableState<PageViewContext>,
    applicationExit: () -> Unit,
): PopupMenu {
    val copywriter = koinApplication.koin.get<GlobalCopywriter>()
    val appWindowManager = koinApplication.koin.get<AppWindowManager>()
    val uiSupport = koinApplication.koin.get<UISupport>()

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
            applicationExit()
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
    private val appWindowManager: AppWindowManager,
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
