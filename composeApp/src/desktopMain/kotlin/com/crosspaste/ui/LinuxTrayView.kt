package com.crosspaste.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import com.crosspaste.app.AppLaunchState
import com.crosspaste.app.DesktopAppSize
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.app.ExitMode
import com.crosspaste.utils.DesktopResourceUtils
import com.crosspaste.utils.GlobalCoroutineScopeImpl.mainCoroutineDispatcher
import dorkbox.systemTray.MenuItem
import dorkbox.systemTray.SystemTray
import dorkbox.systemTray.SystemTray.TrayType
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.awt.GraphicsEnvironment
import java.awt.Toolkit

object LinuxTrayView {

    val logger = KotlinLogging.logger {}

    @Composable
    fun Tray() {
        val applicationExit = LocalExitApplication.current
        val appLaunchState = koinInject<AppLaunchState>()
        val appWindowManager = koinInject<DesktopAppWindowManager>()
        val tray by remember {
            val trayType = getTrayType()
            if (trayType != TrayType.AutoDetect) {
                SystemTray.FORCE_TRAY_TYPE = trayType
            }
            logger.debug { "Tray type: $trayType" }
            val innerTray =
                SystemTray.get() ?: run {
                    SystemTray.FORCE_TRAY_TYPE = TrayType.AutoDetect
                    logger.debug { "Tray type fail back : ${TrayType.AutoDetect}" }
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

            if (appLaunchState.firstLaunch && !appWindowManager.getFirstLaunchCompleted()) {
                appWindowManager.setShowMainWindow(true)
                appWindowManager.setFirstLaunchCompleted(true)
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                tray?.remove()
            }
        }
    }

    private fun refreshWindowPosition(appWindowManager: DesktopAppWindowManager) {
        val gd = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
        val bounds = gd.defaultConfiguration.bounds
        val insets = Toolkit.getDefaultToolkit().getScreenInsets(gd.defaultConfiguration)

        val appSize = appWindowManager.appSize as DesktopAppSize

        val windowWidth = appSize.mainWindowSize.width

        val windowPosition =
            WindowPosition.Absolute(
                x = (bounds.x + bounds.width).dp - windowWidth + appSize.mainHorizontalShadowPadding,
                y = (bounds.y + insets.top).dp,
            )

        appWindowManager.setMainWindowState(
            WindowState(
                size = appWindowManager.appSize.mainWindowSize,
                position = windowPosition,
            ),
        )

        logger.debug { "main position: $windowPosition" }
    }

    private fun getTrayType(): TrayType {
        return System.getProperty("linux.force.trayType")?.let {
            safeFromString(it)
        } ?: TrayType.AutoDetect
    }

    private fun safeFromString(trayName: String): TrayType {
        return try {
            TrayType.valueOf(trayName)
        } catch (_: Exception) {
            TrayType.AutoDetect
        }
    }
}
