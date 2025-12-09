package com.crosspaste.ui.base

import com.crosspaste.app.AppUpdateService
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.app.ExitMode
import com.crosspaste.app.WindowTrigger
import com.crosspaste.i18n.Copywriter
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.About
import com.crosspaste.ui.Devices
import com.crosspaste.ui.Export
import com.crosspaste.ui.Extension
import com.crosspaste.ui.Import
import com.crosspaste.ui.NavigationManager
import com.crosspaste.ui.Route
import com.crosspaste.ui.Settings
import com.crosspaste.ui.ShortcutKeys
import com.crosspaste.ui.tray.NativeTrayManager
import com.crosspaste.utils.GlobalCoroutineScope.mainCoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.launch

class MenuHelper(
    private val appUpdateService: AppUpdateService,
    private val appWindowManager: DesktopAppWindowManager,
    private val copywriter: GlobalCopywriter,
    private val navigationManager: NavigationManager,
    uiSupport: UISupport,
) {

    val about =
        MenuItem(
            title = { copywriter -> copywriter.getText("about") },
            action = {
                mainCoroutineDispatcher.launch(CoroutineName("Open about")) {
                    trayMenuAction(About)
                }
            },
        )

    val checkUpdate =
        MenuItem(
            title = { copywriter -> copywriter.getText("check_for_updates") },
            action = {
                appUpdateService.tryTriggerUpdate()
            },
        )

    val devices =
        MenuItem(
            title = { copywriter -> copywriter.getText("devices") },
            action = {
                mainCoroutineDispatcher.launch(CoroutineName("Open devices")) {
                    trayMenuAction(Devices)
                }
            },
        )

    val export =
        MenuItem(
            title = { copywriter -> copywriter.getText("export") },
            action = {
                mainCoroutineDispatcher.launch(CoroutineName("Export")) {
                    trayMenuAction(Export)
                }
            },
        )

    val extension =
        MenuItem(
            title = { copywriter -> copywriter.getText("extension") },
            action = {
                mainCoroutineDispatcher.launch(CoroutineName("Extension")) {
                    trayMenuAction(Extension)
                }
            },
        )

    val import =
        MenuItem(
            title = { copywriter -> copywriter.getText("import") },
            action = {
                mainCoroutineDispatcher.launch(CoroutineName("Import")) {
                    trayMenuAction(Import)
                }
            },
        )

    val settings =
        MenuItem(
            title = { copywriter -> copywriter.getText("settings") },
            action = {
                mainCoroutineDispatcher.launch(CoroutineName("Open settings")) {
                    trayMenuAction(Settings)
                }
            },
        )

    val shortcutKeys =
        MenuItem(
            title = { copywriter -> copywriter.getText("shortcut_keys") },
            action = {
                mainCoroutineDispatcher.launch(CoroutineName("Open shortcut keys")) {
                    trayMenuAction(ShortcutKeys)
                }
            },
        )

    val faq =
        MenuItem(
            title = { copywriter -> copywriter.getText("faq") },
            action = {
                mainCoroutineDispatcher.launch(CoroutineName("Open FAQ")) {
                    uiSupport.openCrossPasteWebInBrowser("FAQ")
                }
            },
        )

    val menuItems =
        listOf(
            devices,
            settings,
            extension,
            shortcutKeys,
            about,
            checkUpdate,
            faq,
        )

    private fun trayMenuAction(route: Route) {
        navigationManager.navigateAndClearStack(route)
        appWindowManager.showMainWindow(WindowTrigger.MENU)
    }

    fun createMacTrayMenu(
        trayManager: NativeTrayManager,
        applicationExit: (ExitMode) -> Unit,
        update: Boolean = false,
    ) {
        if (!update) {
            for ((index, item) in menuItems.withIndex()) {
                trayManager.addMenuItem(
                    itemId = index,
                    title = item.title(copywriter),
                ) {
                    item.action()
                }
            }
            trayManager.addSeparator()
            trayManager.addMenuItem(
                itemId = menuItems.size + 1,
                title = copywriter.getText("quit"),
            ) {
                applicationExit(ExitMode.EXIT)
            }
        } else {
            for ((index, item) in menuItems.withIndex()) {
                trayManager.updateMenuItem(
                    itemId = index,
                    title = item.title(copywriter),
                )
            }
            trayManager.updateMenuItem(
                itemId = menuItems.size + 1,
                title = copywriter.getText("quit"),
            )
        }
    }
}

data class MenuItem(
    val title: (Copywriter) -> String,
    val action: () -> Unit,
)
