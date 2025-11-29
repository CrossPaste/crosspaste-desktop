package com.crosspaste.ui.base

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny2XRoundedCornerShape
import com.crosspaste.ui.tray.NativeTrayManager
import com.crosspaste.utils.GlobalCoroutineScope.mainCoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.delay
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

    fun createLinuxTrayMenu(applicationExit: (ExitMode) -> Unit): List<dorkbox.systemTray.MenuItem> {
        val openSearchWindow =
            dorkbox.systemTray.MenuItem(
                copywriter.getText("open_search_window"),
            ) {
                mainCoroutineDispatcher.launch(CoroutineName("Open search window")) {
                    delay(200) // wait for force to prev window
                    appWindowManager.saveCurrentActiveAppInfo()
                    appWindowManager.showSearchWindow(WindowTrigger.MENU)
                }
            }

        val items =
            listOf(openSearchWindow) +
                menuItems.map { item ->
                    dorkbox.systemTray.MenuItem(
                        item.title(copywriter),
                    ) {
                        item.action()
                    }
                }

        return items +
            dorkbox.systemTray.MenuItem(
                copywriter.getText("quit"),
            ) {
                applicationExit(ExitMode.EXIT)
            }
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

    @Composable
    fun createWindowsMenu(
        applicationExit: (ExitMode) -> Unit,
        closeWindowMenu: () -> Unit,
    ) {
        val existNewVersion by appUpdateService.existNewVersion().collectAsState(false)

        Box(
            modifier =
                Modifier
                    .wrapContentSize()
                    .background(Color.Transparent),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .clip(tiny2XRoundedCornerShape)
                        .background(MaterialTheme.colorScheme.surfaceBright),
            ) {
                menuItems.forEachIndexed { _, item ->
                    MenuItemView(
                        text = item.title(copywriter),
                        background = AppUIColors.menuBackground,
                        extendContent =
                            if (item == checkUpdate) {
                                if (existNewVersion) {
                                    {
                                        Spacer(modifier = Modifier.width(tiny))
                                        NewVersionButton()
                                    }
                                } else {
                                    null
                                }
                            } else {
                                null
                            },
                        onClick = {
                            item.action()
                            closeWindowMenu()
                        },
                    )
                }
                HorizontalDivider()
                MenuItemView(
                    text = copywriter.getText("quit"),
                    background = MaterialTheme.colorScheme.surfaceBright,
                ) {
                    closeWindowMenu()
                    applicationExit(ExitMode.EXIT)
                }
            }
        }
    }
}

data class MenuItem(
    val title: (Copywriter) -> String,
    val action: () -> Unit,
)
