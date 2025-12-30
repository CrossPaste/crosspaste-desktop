package com.crosspaste.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import androidx.navigation.toRoute
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.paste.PasteData
import com.crosspaste.sync.SyncManager
import com.crosspaste.ui.base.RecommendContentView
import com.crosspaste.ui.devices.DeviceDetailContentView
import com.crosspaste.ui.devices.DeviceScopeFactory
import com.crosspaste.ui.devices.DevicesContentView
import com.crosspaste.ui.devices.NearbyDeviceDetailContentView
import com.crosspaste.ui.devices.QRContentView
import com.crosspaste.ui.devices.SyncScopeFactory
import com.crosspaste.ui.devices.TokenView
import com.crosspaste.ui.extension.ExtensionContentView
import com.crosspaste.ui.extension.ocr.OCRScreen
import com.crosspaste.ui.paste.PasteExportContentView
import com.crosspaste.ui.paste.PasteImportContentView
import com.crosspaste.ui.paste.createPasteDataScope
import com.crosspaste.ui.paste.edit.PasteTextEditContentView
import com.crosspaste.ui.settings.SettingsContentView
import com.crosspaste.ui.settings.ShortcutKeysContentView
import kotlinx.coroutines.channels.Channel
import kotlin.reflect.typeOf

class DesktopScreenProvider(
    private val deviceScopeFactory: DeviceScopeFactory,
    private val syncManager: SyncManager,
    private val syncScopeFactory: SyncScopeFactory,
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
                    typeMap =
                        mapOf(
                            typeOf<SyncInfo>() to JsonNavType(SyncInfo.serializer()),
                        ),
                ) { backStackEntry ->
                    backStackEntry.NearbyDeviceDetailScreen()
                }
            }
            composable<Export> { ExportScreen() }
            navigation<ExtensionGraph>(startDestination = Extension) {
                composable<Extension> { ExtensionScreen() }
                composable<OCR>(
                    exitTransition = { slideOutRight() },
                    enterTransition = { slideInLeft() },
                ) {
                    OCRScreen()
                }
            }
            composable<Import> { ImportScreen() }
            composable<PasteTextEdit>(
                exitTransition = { slideOutRight() },
                enterTransition = { slideInLeft() },
                typeMap =
                    mapOf(
                        typeOf<PasteData>() to JsonNavType(PasteData.serializer()),
                    ),
            ) { backStackEntry ->
                backStackEntry.PasteTextEditScreen()
            }
            composable<QrCode> { QRScreen() }
            composable<Recommend> { RecommendScreen() }
            composable<Settings> { SettingsScreen() }
            composable<ShortcutKeys> { ShortcutKeysScreen() }
        }
    }

    @Composable
    private fun AboutScreen() {
        AboutContentView()
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
            scope.DeviceDetailContentView()
        }
    }

    @Composable
    private fun DevicesScreen() {
        DevicesContentView()
    }

    @Composable
    private fun ExportScreen() {
        PasteExportContentView()
    }

    @Composable
    private fun ExtensionScreen() {
        ExtensionContentView()
    }

    @Composable
    private fun ImportScreen() {
        PasteImportContentView()
    }

    @Composable
    private fun NavBackStackEntry.NearbyDeviceDetailScreen() {
        val nearbyDeviceDetail = toRoute<NearbyDeviceDetail>()
        val scope =
            remember(nearbyDeviceDetail) {
                syncScopeFactory.createSyncScope(nearbyDeviceDetail.syncInfo)
            }
        scope.NearbyDeviceDetailContentView()
    }

    @Composable
    private fun NavBackStackEntry.PasteTextEditScreen() {
        val pasteTextEdit = toRoute<PasteTextEdit>()
        val currentPasteData = pasteTextEdit.pasteData
        val scope =
            remember(currentPasteData.id, currentPasteData.pasteState, currentPasteData.pasteSearchContent) {
                createPasteDataScope(currentPasteData)
            }

        LaunchedEffect(scope) {
            if (scope == null) {
                navigateAndClearStack(Pasteboard)
            }
        }

        scope?.PasteTextEditContentView()
    }

    @Composable
    private fun QRScreen() {
        QRContentView()
    }

    @Composable
    private fun ShortcutKeysScreen() {
        ShortcutKeysContentView()
    }

    @Composable
    private fun SettingsScreen() {
        SettingsContentView()
    }

    @Composable
    private fun RecommendScreen() {
        RecommendContentView()
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
