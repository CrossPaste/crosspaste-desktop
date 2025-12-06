package com.crosspaste.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.navigation.compose.currentBackStackEntryAsState
import com.crosspaste.app.DesktopAppSize
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.platform.Platform
import com.crosspaste.platform.macos.MacAppUtils
import com.crosspaste.ui.base.DesktopMenu.ProvidesMenuContext
import com.crosspaste.ui.model.PasteSelectionViewModel
import com.crosspaste.ui.search.center.CenterSearchWindowContent
import com.crosspaste.ui.search.side.SideSearchWindowContent
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.DesktopSearchWindowStyle
import com.sun.jna.Pointer
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

@Composable
fun SearchWindow(windowIcon: Painter?) {
    val appSize = koinInject<DesktopAppSize>()
    val appWindowManager = koinInject<DesktopAppWindowManager>()
    val configManager = koinInject<DesktopConfigManager>()
    val pasteSelectionViewModel = koinInject<PasteSelectionViewModel>()
    val platform = koinInject<Platform>()

    val navController = LocalNavHostController.current

    val backStackEntry by navController.currentBackStackEntryAsState()

    val config by configManager.config.collectAsState()
    val searchWindowInfo by appWindowManager.searchWindowInfo.collectAsState()

    val isMac = remember { platform.isMacos() }

    val isCenterStyle =
        remember(config.searchWindowStyle) {
            config.searchWindowStyle == DesktopSearchWindowStyle.CENTER_STYLE.style
        }

    val animationProgress by animateFloatAsState(
        targetValue = if (searchWindowInfo.show && !isCenterStyle) 0f else 1f,
        animationSpec =
            tween(
                durationMillis = 150,
                delayMillis = 0,
            ),
    )

    val windowState =
        remember(searchWindowInfo, animationProgress, isCenterStyle) {
            if (isCenterStyle) {
                searchWindowInfo.state
            } else {
                val position =
                    WindowPosition(
                        x = searchWindowInfo.state.position.x,
                        y =
                            searchWindowInfo.state.position.y +
                                appSize.sideSearchWindowHeight * animationProgress,
                    )
                WindowState(
                    placement = searchWindowInfo.state.placement,
                    position = position,
                    size = searchWindowInfo.state.size,
                )
            }
        }

    val logger = remember { KotlinLogging.logger("SearchWindow") }

    LaunchedEffect(searchWindowInfo.show) {
        if (searchWindowInfo.show) {
            appWindowManager.focusSearchWindow(searchWindowInfo.trigger)
        }
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
        LaunchedEffect(color) {
            if (isMac) {
                delay(100)
                runCatching {
                    val pointer = Pointer(window.windowHandle)
                    MacAppUtils.setWindowLevelScreenSaver(pointer)
                    MacAppUtils.applyAcrylicBackground(pointer, color)
                }
            }
        }

        DisposableEffect(Unit) {
            appWindowManager.searchComposeWindow = window

            val windowListener =
                object : WindowAdapter() {
                    override fun windowGainedFocus(e: WindowEvent?) {
                        logger.debug { "Search window gained focus" }
                        pasteSelectionViewModel.requestPasteListFocus()
                    }

                    override fun windowLostFocus(e: WindowEvent?) {
                        logger.debug { "Search window lost focus" }
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

        ProvidesMenuContext {
            if (isCenterStyle) {
                CenterSearchWindowContent()
            } else {
                SideSearchWindowContent()
            }
        }
    }
}
