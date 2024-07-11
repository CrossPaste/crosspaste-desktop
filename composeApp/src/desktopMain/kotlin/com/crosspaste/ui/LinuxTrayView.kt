package com.crosspaste.ui

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import com.crosspaste.app.AppWindowManager
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
        exitApplication: () -> Unit,
    ) {
        val appWindowManager = koinApplication.koin.get<AppWindowManager>()
        val themeDetector = koinApplication.koin.get<ThemeDetector>()

        themeDetector.addListener {
            if (it) {
                systemTray.setImage(DesktopResourceUtils.resourceInputStream("icon/crosspaste.tray.linux.dark.png"))
            } else {
                systemTray.setImage(DesktopResourceUtils.resourceInputStream("icon/crosspaste.tray.linux.light.png"))
            }
        }

        if (themeDetector.isCurrentThemeDark()) {
            systemTray.setImage(DesktopResourceUtils.resourceInputStream("icon/crosspaste.tray.linux.dark.png"))
        } else {
            systemTray.setImage(DesktopResourceUtils.resourceInputStream("icon/crosspaste.tray.linux.light.png"))
        }

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
