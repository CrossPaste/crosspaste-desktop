package com.crosspaste.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import androidx.navigation.toRoute
import com.crosspaste.platform.Platform
import com.crosspaste.sync.NearbyDeviceManager
import com.crosspaste.sync.SyncManager
import com.crosspaste.ui.base.ShareContentView
import com.crosspaste.ui.devices.DesktopPromoteGuide
import com.crosspaste.ui.devices.DeviceDetailContentView
import com.crosspaste.ui.devices.DeviceScopeFactory
import com.crosspaste.ui.devices.DevicesContentView
import com.crosspaste.ui.devices.NearbyDeviceDetailContentView
import com.crosspaste.ui.devices.PairingCodeContentView
import com.crosspaste.ui.devices.SyncScopeFactory
import com.crosspaste.ui.devices.TokenView
import com.crosspaste.ui.extension.ExtensionContentView
import com.crosspaste.ui.extension.mcp.McpContentView
import com.crosspaste.ui.extension.ocr.OCRContentView
import com.crosspaste.ui.extension.sourcecontrol.SourceControlContentView
import com.crosspaste.ui.mouse.MouseSettingsScreen
import com.crosspaste.ui.paste.PasteExportContentView
import com.crosspaste.ui.paste.PasteImportContentView
import com.crosspaste.ui.settings.DesktopNetworkSettingsContentView
import com.crosspaste.ui.settings.DesktopPasteboardSettingsContentView
import com.crosspaste.ui.settings.PasteboardSettingsContentView
import com.crosspaste.ui.settings.SettingsContentView
import com.crosspaste.ui.settings.ShortcutKeysContentView
import com.crosspaste.ui.settings.StoragePathManager
import com.crosspaste.ui.settings.StorageSettingsContentView
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.xxLarge
import com.crosspaste.ui.theme.AppUISize.zero
import kotlinx.coroutines.channels.Channel

class DesktopScreenProvider(
    private val platform: Platform,
    private val deviceScopeFactory: DeviceScopeFactory,
    private val syncScopeFactory: SyncScopeFactory,
    private val storagePathManager: StoragePathManager,
    private val syncManager: SyncManager,
    private val nearbyDeviceManager: NearbyDeviceManager,
) : ScreenProvider {

    companion object {
        fun AnimatedContentTransitionScope<NavBackStackEntry>.slideOutRight(): ExitTransition =
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(300),
            )

        fun AnimatedContentTransitionScope<NavBackStackEntry>.slideInLeft(): EnterTransition =
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(300),
            )
    }

    private val navigationEvents = Channel<NavigationEvent>(Channel.UNLIMITED)

    override fun navigate(route: Route) {
        navigationEvents.trySend(Navigate(route))
    }

    override fun navigateAndClearStack(route: Route) {
        navigationEvents.trySend(NavigateAndClearStack(route))
    }

    override fun navigateUp() {
        navigationEvents.trySend(NavigateUp)
    }

    override fun navigateAction(
        event: NavigationEvent,
        navController: NavHostController,
    ) {
        when (event) {
            is Navigate -> {
                navController.navigate(event.route)
            }
            is NavigateAndClearStack -> {
                navController.navigate(event.route) {
                    popUpTo(0) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
            is NavigateUp -> {
                navController.navigateUp()
            }
        }
    }

    @Composable
    override fun screen() {
        val navController = LocalNavHostController.current

        LaunchedEffect(Unit) {
            for (event in navigationEvents) {
                navigateAction(event, navController)
            }
        }

        NavHost(
            navController = navController,
            startDestination = DevicesGraph,
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            composable<About> { AboutScreen() }

            navigation<DevicesGraph>(startDestination = Devices) {
                composable<Devices> {
                    DevicesScreen()
                }
                composable<DeviceDetail>(
                    exitTransition = { slideOutRight() },
                    enterTransition = { slideInLeft() },
                ) { backStackEntry ->
                    backStackEntry.DeviceDetailScreen()
                }
                composable<NearbyDeviceDetail>(
                    exitTransition = { slideOutRight() },
                    enterTransition = { slideInLeft() },
                ) { backStackEntry ->
                    backStackEntry.NearbyDeviceDetailScreen()
                }
            }
            composable<Export> { ExportScreen() }
            navigation<ExtensionGraph>(startDestination = Extension) {
                composable<Extension> { ExtensionScreen() }
                composable<MCP>(
                    exitTransition = { slideOutRight() },
                    enterTransition = { slideInLeft() },
                ) {
                    McpSettingsScreen()
                }
                composable<OCR>(
                    exitTransition = { slideOutRight() },
                    enterTransition = { slideInLeft() },
                ) {
                    OCRSettingsScreen()
                }
                composable<SourceControl>(
                    exitTransition = { slideOutRight() },
                    enterTransition = { slideInLeft() },
                ) {
                    SourceControlSettingsScreen()
                }
            }
            composable<Import> { ImportScreen() }
            composable<PairingCode> { PairingCodeScreen() }
            composable<Share> { ShareScreen() }
            navigation<SettingsGraph>(startDestination = Settings) {
                composable<Settings> { SettingsScreen() }
                composable<PasteboardSettings>(
                    exitTransition = { slideOutRight() },
                    enterTransition = { slideInLeft() },
                ) {
                    PasteboardSettingsScreen()
                }
                composable<NetworkSettings>(
                    exitTransition = { slideOutRight() },
                    enterTransition = { slideInLeft() },
                ) {
                    NetworkSettingsScreen()
                }
                composable<MouseSettings>(
                    exitTransition = { slideOutRight() },
                    enterTransition = { slideInLeft() },
                ) {
                    MouseSettingsScreen()
                }
                composable<StorageSettings>(
                    exitTransition = { slideOutRight() },
                    enterTransition = { slideInLeft() },
                ) {
                    StorageSettingsScreen()
                }
            }
            composable<ShortcutKeys> { ShortcutKeysScreen() }
        }
    }

    @Composable
    override fun ScreenLayout(
        horizontal: Dp,
        top: Dp,
        bottom: Dp,
        content: @Composable BoxScope.() -> Unit,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = horizontal)
                    .padding(top = top, bottom = bottom),
        ) {
            content()
        }
    }

    @Composable
    private fun DesktopScreenLayout(content: @Composable BoxScope.() -> Unit) {
        ScreenLayout(
            horizontal = medium,
            top = zero,
            bottom = medium,
            content = content,
        )
    }

    @Composable
    private fun AboutScreen() {
        DesktopScreenLayout {
            AboutContentView()
        }
    }

    @Composable
    private fun NavBackStackEntry.DeviceDetailScreen() {
        val deviceDetail = toRoute<DeviceDetail>()

        val syncRuntimeInfo by syncManager
            .getSyncHandlers()[deviceDetail.appInstanceId]
            ?.syncRuntimeInfoFlow
            ?.collectAsState() ?: remember { mutableStateOf(null) }

        LaunchedEffect(syncRuntimeInfo) {
            if (syncRuntimeInfo == null) {
                navigateAndClearStack(Devices)
            }
        }

        syncRuntimeInfo?.let { currentSyncRuntimeInfo ->
            val scope =
                remember(currentSyncRuntimeInfo) {
                    deviceScopeFactory.createDeviceScope(currentSyncRuntimeInfo)
                }
            DesktopScreenLayout {
                scope.DeviceDetailContentView()
            }
        }
    }

    @Composable
    private fun DevicesScreen() {
        DevicesContentView(
            guideContent = { DesktopPromoteGuide() },
        )
    }

    @Composable
    private fun ExportScreen() {
        DesktopScreenLayout {
            CompositionLocalProvider(LocalSmallSettingItemState provides true) {
                PasteExportContentView()
            }
        }
    }

    @Composable
    private fun ExtensionScreen() {
        DesktopScreenLayout {
            ExtensionContentView()
        }
    }

    @Composable
    private fun McpSettingsScreen() {
        DesktopScreenLayout {
            McpContentView()
        }
    }

    @Composable
    private fun OCRSettingsScreen() {
        DesktopScreenLayout {
            OCRContentView()
        }
    }

    @Composable
    private fun SourceControlSettingsScreen() {
        DesktopScreenLayout {
            SourceControlContentView()
        }
    }

    @Composable
    private fun ImportScreen() {
        DesktopScreenLayout {
            PasteImportContentView()
        }
    }

    @Composable
    private fun NavBackStackEntry.NearbyDeviceDetailScreen() {
        val nearbyDeviceDetail = toRoute<NearbyDeviceDetail>()

        val nearbySyncInfos by nearbyDeviceManager.nearbySyncInfos.collectAsState()

        val nearbyDeviceInfo =
            nearbySyncInfos.find {
                it.appInfo.appInstanceId == nearbyDeviceDetail.appInstanceId
            }

        LaunchedEffect(nearbyDeviceInfo) {
            if (nearbyDeviceInfo == null) {
                navigateAndClearStack(Devices)
            }
        }

        nearbyDeviceInfo?.let {
            val scope =
                remember(nearbyDeviceDetail) {
                    syncScopeFactory.createSyncScope(nearbyDeviceInfo)
                }
            DesktopScreenLayout {
                scope.NearbyDeviceDetailContentView()
            }
        }
    }

    @Composable
    private fun PairingCodeScreen() {
        DesktopScreenLayout {
            PairingCodeContentView()
        }
    }

    @Composable
    private fun ShortcutKeysScreen() {
        DesktopScreenLayout {
            CompositionLocalProvider(LocalSmallSettingItemState provides true) {
                ShortcutKeysContentView()
            }
        }
    }

    @Composable
    private fun SettingsScreen() {
        DesktopScreenLayout {
            SettingsContentView()
        }
    }

    @Composable
    private fun PasteboardSettingsScreen() {
        DesktopScreenLayout {
            CompositionLocalProvider(LocalSmallSettingItemState provides true) {
                PasteboardSettingsContentView {
                    DesktopPasteboardSettingsContentView(platform)
                }
            }
        }
    }

    @Composable
    private fun NetworkSettingsScreen() {
        DesktopScreenLayout {
            CompositionLocalProvider(LocalSmallSettingItemState provides true) {
                DesktopNetworkSettingsContentView()
            }
        }
    }

    @Composable
    private fun StorageSettingsScreen() {
        DesktopScreenLayout {
            CompositionLocalProvider(LocalSmallSettingItemState provides true) {
                StorageSettingsContentView(storagePathManager)
            }
        }
    }

    @Composable
    private fun ShareScreen() {
        ScreenLayout(
            horizontal = xxLarge,
            top = xxLarge,
            bottom = xxLarge,
        ) {
            ShareContentView()
        }
    }

    @Composable
    fun TokenView() {
        TokenView(IntOffset(0, 0))
    }

    @Composable
    fun DragTargetView() {
        DragTargetContentView()
    }
}
