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
import androidx.navigation.NavController
import com.crosspaste.app.AppUpdateService
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.app.ExitMode
import com.crosspaste.i18n.Copywriter
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.About
import com.crosspaste.ui.Devices
import com.crosspaste.ui.Export
import com.crosspaste.ui.Import
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
    private val copywriter: GlobalCopywriter,
    private val appWindowManager: DesktopAppWindowManager,
    uiSupport: UISupport,
) {

    val about =
        MenuItem(
            title = { copywriter -> copywriter.getText("about") },
            action = { navController ->
                mainCoroutineDispatcher.launch(CoroutineName("Open about")) {
                    navController.navigate(About)
                    appWindowManager.recordActiveInfoAndShowMainWindow(false)
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
            action = { navController ->
                mainCoroutineDispatcher.launch(CoroutineName("Open devices")) {
                    navController.navigate(Devices)
                    appWindowManager.recordActiveInfoAndShowMainWindow(false)
                }
            },
        )

    val export =
        MenuItem(
            title = { copywriter -> copywriter.getText("export") },
            action = { navController ->
                mainCoroutineDispatcher.launch(CoroutineName("Export")) {
                    navController.navigate(Export)
                    appWindowManager.recordActiveInfoAndShowMainWindow(false)
                }
            },
        )

    val import =
        MenuItem(
            title = { copywriter -> copywriter.getText("import") },
            action = { navController ->
                mainCoroutineDispatcher.launch(CoroutineName("Import")) {
                    navController.navigate(Import)
                    appWindowManager.recordActiveInfoAndShowMainWindow(false)
                }
            },
        )

    val settings =
        MenuItem(
            title = { copywriter -> copywriter.getText("settings") },
            action = { navController ->
                mainCoroutineDispatcher.launch(CoroutineName("Open settings")) {
                    navController.navigate(Settings)
                    appWindowManager.recordActiveInfoAndShowMainWindow(false)
                }
            },
        )

    val shortcutKeys =
        MenuItem(
            title = { copywriter -> copywriter.getText("shortcut_keys") },
            action = { navController ->
                mainCoroutineDispatcher.launch(CoroutineName("Open shortcut keys")) {
                    navController.navigate(ShortcutKeys)
                    appWindowManager.recordActiveInfoAndShowMainWindow(false)
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
            shortcutKeys,
            about,
            checkUpdate,
            faq,
        )

    fun createLinuxTrayMenu(
        applicationExit: (ExitMode) -> Unit,
        navController: NavController,
    ): List<dorkbox.systemTray.MenuItem> {
        val openSearchWindow =
            dorkbox.systemTray.MenuItem(
                copywriter.getText("open_search_window"),
            ) {
                mainCoroutineDispatcher.launch(CoroutineName("Open search window")) {
                    delay(200) // wait for force to prev window
                    appWindowManager.recordActiveInfoAndShowSearchWindow(
                        useShortcutKeys = false,
                    )
                }
            }

        val items =
            listOf(openSearchWindow) +
                menuItems.map { item ->
                    dorkbox.systemTray.MenuItem(
                        item.title(copywriter),
                    ) {
                        item.action(navController)
                    }
                }

        return items +
            dorkbox.systemTray.MenuItem(
                copywriter.getText("quit"),
            ) {
                applicationExit(ExitMode.EXIT)
            }
    }

    fun createMacMenu(
        trayManager: NativeTrayManager,
        applicationExit: (ExitMode) -> Unit,
        navController: NavController,
        update: Boolean = false,
    ) {
        if (!update) {
            for ((index, item) in menuItems.withIndex()) {
                trayManager.addMenuItem(
                    itemId = index,
                    title = item.title(copywriter),
                ) {
                    item.action(navController)
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
        navController: NavController,
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
                            item.action(navController)
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
    val action: (NavController) -> Unit,
)
