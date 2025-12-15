package com.crosspaste.ui.tray

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.window.ApplicationScope
import com.crosspaste.app.AppName
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.app.ExitMode
import com.crosspaste.app.WindowTrigger
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

    val applicationExit = LocalExitApplication.current

    val isLinux = remember { platform.isLinux() }

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
