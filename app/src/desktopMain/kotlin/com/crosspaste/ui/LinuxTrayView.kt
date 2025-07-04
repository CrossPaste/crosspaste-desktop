package com.crosspaste.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import com.crosspaste.app.AppLaunchState
import com.crosspaste.app.DesktopAppLaunch
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.app.generated.resources.Res
import com.crosspaste.i18n.GlobalCopywriter
import dorkbox.systemTray.SystemTray
import dorkbox.systemTray.SystemTray.TrayType
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.koin.compose.koinInject
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.net.URI

object LinuxTrayView {

    val logger = KotlinLogging.logger {}

    @OptIn(ExperimentalResourceApi::class)
    @Composable
    fun Tray() {
        val applicationExit = LocalExitApplication.current
        val appLaunchState = koinInject<AppLaunchState>()
        val appLaunch = koinInject<DesktopAppLaunch>()
        val appWindowManager = koinInject<DesktopAppWindowManager>()
        val copywriter = koinInject<GlobalCopywriter>()
        val menuHelper = koinInject<MenuHelper>()

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

        val firstLaunchCompleted by appLaunch.firstLaunchCompleted.collectAsState()

        DisposableEffect(copywriter.language()) {
            if (tray != null) {
                if (tray.menu.entries.isNotEmpty()) {
                    tray.menu.remove()
                }
                for (item in menuHelper.createLinuxTrayMenu(applicationExit)) {
                    tray.menu.add(item)
                }
            }

            onDispose {
                tray?.remove()
            }
        }

        LaunchedEffect(Unit) {
            tray?.setImage(URI(Res.getUri("drawable/crosspaste.png")).toURL().openStream())
            tray?.setTooltip("CrossPaste")

            refreshWindowPosition(appWindowManager)

            if (appLaunchState.firstLaunch && !firstLaunchCompleted) {
                appWindowManager.setShowMainWindow(true)
                appLaunch.setFirstLaunchCompleted(true)
            }
        }
    }

    private fun refreshWindowPosition(appWindowManager: DesktopAppWindowManager) {
        val gd = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
        val bounds = gd.defaultConfiguration.bounds
        val insets = Toolkit.getDefaultToolkit().getScreenInsets(gd.defaultConfiguration)

        val appSize = appWindowManager.appSize

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
        return runCatching {
            TrayType.valueOf(trayName)
        }.getOrElse {
            TrayType.AutoDetect
        }
    }
}
