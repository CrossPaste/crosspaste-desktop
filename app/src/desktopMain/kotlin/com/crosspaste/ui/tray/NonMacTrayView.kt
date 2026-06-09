package com.crosspaste.ui.tray

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.window.ApplicationScope
import com.crosspaste.app.AppName
import com.crosspaste.app.AppUpdateService
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.app.ExitMode
import com.crosspaste.app.WindowTrigger
import com.crosspaste.app.WindowsUpdateChannel
import com.crosspaste.app.WindowsZipUpdater
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.platform.Platform
import com.crosspaste.ui.LocalExitApplication
import com.crosspaste.ui.base.MenuHelper
import com.crosspaste.utils.GlobalCoroutineScope.mainCoroutineDispatcher
import com.kdroid.composetray.tray.api.Tray
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun ApplicationScope.NonMacTrayView(windowIcon: Painter) {
    val appWindowManager = koinInject<DesktopAppWindowManager>()
    val copywriter = koinInject<GlobalCopywriter>()
    val menuHelper = koinInject<MenuHelper>()
    val platform = koinInject<Platform>()
    val appUpdateService = koinInject<AppUpdateService>()
    val windowsZipUpdater = koinInject<WindowsZipUpdater>()

    val applicationExit = LocalExitApplication.current

    val isLinux = remember { platform.isLinux() }

    // Windows portable-zip only: an actionable "update to vX.Y.Z" entry.
    val isPortableZip =
        remember { windowsZipUpdater.channel == WindowsUpdateChannel.PORTABLE_ZIP }
    val hasNewVersion by remember { appUpdateService.existNewVersion() }
        .collectAsState(initial = false)
    val lastVersion by appUpdateService.lastVersion.collectAsState()

    Tray(
        icon = windowIcon,
        tooltip = AppName,
        primaryAction = {
            mainCoroutineDispatcher.launch {
                appWindowManager.saveCurrentActiveAppInfo()
                appWindowManager.showSearchWindow(WindowTrigger.TRAY_ICON)
            }
        },
    ) {
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
}
