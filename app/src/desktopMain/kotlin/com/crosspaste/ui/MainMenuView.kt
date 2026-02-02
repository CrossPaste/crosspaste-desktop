package com.crosspaste.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.crosspaste.ui.theme.AppUISize.large
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.small2X
import com.crosspaste.ui.theme.AppUISize.small3X
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny3X
import com.crosspaste.ui.theme.AppUISize.tiny4X
import com.crosspaste.ui.theme.AppUISize.tiny5X
import com.crosspaste.ui.theme.AppUISize.tinyRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.xxxLarge
import org.koin.compose.koinInject

@Composable
fun MainMenuView() {
    val appLaunch = koinInject<DesktopAppLaunch>()
    val appUpdateService = koinInject<AppUpdateService>()
    val configManager = koinInject<DesktopConfigManager>()
    val navigateManage = koinInject<NavigationManager>()

    val primaryMenuList =
        remember {
            listOf(
                MainMenuItem("devices", Devices, Icons.Outlined.Devices),
                MainMenuItem("pairing_code", PairingCode, Icons.Outlined.VpnKey),
                MainMenuItem("settings", Settings, Icons.Outlined.Settings),
                MainMenuItem("extension", Extension, Icons.Outlined.Extension),
            )
        }

    val secondaryMenuList =
        remember {
            listOf(
                MainMenuItem("import", Import, Icons.Outlined.Download),
                MainMenuItem("export", Export, Icons.Outlined.Upload),
                MainMenuItem("shortcut_keys", ShortcutKeys, Icons.Outlined.Keyboard),
                MainMenuItem("share", Share, Icons.Outlined.Share),
                MainMenuItem("about", About, Icons.Outlined.Info),
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
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = tiny),
            verticalArrangement = Arrangement.spacedBy(tiny4X),
        ) {
            primaryMenuList.forEach { item ->
                MainMenuItemView(
                    title = item.title,
                    icon = item.icon,
                    selected = rootRouteName == item.route.name,
                    onClick = { navigateManage.navigateAndClearStack(item.route) },
                )
            }
        }

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = tiny, end = tiny),
            verticalArrangement = Arrangement.spacedBy(tiny4X),
        ) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = tiny3X),
                thickness = tiny5X,
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            Spacer(modifier = Modifier.height(tiny3X))

            if (firstLaunchCompleted && config.showTutorial) {
                Box(modifier = Modifier.padding(horizontal = tiny, vertical = tiny3X)) {
                    TutorialButton()
                }
            }

            secondaryMenuList.forEach { item ->
                MainMenuItemView(
                    title = item.title,
                    icon = item.icon,
                    selected = rootRouteName == item.route.name,
                    onClick = { navigateManage.navigateAndClearStack(item.route) },
                    compact = true,
                )
            }

            MainMenuItemView(
                title = "check_for_updates",
                icon = Icons.Outlined.Refresh,
                selected = false,
                onClick = { appUpdateService.tryTriggerUpdate() },
                compact = true,
            )

            MainMenuItemView(
                title = "quit",
                icon = Icons.AutoMirrored.Outlined.ExitToApp,
                selected = false,
                onClick = { exitApplication(ExitMode.EXIT) },
                compact = true,
                tintColor = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
fun MainMenuItemView(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    compact: Boolean = false,
    tintColor: Color? = null,
) {
    val copywriter = koinInject<GlobalCopywriter>()

    val containerColor =
        if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        }

    val contentColor =
        tintColor
            ?: if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }

    val itemHeight = if (compact) (xxxLarge - tiny4X) else xxxLarge
    val iconSize = if (compact) medium else large

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .clip(tinyRoundedCornerShape)
                .background(containerColor)
                .clickable(onClick = onClick)
                .padding(horizontal = small2X),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(small3X),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = contentColor,
        )
        Text(
            text = copywriter.getText(title),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = contentColor,
            style =
                MaterialTheme.typography.bodyMedium.copy(
                    fontSize = if (compact) 13.sp else 14.sp,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    letterSpacing = 0.2.sp,
                ),
        )
    }
}

data class MainMenuItem(
    val title: String,
    val route: Route,
    val icon: ImageVector,
)
