package com.crosspaste.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.navigation.compose.currentBackStackEntryAsState
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.platform.Platform
import com.crosspaste.platform.macos.MacAppUtils
import com.crosspaste.ui.DesktopContext.SearchWindowContext
import com.crosspaste.ui.model.PasteSelectionViewModel
import com.crosspaste.ui.search.side.SideSearchWindowContent
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.utils.cpuDispatcher
import com.sun.jna.Pointer
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

@Composable
fun SearchWindow(windowIcon: Painter?) {
    val appWindowManager = koinInject<DesktopAppWindowManager>()
    val pasteSelectionViewModel = koinInject<PasteSelectionViewModel>()
    val platform = koinInject<Platform>()

    val appSizeValue = LocalDesktopAppSizeValueState.current
    val navController = LocalNavHostController.current

    val backStackEntry by navController.currentBackStackEntryAsState()

    val searchWindowInfo by appWindowManager.searchWindowInfo.collectAsState()

    val isMac = remember { platform.isMacos() }

    val animationProgress by animateFloatAsState(
        targetValue = if (searchWindowInfo.show) 0f else 1f,
        animationSpec = tween(durationMillis = 150, delayMillis = 0),
    )

    val windowState =
        remember {
            WindowState(
                placement = searchWindowInfo.state.placement,
                position = searchWindowInfo.state.position,
                size = searchWindowInfo.state.size,
            )
        }

    val logger = remember { KotlinLogging.logger("SearchWindow") }

    var ignoreFocusLoss by remember { mutableStateOf(true) }

    LaunchedEffect(searchWindowInfo, animationProgress, appSizeValue) {
        // Update size and placement if they change
        windowState.placement = searchWindowInfo.state.placement
        windowState.size = searchWindowInfo.state.size

        // Calculate the dynamic Y position based on animation
        // Assume the target position is the 'visible' state (progress = 0f)
        val targetX = searchWindowInfo.state.position.x
        val targetY =
            searchWindowInfo.state.position.y +
                (appSizeValue.sideSearchWindowHeight * animationProgress)

        // Apply the position update to the stable state object
        windowState.position = WindowPosition(x = targetX, y = targetY)
    }

    LaunchedEffect(searchWindowInfo.show) {
        if (searchWindowInfo.show) {
            ignoreFocusLoss = true
            appWindowManager.focusSearchWindow(searchWindowInfo.trigger)
            delay(1000)
        }
        ignoreFocusLoss = false
    }

    Window(
        onCloseRequest = {
            appWindowManager.hideSearchWindow()
        },
        visible = searchWindowInfo.show,
        state = windowState,
        title = appWindowManager.searchWindowTitle,
        icon = windowIcon,
        alwaysOnTop = true,
        undecorated = true,
        transparent = isMac,
        resizable = false,
    ) {
        val color = AppUIColors.generalBackground.copy(alpha = 0.5f).toArgb()

        if (isMac) {
            WindowAcrylicEffect(
                window = this.window,
                currentArgb = color,
            )
        }

        DisposableEffect(Unit) {
            appWindowManager.searchComposeWindow = window

            val windowListener =
                object : WindowAdapter() {
                    override fun windowGainedFocus(e: WindowEvent) {
                        logger.info { "Search window gained focus" }
                        pasteSelectionViewModel.requestPasteListFocus()
                    }

                    override fun windowLostFocus(e: WindowEvent) {
                        if (ignoreFocusLoss) {
                            logger.info { "Ignored focus loss during startup grace period" }
                            return
                        }

                        logger.info { "Search window lost focus" }
                        if (backStackEntry?.let { getRouteName(it.destination) } != PasteTextEdit.NAME) {
                            appWindowManager.hideSearchWindow()
                        }
                    }
                }

            window.addWindowFocusListener(windowListener)

            onDispose {
                window.removeWindowFocusListener(windowListener)
            }
        }

        SearchWindowContext(searchWindowInfo) {
            SideSearchWindowContent()
        }
    }
}

@Composable
fun WindowAcrylicEffect(
    window: ComposeWindow,
    currentArgb: Int,
) {
    LaunchedEffect(window, currentArgb) {
        snapshotFlow { window.isDisplayable }
            .first { it }

        withContext(cpuDispatcher) {
            runCatching {
                val pointer = Pointer(window.windowHandle)
                MacAppUtils.setWindowLevelScreenSaver(pointer)
                MacAppUtils.applyAcrylicBackground(pointer, currentArgb)
            }
        }
    }
}
