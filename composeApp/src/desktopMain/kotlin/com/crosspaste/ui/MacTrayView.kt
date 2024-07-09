package com.crosspaste.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.compose.ui.window.WindowPosition
import com.crosspaste.LocalExitApplication
import com.crosspaste.LocalKoinApplication
import com.crosspaste.LocalPageViewContent
import com.crosspaste.app.AppWindowManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.base.NotificationManager
import com.crosspaste.ui.base.UISupport
import com.crosspaste.utils.GlobalCoroutineScopeImpl.mainCoroutineDispatcher
import com.crosspaste.utils.contains
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.launch
import org.koin.core.KoinApplication
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.awt.Insets
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.event.InputEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JFrame

@Composable
fun MacTray() {
    val current = LocalKoinApplication.current
    val pageViewContext = LocalPageViewContent.current
    val applicationExit = LocalExitApplication.current
    val density = LocalDensity.current

    val appWindowManager = current.koin.get<AppWindowManager>()
    val notificationManager = current.koin.get<NotificationManager>()
    val copywriter = current.koin.get<GlobalCopywriter>()

    val trayIcon = painterResource("icon/crosspaste.tray.mac.png")

    var menu by remember { mutableStateOf(createPopupMenu(current, pageViewContext, applicationExit)) }
    val frame by remember { mutableStateOf(createFrame()) }

    LaunchedEffect(copywriter.language()) {
        frame.removeAll()
        menu = createPopupMenu(current, pageViewContext, applicationExit)
        frame.add(menu)
    }

    CrossPasteTray(
        icon = trayIcon,
        state = remember { notificationManager.trayState },
        tooltip = "CrossPaste",
        mouseListener =
            MacTrayMouseClicked(appWindowManager) { event, rectangle, _ ->
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
                    val stepWidth = with(density) { 48.dp.roundToPx() }
                    menu.show(frame, event.x - stepWidth, rectangle.y + 5)
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

fun createFrame(): Frame {
    val frame = JFrame()
    frame.isUndecorated = true
    frame.isVisible = true
    frame.setResizable(false)
    return frame
}

class MacTrayMouseClicked(
    private val appWindowManager: AppWindowManager,
    private val mouseClickedAction: (MouseEvent, Rectangle, Insets) -> Unit,
) : MouseAdapter() {

    override fun mouseClicked(e: MouseEvent) {
        val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
        val bounds = ge.defaultScreenDevice.defaultConfiguration.bounds
        val scDevices = ge.screenDevices

        val clickedDevice =
            scDevices.firstOrNull { device ->
                device.contains(e.point, bounds.x, bounds.y)
            }

        val gd = clickedDevice ?: ge.defaultScreenDevice
        val insets = Toolkit.getDefaultToolkit().getScreenInsets(gd.defaultConfiguration)
        mouseClickedAction(e, gd.defaultConfiguration.bounds, insets)

        appWindowManager.mainWindowState.position =
            WindowPosition.Absolute(
                x = calculatePosition(e.x.dp, appWindowManager.mainWindowState.size.width),
                y = (clickedDevice?.defaultConfiguration?.bounds?.y?.dp ?: 0.dp) + 30.dp,
            )
    }

    private fun calculatePosition(
        x: Dp,
        width: Dp,
    ): Dp {
        val fNum = x / 32.dp
        val iNum = fNum.toInt()
        return if (fNum - iNum < 0.5f) {
            iNum * 32.dp - (width / 2)
        } else {
            (iNum + 1) * 32.dp - (width / 2)
        }
    }
}
