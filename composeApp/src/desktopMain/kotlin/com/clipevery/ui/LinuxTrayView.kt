package com.clipevery.ui

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import com.clipevery.app.AppWindowManager
import com.clipevery.utils.GlobalCoroutineScopeImpl.mainCoroutineDispatcher
import com.clipevery.utils.getResourceUtils
import dorkbox.systemTray.MenuItem
import dorkbox.systemTray.SystemTray
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.launch
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
            MenuItem("Open Clipevery") {
                mainCoroutineDispatcher.launch(CoroutineName("Open Clipevery")) {
                    appWindowManager.activeMainWindow()
                }
            },
        )

        systemTray.menu?.add(
            MenuItem("Quit Clipevery") {
                exitApplication()
            },
        )
    }

    fun setWindowPosition(appWindowManager: AppWindowManager) {
        val gd = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
        val bounds = gd.defaultConfiguration.bounds
        val insets = Toolkit.getDefaultToolkit().getScreenInsets(gd.defaultConfiguration)

        val usableWidth = bounds.width - insets.right

        val windowWidth = appWindowManager.mainWindowState.size.width

        appWindowManager.mainWindowState.position =
            WindowPosition.Absolute(
                x = usableWidth.dp - windowWidth,
                y = bounds.y.dp + insets.top.dp + 30.dp,
            )
    }
}
