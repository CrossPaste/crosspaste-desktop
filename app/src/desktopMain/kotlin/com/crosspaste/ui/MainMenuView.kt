package com.crosspaste.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.currentBackStackEntryAsState
import com.crosspaste.app.AppUpdateService
import com.crosspaste.app.DesktopAppLaunch
import com.crosspaste.app.ExitMode
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.base.TutorialButton
import com.crosspaste.ui.theme.AppUISize.large2X
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.small2X
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny4X
import org.koin.compose.koinInject

@Composable
fun MainMenuView() {
    val appLaunch = koinInject<DesktopAppLaunch>()
    val appUpdateService = koinInject<AppUpdateService>()
    val configManager = koinInject<DesktopConfigManager>()
    val navigateManage = koinInject<NavigationManager>()

    val prevMenuList =
        remember {
            listOf(
                MainMenuItem("devices", Devices),
                MainMenuItem("pairing_code", PairingCode),
                MainMenuItem("settings", Settings),
                MainMenuItem("extension", Extension),
            )
        }

    val nextMenuList =
        remember {
            listOf(
                MainMenuItem("import", Import),
                MainMenuItem("export", Export),
                MainMenuItem("shortcut_keys", ShortcutKeys),
                MainMenuItem("share", Share),
                MainMenuItem("about", About),
            )
        }

    val config by configManager.config.collectAsState()
    val firstLaunchCompleted by appLaunch.firstLaunchCompleted.collectAsState()
    val navController = LocalNavHostController.current
    val backStackEntry by navController.currentBackStackEntryAsState()
    val rootRouteName = backStackEntry?.let { getRootRouteName(it.destination) }
    val exitApplication = LocalExitApplication.current

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(bottom = medium),
    ) {
        prevMenuList.forEach { item ->
            MainMenuItemView(
                title = item.title,
                selected = rootRouteName == item.route.name,
                onClick = { navigateManage.navigateAndClearStack(item.route) },
            )
        }

        Spacer(Modifier.weight(1f))

        if (firstLaunchCompleted && config.showTutorial) {
            Box(modifier = Modifier.padding(horizontal = medium, vertical = tiny)) {
                TutorialButton()
            }
        }

        nextMenuList.forEach { item ->
            MainMenuItemView(
                title = item.title,
                selected = rootRouteName == item.route.name,
                onClick = { navigateManage.navigateAndClearStack(item.route) },
            )
        }

        MainMenuItemView("check_for_updates", false) {
            appUpdateService.tryTriggerUpdate()
        }

        MainMenuItemView("quit", false) {
            exitApplication(ExitMode.EXIT)
        }
    }
}

@Composable
fun MainMenuItemView(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val copywriter = koinInject<GlobalCopywriter>()

    val containerColor =
        if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        }

    val contentColor =
        if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(large2X * 2)
                .padding(horizontal = tiny, vertical = tiny4X)
                .clip(RoundedCornerShape(tiny))
                .background(containerColor)
                .clickable(onClick = onClick)
                .padding(horizontal = small2X),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = copywriter.getText(title),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = contentColor,
            style =
                MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    // Adjusting line height and letter spacing for desktop clarity
                    letterSpacing = 0.2.sp,
                ),
        )
    }
}

data class MainMenuItem(
    val title: String,
    val route: Route,
)
