package com.clipevery.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import com.clipevery.app.AppWindowManager
import com.clipevery.platform.currentPlatform
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.awt.event.MouseAdapter

fun getTrayMouseAdapter(
    appWindowManager: AppWindowManager,
    windowState: WindowState,
    mouseClickedAction: () -> Unit,
): MouseAdapter {
    return if (currentPlatform().isMacos()) {
        MacTrayMouseClicked(appWindowManager, windowState, mouseClickedAction)
    } else {
        WindowsTrayMouseClicked(appWindowManager, windowState, mouseClickedAction)
    }
}

class MacTrayMouseClicked(
    private val appWindowManager: AppWindowManager,
    private val windowState: WindowState,
    private val mouseClickedAction: () -> Unit,
) : MouseAdapter() {

    override fun mouseClicked(e: java.awt.event.MouseEvent) {
        mouseClickedAction()
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

class WindowsTrayMouseClicked(
    private val appWindowManager: AppWindowManager,
    private val windowState: WindowState,
    private val mouseClickedAction: () -> Unit,
) : MouseAdapter() {

    override fun mouseClicked(e: java.awt.event.MouseEvent) {
        mouseClickedAction()

        val gd = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
        val bounds = gd.defaultConfiguration.bounds
        val insets = Toolkit.getDefaultToolkit().getScreenInsets(gd.defaultConfiguration)

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
