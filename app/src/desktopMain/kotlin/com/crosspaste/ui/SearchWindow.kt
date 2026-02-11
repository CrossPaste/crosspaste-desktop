package com.crosspaste.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.platform.Platform
import com.crosspaste.platform.macos.MacAppUtils
import com.crosspaste.platform.windows.WindowsVersionHelper
import com.crosspaste.platform.windows.api.Dwmapi
import com.crosspaste.ui.DesktopContext.SearchWindowContext
import com.crosspaste.ui.model.PasteSelectionViewModel
import com.crosspaste.ui.search.side.SideSearchWindowContent
import com.crosspaste.ui.theme.ThemeDetector
import com.crosspaste.utils.cpuDispatcher
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun SearchWindow(windowIcon: Painter?) {
    val appWindowManager = koinInject<DesktopAppWindowManager>()
    val pasteSelectionViewModel = koinInject<PasteSelectionViewModel>()
    val platform = koinInject<Platform>()
    val themeDetector = koinInject<ThemeDetector>()

    val appSizeValue = LocalDesktopAppSizeValueState.current

    val searchWindowInfo by appWindowManager.searchWindowInfo.collectAsState()

    val themeState by themeDetector.themeState.collectAsState()

    val isMac = remember { platform.isMacos() }
    val isWindowsAndSupportBlurEffect =
        remember {
            platform.isWindows() && WindowsVersionHelper.isWindows11_22H2OrGreater
        }

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

    // AtomicBoolean instead of mutableStateOf: this flag is read from the AWT EDT thread
    // in the WindowAdapter callback, so it needs thread-safe access rather than Compose snapshot state.
    // Note: kotlinx.atomicfu.atomic() cannot be used here because the atomicfu compiler plugin
    // only supports class property initializers, not local variables inside functions.
    val ignoreFocusLoss = remember { AtomicBoolean(true) }

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
            ignoreFocusLoss.set(true)
            appWindowManager.focusSearchWindow(searchWindowInfo.trigger)
            delay(1000)
        }
        ignoreFocusLoss.set(false)
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
        transparent = isMac || isWindowsAndSupportBlurEffect,
        resizable = false,
    ) {
        if (isMac) {
            MacAcrylicEffect(
                window = this.window,
                isDark = themeState.isCurrentThemeDark,
            )
        } else if (isWindowsAndSupportBlurEffect) {
            WindowsBlurEffect(
                window = this.window,
                isDark = themeState.isCurrentThemeDark,
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
                        if (ignoreFocusLoss.get()) {
                            logger.info { "Ignored focus loss during startup grace period" }
                            return
                        }

                        logger.info { "Search window lost focus" }
                        if (!appWindowManager.isBubbleWindowVisible()) {
                            appWindowManager.hideSearchWindow()
                        }
                    }
                }

            window.addWindowFocusListener(windowListener)

            onDispose {
                window.removeWindowFocusListener(windowListener)
                appWindowManager.searchComposeWindow = null
            }
        }

        SearchWindowContext(searchWindowInfo) {
            SideSearchWindowContent()
        }
    }
}

@Composable
fun MacAcrylicEffect(
    window: ComposeWindow,
    isDark: Boolean,
) {
    LaunchedEffect(window, isDark) {
        snapshotFlow { window.isDisplayable }
            .first { it }

        window.background = java.awt.Color(0, 0, 0, 0)

        withContext(cpuDispatcher) {
            runCatching {
                val pointer = Pointer(window.windowHandle)
                MacAppUtils.setWindowLevelScreenSaver(pointer)
                MacAppUtils.applyAcrylicBackground(pointer, isDark)
            }
        }
    }
}

@Composable
fun WindowsBlurEffect(
    window: ComposeWindow,
    isDark: Boolean,
) {
    LaunchedEffect(window, isDark) {
        snapshotFlow { window.isDisplayable }.first { it }

        window.background = java.awt.Color(0, 0, 0, 0)

        val hwnd = WinDef.HWND(Native.getWindowPointer(window))

        val darkMode = Memory(4).apply { setInt(0, if (isDark) 1 else 0) }
        Dwmapi.INSTANCE.DwmSetWindowAttribute(
            hwnd,
            Dwmapi.DWMWA_USE_IMMERSIVE_DARK_MODE,
            darkMode,
            4,
        )

        val backdropType = Memory(4).apply { setInt(0, Dwmapi.DWMSBT_TRANSIENT) }
        Dwmapi.INSTANCE.DwmSetWindowAttribute(
            hwnd,
            Dwmapi.DWMWA_SYSTEMBACKDROP_TYPE,
            backdropType,
            4,
        )
    }
}
