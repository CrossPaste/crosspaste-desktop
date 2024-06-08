package com.clipevery.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import com.clipevery.app.AppWindowManager
import com.clipevery.platform.currentPlatform
import com.clipevery.ui.base.NotificationManager
import java.awt.GraphicsEnvironment
import java.awt.Insets
import java.awt.PopupMenu
import java.awt.Toolkit
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

@Composable
fun ApplicationScope.ClipeveryTray(
    notificationManager: NotificationManager,
    appWindowManager: AppWindowManager,
    windowState: WindowState,
) {
    val density = LocalDensity.current

    val trayIcon =
        if (currentPlatform().isMacos()) {
            painterResource("icon/clipevery.tray.mac.png")
        } else {
            painterResource("icon/clipevery.tray.win.png")
        }

    val menu by remember { mutableStateOf(PopupMenu()) }
    val frame by remember { mutableStateOf(createFrame()) }

    val platform = currentPlatform()

    if (platform.isMacos()) {
        Tray(
            state = remember { notificationManager.trayState },
            icon = trayIcon,
            tooltip = "Clipevery",
            mouseListener =
                getTrayMouseAdapter(appWindowManager, windowState) { event, insets ->
                    event.point
                    if (event.button == MouseEvent.BUTTON1) {
                        appWindowManager.switchMainWindow()
                    } else {
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
    } else if (platform.isWindows()) {
        Tray(
            state = remember { notificationManager.trayState },
            icon = trayIcon,
            tooltip = "Clipevery",
            mouseListener =
                getTrayMouseAdapter(appWindowManager, windowState) { event, insets ->
                    event.point
                    if (event.button == MouseEvent.BUTTON1) {
                        appWindowManager.switchMainWindow()
                    }
                },
            menu = {
                Item(
                    "Increment value",
                    onClick = {
                    },
                )
                Item(
                    "Send notification",
                    onClick = {
                    },
                )
                Item(
                    "Exit",
                    onClick = {
                    },
                )
            },
        )
    }
}

fun getTrayMouseAdapter(
    appWindowManager: AppWindowManager,
    windowState: WindowState,
    mouseClickedAction: (MouseEvent, Insets) -> Unit,
): MouseAdapter {
    return if (currentPlatform().isMacos()) {
        MacTrayMouseClicked(appWindowManager, windowState, mouseClickedAction)
    } else {
        WindowsTrayMouseClicked(appWindowManager, windowState, mouseClickedAction)
    }
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
}

class WindowsTrayMouseClicked(
    private val appWindowManager: AppWindowManager,
    private val windowState: WindowState,
    private val mouseClickedAction: (MouseEvent, Insets) -> Unit,
) : MouseAdapter() {

    override fun mouseClicked(e: MouseEvent) {
        val gd = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
        val bounds = gd.defaultConfiguration.bounds
        val insets = Toolkit.getDefaultToolkit().getScreenInsets(gd.defaultConfiguration)
        mouseClickedAction(e, insets)

        val usableWidth = bounds.width - insets.right
        val usableHeight = bounds.height - insets.bottom

        val windowWidth = windowState.size.width
        val windowHeight = windowState.size.height

        appWindowManager.mainWindowPosition =
            WindowPosition.Absolute(
                x = usableWidth.dp - windowWidth + 8.dp,
                y = usableHeight.dp - windowHeight + 8.dp,
            )

        windowState.position = appWindowManager.mainWindowPosition
    }
}

object LinuxTrayWindowState {

    fun setWindowPosition(
        appWindowManager: AppWindowManager,
        windowState: WindowState,
    ) {
        val gd = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
        val bounds = gd.defaultConfiguration.bounds
        val insets = Toolkit.getDefaultToolkit().getScreenInsets(gd.defaultConfiguration)

        val usableWidth = bounds.width - insets.right

        val windowWidth = windowState.size.width

        appWindowManager.mainWindowPosition =
            WindowPosition.Absolute(
                x = usableWidth.dp - windowWidth,
                y = 0.dp,
            )

        windowState.position = appWindowManager.mainWindowPosition
    }
}
