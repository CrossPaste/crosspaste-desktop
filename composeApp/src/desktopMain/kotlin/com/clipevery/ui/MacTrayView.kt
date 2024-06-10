package com.clipevery.ui

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
import androidx.compose.ui.window.WindowState
import com.clipevery.LocalExitApplication
import com.clipevery.LocalKoinApplication
import com.clipevery.LocalPageViewContent
import com.clipevery.app.AppWindowManager
import com.clipevery.i18n.GlobalCopywriter
import com.clipevery.ui.base.NotificationManager
import com.clipevery.ui.base.UISupport
import org.koin.core.KoinApplication
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.awt.Insets
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.Toolkit
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JFrame

@Composable
fun MacTray(windowState: WindowState) {
    val current = LocalKoinApplication.current
    val pageViewContext = LocalPageViewContent.current
    val applicationExit = LocalExitApplication.current
    val density = LocalDensity.current

    val appWindowManager = current.koin.get<AppWindowManager>()
    val notificationManager = current.koin.get<NotificationManager>()
    val copywriter = current.koin.get<GlobalCopywriter>()

    val trayIcon = painterResource("icon/clipevery.tray.mac.png")

    var menu by remember { mutableStateOf(createPopupMenu(current, pageViewContext, applicationExit)) }
    val frame by remember { mutableStateOf(createFrame()) }

    LaunchedEffect(copywriter.language()) {
        frame.removeAll()
        menu = createPopupMenu(current, pageViewContext, applicationExit)
        frame.add(menu)
    }

    ClipeveryTray(
        icon = trayIcon,
        state = remember { notificationManager.trayState },
        tooltip = "Clipevery",
        mouseListener =
            MacTrayMouseClicked(appWindowManager, windowState) { event, insets ->
                if (event.button == MouseEvent.BUTTON1) {
                    appWindowManager.switchMainWindow()
                } else {
                    if (appWindowManager.showMainWindow) {
                        appWindowManager.unActiveMainWindow()
                    }

                    val stepWidth = with(density) { 32.dp.roundToPx() }
                    val position: Int = ((event.x) / stepWidth) * stepWidth
                    if (event.x - position > stepWidth / 2) {
                        menu.show(frame, position - insets.left, 5)
                    } else {
                        menu.show(frame, position - (stepWidth / 2) - insets.left, 5)
                    }
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
        createMenuItem(copywriter.getText("Settings")) {
            appWindowManager.activeMainWindow()
            currentPage.value = PageViewContext(PageViewType.SETTINGS, currentPage.value)
        },
    )

    popup.add(
        createMenuItem(copywriter.getText("Shortcut_Keys")) {
            appWindowManager.activeMainWindow()
            currentPage.value = PageViewContext(PageViewType.SHORTCUT_KEYS, currentPage.value)
        },
    )

    popup.add(
        createMenuItem(copywriter.getText("About")) {
            appWindowManager.activeMainWindow()
            currentPage.value = PageViewContext(PageViewType.ABOUT, currentPage.value)
        },
    )

    popup.add(
        createMenuItem(copywriter.getText("FQA")) {
            uiSupport.openUrlInBrowser("https://www.clipevery.com/FQA")
        },
    )

    popup.addSeparator()

    popup.add(
        createMenuItem(copywriter.getText("Quit")) {
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
    private val windowState: WindowState,
    private val mouseClickedAction: (MouseEvent, Insets) -> Unit,
) : MouseAdapter() {

    override fun mouseClicked(e: MouseEvent) {
        val gd = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
        val insets = Toolkit.getDefaultToolkit().getScreenInsets(gd.defaultConfiguration)
        mouseClickedAction(e, insets)

        appWindowManager.mainWindowPosition =
            WindowPosition.Absolute(
                x = calculatePosition(e.x.dp, windowState.size.width),
                y = 30.dp,
            )
        windowState.position = appWindowManager.mainWindowPosition
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
