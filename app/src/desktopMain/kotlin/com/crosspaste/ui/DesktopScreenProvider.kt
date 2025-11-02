package com.crosspaste.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.crosspaste.app.DesktopAppSize
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.paste.PasteData
import com.crosspaste.sync.SyncManager
import com.crosspaste.ui.base.RecommendContentView
import com.crosspaste.ui.base.ToastListView
import com.crosspaste.ui.devices.DeviceDetailContentView
import com.crosspaste.ui.devices.DeviceScopeFactory
import com.crosspaste.ui.devices.DevicesContentView
import com.crosspaste.ui.devices.NearbyDeviceDetailContentView
import com.crosspaste.ui.devices.QRContentView
import com.crosspaste.ui.devices.SyncScopeFactory
import com.crosspaste.ui.devices.TokenView
import com.crosspaste.ui.extension.ExtensionContentView
import com.crosspaste.ui.paste.PasteExportContentView
import com.crosspaste.ui.paste.PasteImportContentView
import com.crosspaste.ui.paste.createPasteDataScope
import com.crosspaste.ui.paste.edit.PasteTextEditContentView
import com.crosspaste.ui.paste.preview.PasteboardContentView
import com.crosspaste.ui.settings.SettingsContentView
import com.crosspaste.ui.settings.ShortcutKeysContentView
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny3X
import com.crosspaste.ui.theme.AppUISize.zero
import kotlinx.coroutines.channels.Channel
import kotlin.reflect.typeOf

class DesktopScreenProvider(
    private val appSize: DesktopAppSize,
    private val deviceScopeFactory: DeviceScopeFactory,
    private val syncManager: SyncManager,
    private val syncScopeFactory: SyncScopeFactory,
) : ScreenProvider {

    private val navigationEvents = Channel<NavigationEvent>(Channel.UNLIMITED)

    override fun navigate(route: Route) {
        navigationEvents.trySend(NavigationEvent.Navigate(route))
    }

    override fun navigateAndClearStack(route: Route) {
        navigationEvents.trySend(NavigationEvent.NavigateAndClearStack(route))
    }

    override fun navigateUp() {
        navigationEvents.trySend(NavigationEvent.NavigateUp)
    }

    override fun navigateAction(
        event: NavigationEvent,
        navController: NavHostController,
    ) {
        when (event) {
            is NavigationEvent.Navigate -> {
                navController.navigate(event.route)
            }
            is NavigationEvent.NavigateAndClearStack -> {
                navController.navigate(event.route) {
                    popUpTo(0) { inclusive = true }
                }
            }
            is NavigationEvent.NavigateUp -> {
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
            startDestination = Pasteboard,
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            composable<About> { AboutScreen() }
            composable<Devices> {
                DevicesScreen()
            }
            composable<DeviceDetail> { backStackEntry ->
                backStackEntry.DeviceDetailScreen()
            }
            composable<Export> { ExportScreen() }
            composable<Extension> { ExtensionScreen() }
            composable<Import> { ImportScreen() }
            composable<NearbyDeviceDetail>(
                typeMap =
                    mapOf(
                        typeOf<SyncInfo>() to JsonNavType(SyncInfo.serializer()),
                    ),
            ) { backStackEntry ->
                backStackEntry.NearbyDeviceDetailScreen()
            }
            composable<Pasteboard> {
                PasteboardScreen()
            }
            composable<PasteTextEdit>(
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
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = medium)
                    .padding(bottom = medium),
        ) {
            WindowDecoration()
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
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = medium)
                        .padding(bottom = medium),
            ) {
                WindowDecoration()
                scope.DeviceDetailContentView()
            }
        }
    }

    @Composable
    private fun DevicesScreen() {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = medium)
                    .padding(bottom = medium),
        ) {
            WindowDecoration()
            DevicesContentView()
        }
    }

    @Composable
    private fun ExportScreen() {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = medium)
                    .padding(bottom = medium),
        ) {
            WindowDecoration()
            PasteExportContentView()
        }
    }

    @Composable
    private fun ExtensionScreen() {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = medium)
                    .padding(bottom = medium),
        ) {
            WindowDecoration()
            ExtensionContentView()
        }
    }

    @Composable
    private fun ImportScreen() {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = medium)
                    .padding(bottom = medium),
        ) {
            WindowDecoration()
            PasteImportContentView()
        }
    }

    @Composable
    private fun NavBackStackEntry.NearbyDeviceDetailScreen() {
        val nearbyDeviceDetail = toRoute<NearbyDeviceDetail>()
        val scope =
            remember(nearbyDeviceDetail) {
                syncScopeFactory.createSyncScope(nearbyDeviceDetail.syncInfo)
            }
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = medium)
                    .padding(bottom = medium),
        ) {
            WindowDecoration()
            scope.NearbyDeviceDetailContentView()
        }
    }

    @Composable
    private fun PasteboardScreen() {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(start = medium, bottom = medium),
        ) {
            WindowDecoration()
            PasteboardContentView()
        }
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

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = medium)
                    .padding(bottom = medium),
        ) {
            WindowDecoration()
            scope?.PasteTextEditContentView()
        }
    }

    @Composable
    private fun QRScreen() {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = medium)
                    .padding(bottom = medium),
        ) {
            WindowDecoration()
            QRContentView()
        }
    }

    @Composable
    private fun ShortcutKeysScreen() {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = medium)
                    .padding(bottom = medium),
        ) {
            WindowDecoration()
            ShortcutKeysContentView()
        }
    }

    @Composable
    private fun SettingsScreen() {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = medium)
                    .padding(bottom = medium),
        ) {
            WindowDecoration()
            SettingsContentView()
        }
    }

    @Composable
    private fun RecommendScreen() {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = medium)
                    .padding(bottom = medium),
        ) {
            WindowDecoration()
            RecommendContentView()
        }
    }

    @Composable
    fun TokenView() {
        TokenView(IntOffset(0, 0))
    }

    @Composable
    fun ToastView() {
        val density = LocalDensity.current

        val yOffset by remember {
            mutableStateOf(-appSize.windowDecorationHeight + tiny3X)
        }

        ToastListView(
            IntOffset(
                with(density) { zero.roundToPx() },
                with(density) { yOffset.roundToPx() },
            ),
            enter =
                slideInHorizontally(
                    initialOffsetX = { -it },
                    animationSpec = tween(300),
                ) +
                    fadeIn(
                        initialAlpha = 0f,
                        animationSpec = tween(150),
                    ),
            exit =
                slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(300),
                ) +
                    fadeOut(
                        animationSpec = tween(300),
                    ) +
                    shrinkVertically(
                        animationSpec = tween(300),
                    ),
        )
    }

    @Composable
    fun DragTargetView() {
        DragTargetContentView()
    }
}
