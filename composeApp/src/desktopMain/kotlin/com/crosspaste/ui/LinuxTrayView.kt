package com.crosspaste.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import com.crosspaste.LocalExitApplication
import com.crosspaste.LocalKoinApplication
import com.crosspaste.app.AppLaunchState
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.app.ExitMode
import com.crosspaste.app.logger
import com.crosspaste.utils.DesktopResourceUtils
import com.crosspaste.utils.GlobalCoroutineScopeImpl.mainCoroutineDispatcher
import dorkbox.systemTray.MenuItem
import dorkbox.systemTray.SystemTray
import dorkbox.systemTray.SystemTray.TrayType
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.launch
import java.awt.GraphicsEnvironment
import java.awt.Toolkit

@Composable
fun LinuxTray() {
    val current = LocalKoinApplication.current
    val applicationExit = LocalExitApplication.current
    val appLaunchState = current.koin.get<AppLaunchState>()
    val appWindowManager = current.koin.get<DesktopAppWindowManager>()
    val tray by remember {
        val trayType = getTrayType()
        if (trayType != TrayType.AutoDetect) {
            SystemTray.FORCE_TRAY_TYPE = trayType
        }
        logger.info { "Tray type: $trayType" }
        val innerTray =
            SystemTray.get() ?: run {
                SystemTray.FORCE_TRAY_TYPE = TrayType.AutoDetect
                logger.info { "Tray type fail back : ${TrayType.AutoDetect}" }
                SystemTray.get()
            }
        mutableStateOf(innerTray)
    }

    LaunchedEffect(Unit) {
        tray?.setImage(DesktopResourceUtils.resourceInputStream("icon/crosspaste.png"))
        tray?.setTooltip("CrossPaste")
        tray?.menu?.add(
            MenuItem("Open CrossPaste") {
                mainCoroutineDispatcher.launch(CoroutineName("Open CrossPaste")) {
                    refreshWindowPosition(appWindowManager)
                    appWindowManager.activeMainWindow()
                }
            },
        )

        tray?.menu?.add(
            MenuItem("Quit CrossPaste") {
                applicationExit(ExitMode.EXIT)
            },
        )

        refreshWindowPosition(appWindowManager)

        if (appLaunchState.firstLaunch && !appWindowManager.hasCompletedFirstLaunchShow) {
            appWindowManager.showMainWindow = true
            appWindowManager.hasCompletedFirstLaunchShow = true
        }
    }
}

fun refreshWindowPosition(appWindowManager: DesktopAppWindowManager) {
    val gd = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
    val bounds = gd.defaultConfiguration.bounds
    val insets = Toolkit.getDefaultToolkit().getScreenInsets(gd.defaultConfiguration)

    val windowWidth = appWindowManager.mainWindowState.size.width

    appWindowManager.mainWindowState.position =
        WindowPosition.Absolute(
            x = bounds.x.dp - insets.left.dp - windowWidth,
            y = bounds.y.dp + insets.top.dp,
        )
}

fun getTrayType(): TrayType {
    return System.getProperty("linux.force.trayType")?.let {
        safeFromString(it)
    } ?: TrayType.AutoDetect
}

fun safeFromString(trayName: String): TrayType {
    return try {
        TrayType.valueOf(trayName)
    } catch (e: Exception) {
        TrayType.AutoDetect
    }
}
