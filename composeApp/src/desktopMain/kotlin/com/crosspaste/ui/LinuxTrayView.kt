package com.crosspaste.ui

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import com.crosspaste.app.AppLaunchState
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.app.ExitMode
import com.crosspaste.utils.DesktopResourceUtils
import com.crosspaste.utils.GlobalCoroutineScopeImpl.mainCoroutineDispatcher
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
        exitApplication: (ExitMode) -> Unit,
    ) {
        val appWindowManager = koinApplication.koin.get<DesktopAppWindowManager>()

        systemTray.setImage(DesktopResourceUtils.resourceInputStream("icon/crosspaste.png"))

        systemTray.setTooltip("CrossPaste")
        systemTray.menu?.add(
            MenuItem("Open CrossPaste") {
                mainCoroutineDispatcher.launch(CoroutineName("Open CrossPaste")) {
                    appWindowManager.activeMainWindow()
                }
            },
        )

        systemTray.menu?.add(
            MenuItem("Quit CrossPaste") {
                exitApplication(ExitMode.EXIT)
            },
        )
    }

    fun setWindowPosition(
        appWindowManager: DesktopAppWindowManager,
        appLaunchState: AppLaunchState,
    ) {
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

        if (appLaunchState.firstLaunch && !appWindowManager.hasCompletedFirstLaunchShow) {
            appWindowManager.showMainWindow = true
            appWindowManager.hasCompletedFirstLaunchShow = true
        }
    }
}
