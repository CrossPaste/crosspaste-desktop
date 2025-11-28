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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.navigation.compose.currentBackStackEntryAsState
import com.crosspaste.app.DesktopAppSize
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.platform.Platform
import com.crosspaste.platform.macos.MacAppUtils
import com.crosspaste.ui.model.PasteSelectionViewModel
import com.crosspaste.ui.search.center.CenterSearchWindowContent
import com.crosspaste.ui.search.side.SideSearchWindowContent
import com.crosspaste.ui.theme.DesktopSearchWindowStyle
import com.sun.jna.Pointer
import io.github.oshai.kotlinlogging.KotlinLogging
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
    val currentSearchWindowState by appWindowManager.searchWindowState.collectAsState()
    val showSearchWindow by appWindowManager.showSearchWindow.collectAsState()

    val isMac by remember { mutableStateOf(platform.isMacos()) }

    var currentStyle by remember { mutableStateOf(config.searchWindowStyle) }

    val isCenterStyle by remember(currentStyle) {
        mutableStateOf(
            config.searchWindowStyle == DesktopSearchWindowStyle.CENTER_STYLE.style,
        )
    }

    val animationProgress by animateFloatAsState(
        targetValue = if (showSearchWindow && !isCenterStyle) 0f else 1f,
        animationSpec =
            tween(
                durationMillis = 150,
                delayMillis = 0,
            ),
    )

    val windowState =
        remember(showSearchWindow, animationProgress, isCenterStyle) {
            if (isCenterStyle) {
                currentSearchWindowState
            } else {
                currentSearchWindowState.position

                val position =
                    WindowPosition(
                        x = currentSearchWindowState.position.x,
                        y =
                            currentSearchWindowState.position.y +
                                appSize.sideSearchWindowHeight * animationProgress,
                    )
                WindowState(
                    placement = currentSearchWindowState.placement,
                    position = position,
                    size = currentSearchWindowState.size,
                )
            }
        }

    val logger = remember { KotlinLogging.logger("SearchWindow") }

    LaunchedEffect(showSearchWindow) {
        if (showSearchWindow) {
            appWindowManager.focusSearchWindow()
        }
    }

    Window(
        onCloseRequest = {
            appWindowManager.hideSearchWindow()
        },
        visible = showSearchWindow,
        state = windowState,
        title = appWindowManager.searchWindowTitle,
        icon = windowIcon,
        alwaysOnTop = true,
        undecorated = true,
        transparent = false,
        resizable = false,
    ) {
        LaunchedEffect(config.searchWindowStyle) {
            if (currentStyle != config.searchWindowStyle) {
                currentStyle = config.searchWindowStyle
            }
        }

        DisposableEffect(Unit) {
            if (isMac) {
                runCatching {
                    val pointer = Pointer(window.windowHandle)
                    MacAppUtils.setWindowLevelScreenSaver(pointer)
                }
            }

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

        if (isCenterStyle) {
            CenterSearchWindowContent()
        } else {
            SideSearchWindowContent()
        }
    }
}
