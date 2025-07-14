package com.crosspaste.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.crosspaste.app.AppLaunchState
import com.crosspaste.app.DesktopAppLaunch
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.app.generated.resources.Res
import com.crosspaste.i18n.GlobalCopywriter
import dorkbox.systemTray.SystemTray
import dorkbox.systemTray.SystemTray.TrayType
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.koin.compose.koinInject
import java.net.URI

object LinuxTrayView {

    val logger = KotlinLogging.logger {}

    private fun createTray(): SystemTray? {
        val trayType = getTrayType()
        if (trayType != TrayType.AutoDetect) {
            SystemTray.FORCE_TRAY_TYPE = trayType
        }
        logger.debug { "Tray type: $trayType" }
        return SystemTray.get() ?: run {
            SystemTray.FORCE_TRAY_TYPE = TrayType.AutoDetect
            logger.debug { "Tray type fail back : ${TrayType.AutoDetect}" }
            SystemTray.get()
        }
    }

    @OptIn(ExperimentalResourceApi::class)
    @Composable
    fun Tray() {
        val applicationExit = LocalExitApplication.current
        val appLaunchState = koinInject<AppLaunchState>()
        val appLaunch = koinInject<DesktopAppLaunch>()
        val appWindowManager = koinInject<DesktopAppWindowManager>()
        val copywriter = koinInject<GlobalCopywriter>()
        val menuHelper = koinInject<MenuHelper>()

        var tray by remember { mutableStateOf<SystemTray?>(null) }

        val firstLaunchCompleted by appLaunch.firstLaunchCompleted.collectAsState()

        LaunchedEffect(copywriter.language()) {
            if (tray == null) {
                tray = createTray()
            } else {
                tray?.remove()
                delay(500) // Wait for the tray to be removed
                tray = createTray()
            }

            tray?.setImage(URI(Res.getUri("drawable/crosspaste.png")).toURL().openStream())
            tray?.setTooltip("CrossPaste")

            tray?.let { linuxTray ->
                for (item in menuHelper.createLinuxTrayMenu(applicationExit)) {
                    linuxTray.menu.add(item)
                }
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                tray?.remove()
            }
        }

        LaunchedEffect(Unit) {
            if (appLaunchState.firstLaunch && !firstLaunchCompleted) {
                appWindowManager.showMainWindow()
                appLaunch.setFirstLaunchCompleted(true)
            }
        }
    }

    private fun getTrayType(): TrayType =
        System.getProperty("linux.force.trayType")?.let {
            safeFromString(it)
        } ?: TrayType.AutoDetect

    private fun safeFromString(trayName: String): TrayType =
        runCatching {
            TrayType.valueOf(trayName)
        }.getOrElse {
            TrayType.AutoDetect
        }
}
