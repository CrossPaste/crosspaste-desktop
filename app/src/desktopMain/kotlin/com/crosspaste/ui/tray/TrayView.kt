package com.crosspaste.ui.tray

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.window.ApplicationScope
import com.crosspaste.app.AppName
import com.crosspaste.app.AppUpdateService
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.app.ExitMode
import com.crosspaste.app.WindowTrigger
import com.crosspaste.app.WindowsUpdateChannel
import com.crosspaste.app.WindowsZipUpdater
import com.crosspaste.app.generated.resources.Res
import com.crosspaste.app.generated.resources.crosspaste_mac_tray
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.platform.Platform
import com.crosspaste.ui.LocalExitApplication
import com.crosspaste.ui.base.MenuHelper
import com.crosspaste.utils.GlobalCoroutineScope.mainCoroutineDispatcher
import dev.nucleusframework.composenativetray.menu.api.TrayMenuBuilder
import dev.nucleusframework.composenativetray.tray.api.Tray
import dev.nucleusframework.composenativetray.utils.isMenuBarInDarkMode
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject

@Composable
fun ApplicationScope.TrayView(windowIcon: Painter) {
    val appWindowManager = koinInject<DesktopAppWindowManager>()
    val copywriter = koinInject<GlobalCopywriter>()
    val menuHelper = koinInject<MenuHelper>()
    val platform = koinInject<Platform>()
    val appUpdateService = koinInject<AppUpdateService>()
    val windowsZipUpdater = koinInject<WindowsZipUpdater>()

    val applicationExit = LocalExitApplication.current

    val isMacos = remember { platform.isMacos() }
    val isLinux = remember { platform.isLinux() }

    // Windows portable-zip only: an actionable "update to vX.Y.Z" entry.
    val isPortableZip =
        remember { windowsZipUpdater.channel == WindowsUpdateChannel.PORTABLE_ZIP }
    val hasNewVersion by remember { appUpdateService.existNewVersion() }
        .collectAsState(initial = false)
    val lastVersion by appUpdateService.lastVersion.collectAsState()

    val menuContent: TrayMenuBuilder.() -> Unit = {
        if (isPortableZip && hasNewVersion) {
            Item(
                label = copywriter.getText("update_to") + " v${lastVersion ?: ""}",
            ) {
                menuHelper.triggerPortableUpdate()
            }

            Divider()
        }

        if (isLinux) {
            Item(label = copywriter.getText("open_search_window")) {
                mainCoroutineDispatcher.launch {
                    appWindowManager.saveCurrentActiveAppInfo()
                    appWindowManager.showSearchWindow(WindowTrigger.MENU)
                }
            }

            Divider()
        }

        menuHelper.menuItems.forEach { item ->
            Item(label = item.title(copywriter)) {
                item.action()
            }
        }

        Divider()

        Item(label = copywriter.getText("quit")) {
            applicationExit(ExitMode.EXIT)
        }
    }

    if (isMacos) {
        // Monochrome menu bar glyph tinted to the actual menu bar appearance
        // (which follows the wallpaper, not just the system theme) — the
        // equivalent of the NSImage template behavior.
        val menuBarDark = isMenuBarInDarkMode()
        val trayGlyph = painterResource(Res.drawable.crosspaste_mac_tray)
        Tray(
            iconContent = {
                Image(
                    painter = trayGlyph,
                    contentDescription = AppName,
                    colorFilter =
                        ColorFilter.tint(if (menuBarDark) Color.White else Color.Black),
                    modifier = Modifier.fillMaxSize(),
                )
            },
            tooltip = AppName,
            primaryAction = {
                mainCoroutineDispatcher.launch {
                    appWindowManager.hideMainWindow()
                    if (appWindowManager.getCurrentSearchWindowInfo().show) {
                        appWindowManager.hideSearchWindow()
                    } else {
                        appWindowManager.saveCurrentActiveAppInfo()
                        appWindowManager.showSearchWindow(WindowTrigger.TRAY_ICON)
                    }
                }
            },
            menuContent = menuContent,
        )
    } else {
        Tray(
            icon = windowIcon,
            tooltip = AppName,
            primaryAction = {
                mainCoroutineDispatcher.launch {
                    appWindowManager.saveCurrentActiveAppInfo()
                    appWindowManager.showSearchWindow(WindowTrigger.TRAY_ICON)
                }
            },
            menuContent = menuContent,
        )
    }
}
