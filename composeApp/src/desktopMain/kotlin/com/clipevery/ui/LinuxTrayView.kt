package com.clipevery.ui

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import com.clipevery.app.AppWindowManager
import com.clipevery.utils.getResourceUtils
import dorkbox.systemTray.MenuItem
import dorkbox.systemTray.SystemTray
import org.koin.core.KoinApplication
import java.awt.GraphicsEnvironment
import java.awt.Toolkit

object LinuxTrayView {

    fun initSystemTray(
        systemTray: SystemTray,
        koinApplication: KoinApplication,
        exitApplication: () -> Unit,
    ) {
        val appWindowManager = koinApplication.koin.get<AppWindowManager>()
        val resourceUtils = getResourceUtils()

        systemTray.setImage(resourceUtils.resourceInputStream("icon/clipevery.tray.linux.png"))
        systemTray.setTooltip("Clipevery")
        systemTray.menu?.add(
            MenuItem("Open Clipevery") { appWindowManager.activeMainWindow() },
        )

        systemTray.menu?.add(
            MenuItem("Quit Clipevery") {
                exitApplication()
            },
        )
    }

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
