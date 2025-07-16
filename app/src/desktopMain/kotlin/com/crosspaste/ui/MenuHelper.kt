package com.crosspaste.ui

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
import com.crosspaste.i18n.Copywriter
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.base.MenuItemView
import com.crosspaste.ui.base.NewVersionButton
import com.crosspaste.ui.base.UISupport
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny2XRoundedCornerShape
import com.crosspaste.utils.GlobalCoroutineScope.mainCoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.PopupMenu

class MenuHelper(
    private val appUpdateService: AppUpdateService,
    private val copywriter: GlobalCopywriter,
    private val appWindowManager: DesktopAppWindowManager,
    uiSupport: UISupport,
) {

    val about =
        MenuItem(
            title = { copywriter -> copywriter.getText("about") },
            action = {
                mainCoroutineDispatcher.launch(CoroutineName("Open about")) {
                    appWindowManager.toScreen(About)
                    appWindowManager.recordActiveInfoAndShowMainWindow(false)
                }
            },
        )

    val checkUpdate =
        MenuItem(
            title = { copywriter -> copywriter.getText("check_for_updates") },
            action = {
                mainCoroutineDispatcher.launch(CoroutineName("Check for updates")) {
                    appUpdateService.tryTriggerUpdate()
                }
            },
        )

    val devices =
        MenuItem(
            title = { copywriter -> copywriter.getText("devices") },
            action = {
                mainCoroutineDispatcher.launch(CoroutineName("Open devices")) {
                    appWindowManager.toScreen(Devices)
                    appWindowManager.recordActiveInfoAndShowMainWindow(false)
                }
            },
        )

    val export =
        MenuItem(
            title = { copywriter -> copywriter.getText("export") },
            action = {
                mainCoroutineDispatcher.launch(CoroutineName("Export")) {
                    appWindowManager.toScreen(Export)
                    appWindowManager.recordActiveInfoAndShowMainWindow(false)
                }
            },
        )

    val import =
        MenuItem(
            title = { copywriter -> copywriter.getText("import") },
            action = {
                mainCoroutineDispatcher.launch(CoroutineName("Import")) {
                    appWindowManager.toScreen(Import)
                    appWindowManager.recordActiveInfoAndShowMainWindow(false)
                }
            },
        )

    val settings =
        MenuItem(
            title = { copywriter -> copywriter.getText("settings") },
            action = {
                mainCoroutineDispatcher.launch(CoroutineName("Open settings")) {
                    appWindowManager.toScreen(Settings)
                    appWindowManager.recordActiveInfoAndShowMainWindow(false)
                }
            },
        )

    val shortcutKeys =
        MenuItem(
            title = { copywriter -> copywriter.getText("shortcut_keys") },
            action = {
                mainCoroutineDispatcher.launch(CoroutineName("Open shortcut keys")) {
                    appWindowManager.toScreen(ShortcutKeys)
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

    fun createLinuxTrayMenu(applicationExit: (ExitMode) -> Unit): List<dorkbox.systemTray.MenuItem> {
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

    fun createMacPopupMenu(applicationExit: (ExitMode) -> Unit): PopupMenu {
        val popupMenu = PopupMenu()

        for (item in menuItems) {
            popupMenu.add(
                createMacMenuItem(
                    text = item.title(copywriter),
                    action = item.action,
                ),
            )
        }

        popupMenu.addSeparator()

        popupMenu.add(
            createMacMenuItem(
                text = copywriter.getText("quit"),
                action = { applicationExit(ExitMode.EXIT) },
            ),
        )
        return popupMenu
    }

    private fun createMacMenuItem(
        text: String,
        action: () -> Unit,
    ): java.awt.MenuItem {
        val menuItem = java.awt.MenuItem(text)
        menuItem.addActionListener {
            action()
        }
        return menuItem
    }

    @Composable
    fun createWindowsMenu(
        closeWindowMenu: () -> Unit,
        applicationExit: (ExitMode) -> Unit,
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
                menuItems.forEachIndexed { index, item ->
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
