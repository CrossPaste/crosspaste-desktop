package com.crosspaste.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import com.crosspaste.app.AppUpdateService
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.app.ExitMode
import com.crosspaste.app.WindowTrigger
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.platform.macos.MacAppUtils
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.awt.Desktop

@Composable
fun FrameWindowScope.DesktopMenuBar() {
    val appUpdateService = koinInject<AppUpdateService>()
    val appWindowManager = koinInject<DesktopAppWindowManager>()
    val copywriter = koinInject<GlobalCopywriter>()
    val navigateManage = koinInject<NavigationManager>()

    val applicationExit = LocalExitApplication.current

    val scope = rememberCoroutineScope()

    val openAbout: () -> Unit = {
        scope.launch {
            navigateManage.navigateAndClearStack(About)
            appWindowManager.showMainWindow(WindowTrigger.MENU)
        }
    }

    // The application menu's "About <app>" item is owned by AWT. Without a handler
    // it opens the JVM's own about panel (titled "java"), so route it to our About
    // page instead, and retitle it from the same i18n key the Help menu uses so it
    // follows the in-app language rather than the system locale.
    DisposableEffect(Unit) {
        val desktop = Desktop.getDesktop()
        if (desktop.isSupported(Desktop.Action.APP_ABOUT)) {
            desktop.setAboutHandler { openAbout() }
        }
        onDispose {
            if (desktop.isSupported(Desktop.Action.APP_ABOUT)) {
                desktop.setAboutHandler(null)
            }
        }
    }

    val aboutTitle = copywriter.getText("about")
    LaunchedEffect(aboutTitle) {
        MacAppUtils.setAboutMenuItemTitle(aboutTitle)
    }

    MenuBar {
        Menu(copywriter.getText("sync")) {
            Item(copywriter.getText("devices")) {
                scope.launch {
                    navigateManage.navigateAndClearStack(Devices)
                    appWindowManager.showMainWindow(WindowTrigger.MENU)
                }
            }
            Item(copywriter.getText("pairing_code")) {
                scope.launch {
                    navigateManage.navigateAndClearStack(PairingCode)
                    appWindowManager.showMainWindow(WindowTrigger.MENU)
                }
            }
        }
        Menu(copywriter.getText("action")) {
            Item(copywriter.getText("settings")) {
                scope.launch {
                    navigateManage.navigateAndClearStack(Settings)
                    appWindowManager.showMainWindow(WindowTrigger.MENU)
                }
            }
            Item(copywriter.getText("extension")) {
                scope.launch {
                    navigateManage.navigateAndClearStack(Extension)
                    appWindowManager.showMainWindow(WindowTrigger.MENU)
                }
            }
            Item(copywriter.getText("import")) {
                scope.launch {
                    navigateManage.navigateAndClearStack(Import)
                    appWindowManager.showMainWindow(WindowTrigger.MENU)
                }
            }
            Item(copywriter.getText("export")) {
                scope.launch {
                    navigateManage.navigateAndClearStack(Export)
                    appWindowManager.showMainWindow(WindowTrigger.MENU)
                }
            }
        }
        Menu(copywriter.getText("help")) {
            Item(copywriter.getText("shortcut_keys")) {
                scope.launch {
                    navigateManage.navigateAndClearStack(ShortcutKeys)
                    appWindowManager.showMainWindow(WindowTrigger.MENU)
                }
            }
            Item(aboutTitle) { openAbout() }
            Item(copywriter.getText("check_for_updates")) {
                appUpdateService.tryTriggerUpdate()
            }
            Separator()
            Item(copywriter.getText("quit")) {
                applicationExit(ExitMode.EXIT)
            }
        }
    }
}
