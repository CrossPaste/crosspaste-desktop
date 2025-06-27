package com.crosspaste.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import com.crosspaste.CrossPaste.Companion.koinApplication
import com.crosspaste.app.DesktopAppSize
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.platform.Platform
import com.crosspaste.platform.macos.MacAppUtils
import com.crosspaste.ui.search.center.CenterSearchWindowContent
import com.crosspaste.ui.search.side.SideSearchWindowContent
import com.crosspaste.ui.theme.DesktopSearchWindowStyle
import com.sun.jna.Pointer
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

@Composable
fun ApplicationScope.SearchWindow(windowIcon: Painter?) {
    val configManager = koinApplication.koin.get<DesktopConfigManager>()
    val config by configManager.config.collectAsState()

    when (config.searchWindowStyle) {
        DesktopSearchWindowStyle.CENTER_STYLE.style -> {
            SearchWindowCentreStyle(windowIcon)
        }
        else -> {
            SearchWindowSideStyle(windowIcon)
        }
    }
}

@Composable
private fun ApplicationScope.SearchWindowCentreStyle(windowIcon: Painter?) {
    val appWindowManager = koinApplication.koin.get<DesktopAppWindowManager>()

    val currentSearchWindowState by appWindowManager.searchWindowState.collectAsState()
    val showSearchWindow by appWindowManager.showSearchWindow.collectAsState()

    Window(
        onCloseRequest = ::exitApplication,
        visible = showSearchWindow,
        state = currentSearchWindowState,
        title = appWindowManager.getSearchWindowTitle(),
        icon = windowIcon,
        alwaysOnTop = true,
        undecorated = true,
        transparent = true,
        resizable = false,
    ) {
        DisposableEffect(Unit) {
            appWindowManager.searchComposeWindow = window

            val windowListener =
                object : WindowAdapter() {
                    override fun windowGainedFocus(e: WindowEvent?) {
                        appWindowManager.setShowSearchWindow(true)
                    }

                    override fun windowLostFocus(e: WindowEvent?) {
                        appWindowManager.setShowSearchWindow(false)
                    }
                }

            window.addWindowFocusListener(windowListener)

            onDispose {
                window.removeWindowFocusListener(windowListener)
            }
        }

        CenterSearchWindowContent()
    }
}

@Composable
private fun ApplicationScope.SearchWindowSideStyle(windowIcon: Painter?) {
    val appSize = koinApplication.koin.get<DesktopAppSize>()
    val appWindowManager = koinApplication.koin.get<DesktopAppWindowManager>()
    val platform = koinApplication.koin.get<Platform>()

    val currentSearchWindowState by appWindowManager.searchWindowState.collectAsState()
    val showSearchWindow by appWindowManager.showSearchWindow.collectAsState()

    val animationProgress by animateFloatAsState(
        targetValue = if (showSearchWindow) 0f else 1f,
        animationSpec =
            tween(
                durationMillis = 150,
                delayMillis = 0,
            ),
    )

    val windowState =
        remember(showSearchWindow, animationProgress) {
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

    Window(
        onCloseRequest = ::exitApplication,
        visible = showSearchWindow,
        state = windowState,
        title = appWindowManager.getSearchWindowTitle(),
        icon = windowIcon,
        alwaysOnTop = true,
        undecorated = true,
        transparent = true,
        resizable = false,
    ) {
        DisposableEffect(Unit) {
            if (platform.isMacos()) {
                runCatching {
                    val pointer = Pointer(window.windowHandle)
                    MacAppUtils.setWindowLevelScreenSaver(pointer)
                }
            }

            appWindowManager.searchComposeWindow = window

            val windowListener =
                object : WindowAdapter() {
                    override fun windowGainedFocus(e: WindowEvent?) {
                        appWindowManager.setShowSearchWindow(true)
                    }

                    override fun windowLostFocus(e: WindowEvent?) {
                        appWindowManager.setShowSearchWindow(false)
                    }
                }

            window.addWindowFocusListener(windowListener)

            onDispose {
                window.removeWindowFocusListener(windowListener)
            }
        }

        SideSearchWindowContent()
    }
}
