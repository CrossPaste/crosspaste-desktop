package com.crosspaste.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.awt.SwingWindow
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.window.WindowDecoration
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.app.WinAppWindowManager
import com.crosspaste.app.WindowTrigger
import com.crosspaste.platform.Platform
import com.crosspaste.platform.macos.MacAppUtils
import com.crosspaste.platform.windows.WindowsFocusUtils
import com.crosspaste.platform.windows.WindowsVersionHelper
import com.crosspaste.platform.windows.api.Dwmapi
import com.crosspaste.ui.DesktopContext.SearchWindowContext
import com.crosspaste.ui.DesktopDisplayScale.toWindowUnits
import com.crosspaste.ui.model.PasteSelectionViewModel
import com.crosspaste.ui.search.side.SideSearchWindowContent
import com.crosspaste.ui.theme.ThemeDetector
import com.crosspaste.utils.cpuDispatcher
import com.github.kwhat.jnativehook.GlobalScreen
import com.github.kwhat.jnativehook.mouse.NativeMouseEvent
import com.github.kwhat.jnativehook.mouse.NativeMouseListener
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import java.awt.EventQueue
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds

@Composable
@OptIn(ExperimentalComposeUiApi::class)
fun SearchWindow(windowIcon: Painter?) {
    val appWindowManager = koinInject<DesktopAppWindowManager>()
    val pasteSelectionViewModel = koinInject<PasteSelectionViewModel>()
    val platform = koinInject<Platform>()
    val themeDetector = koinInject<ThemeDetector>()

    val appSizeValue = LocalDesktopAppSizeValueState.current

    val searchWindowInfo by appWindowManager.searchWindowInfo.collectAsState()

    val themeConfig by themeDetector.themeConfig.collectAsState()
    val isDarkTheme = themeConfig.resolveIsDark(isSystemInDarkTheme())

    val isMac = remember { platform.isMacos() }
    val isLinux = remember { platform.isLinux() }
    val isWindows = remember { platform.isWindows() }
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
    // Reset before the window becomes visible, not in LaunchedEffect: AWT can
    // deliver the first focus event before that coroutine starts.
    val gainedFocus =
        remember(searchWindowInfo.show, searchWindowInfo.trigger) {
            AtomicBoolean(searchWindowInfo.show && appWindowManager.searchComposeWindow?.isFocused == true)
        }
    val ignoreFocusLoss =
        remember(searchWindowInfo.show, searchWindowInfo.trigger) {
            AtomicBoolean(searchWindowInfo.show && !isWindows && !(isLinux && gainedFocus.get()))
        }
    // The focus listener is registered once per window; it reads the flags of the
    // current show cycle through these, so re-showing does not re-register it.
    val currentGainedFocus by rememberUpdatedState(gainedFocus)
    val currentIgnoreFocusLoss by rememberUpdatedState(ignoreFocusLoss)
    val windowsFocusSession =
        remember(searchWindowInfo.show, searchWindowInfo.trigger) {
            if (isWindows && searchWindowInfo.show && searchWindowInfo.trigger != WindowTrigger.PREVIEW) {
                SearchWindowFocusSession()
            } else {
                null
            }
        }
    val currentWindowsFocusSession by rememberUpdatedState(windowsFocusSession)
    DisposableEffect(windowsFocusSession) {
        onDispose { windowsFocusSession?.close() }
    }

    // Compute and apply the effective window position synchronously during composition.
    // This ensures the Window composable always sees the correct off-screen position on the
    // same frame where visible becomes true, preventing the flash when macOS recreates the
    // native window after reclaiming it during long background periods.
    // Similar to the theme flash fix (9086f28): compute effective state before rendering.
    remember(searchWindowInfo, animationProgress, appSizeValue) {
        windowState.placement = searchWindowInfo.state.placement
        windowState.size = searchWindowInfo.state.size
        windowState.position =
            WindowPosition(
                x = searchWindowInfo.state.position.x,
                y =
                    searchWindowInfo.state.position.y +
                        (appSizeValue.sideSearchWindowHeight.toWindowUnits() * animationProgress),
            )
    }

    LaunchedEffect(searchWindowInfo.show, searchWindowInfo.trigger) {
        // Windows activation starts inside the window, after its manager registration.
        // Native foreground events provide protection there without a timed grace period.
        if (isWindows) return@LaunchedEffect
        if (searchWindowInfo.show) {
            if (searchWindowInfo.trigger != WindowTrigger.PREVIEW) {
                // Reset selection and scroll before an interactive window paints. Preview must
                // not mutate user state, and a user action can claim an already-visible preview.
                pasteSelectionViewModel.resetToTop()
                appWindowManager.focusSearchWindow(searchWindowInfo.trigger)
            }
            delay(1000.milliseconds)
        }
        ignoreFocusLoss.set(false)
        // A loss ignored during startup will not be delivered again. Check the
        // final focus, but keep non-focusable previews and bubble transitions.
        if (searchWindowInfo.show && gainedFocus.get() && searchWindowInfo.trigger != WindowTrigger.PREVIEW) {
            appWindowManager.searchComposeWindow?.let { searchWindow ->
                if (!searchWindow.isFocused && appWindowManager.bubbleComposeWindow?.isFocused != true) {
                    appWindowManager.hideSearchWindow()
                }
            }
        }
    }

    SwingWindow(
        onCloseRequest = {
            appWindowManager.hideSearchWindow()
        },
        visible = searchWindowInfo.show,
        state = windowState,
        title = appWindowManager.searchWindowTitle,
        icon = windowIcon,
        alwaysOnTop = true,
        decoration = WindowDecoration.Undecorated(),
        transparent = isMac || isWindowsAndSupportBlurEffect,
        resizable = false,
        // During an appearance preview the window must not steal focus from the
        // settings window (AWT setVisible would otherwise auto-request focus)
        focusable = searchWindowInfo.trigger != WindowTrigger.PREVIEW,
        init = { window ->
            if (isWindows) {
                // Configure the native peer before the shell can create a taskbar button.
                // Changing WS_EX_TOOLWINDOW on a visible frame leaves a ghost button behind.
                window.type = java.awt.Window.Type.UTILITY
                // Windows activation is explicit; AWT's automatic show-time request
                // can briefly take foreground and then restore the previous app.
                window.isAutoRequestFocus = false
            }
        },
    ) {
        DisplayScaleContent {
            if (isMac) {
                MacAcrylicEffect(
                    window = this.window,
                    isDark = isDarkTheme,
                )
            } else if (isWindowsAndSupportBlurEffect) {
                WindowsBlurEffect(
                    window = this.window,
                    isDark = isDarkTheme,
                )
            }

            DisposableEffect(Unit) {
                appWindowManager.searchComposeWindow = window
                val listenerActive = AtomicBoolean(true)

                fun deferWindowsFocusLoss(session: SearchWindowFocusSession) {
                    session.deferFocusLoss(
                        enqueue = { EventQueue.invokeLater(it) },
                        shouldDismiss = {
                            val manager = appWindowManager as WinAppWindowManager
                            val otherForeground = WindowsFocusUtils.isOtherApplicationForeground()
                            val searchForeground = manager.isSearchWindowForeground()
                            val bubbleFocused =
                                manager.bubbleComposeWindow?.isFocused == true || manager.isBubbleWindowForeground()
                            listenerActive.get() &&
                                currentWindowsFocusSession === session &&
                                window.isVisible &&
                                !searchForeground &&
                                !window.isFocused &&
                                !bubbleFocused &&
                                !WindowsFocusUtils.isTrayForeground() &&
                                (otherForeground || manager.isMainWindowForeground())
                        },
                        dismiss = {
                            appWindowManager.hideSearchWindow()
                        },
                    )
                }

                val foregroundSubscription =
                    if (appWindowManager is WinAppWindowManager) {
                        appWindowManager.subscribeForegroundChanges { hwnd ->
                            val session = currentWindowsFocusSession
                            EventQueue.invokeLater {
                                if (listenerActive.get() && session != null && currentWindowsFocusSession === session) {
                                    // Ignore historical startup gains that already reverted.
                                    // Outside presses have their own independent dismissal path.
                                    val acquired =
                                        appWindowManager.isSearchWindow(hwnd) &&
                                            appWindowManager.isSearchWindowForeground()
                                    session.onForegroundChanged(acquired) {
                                        deferWindowsFocusLoss(session)
                                    }
                                }
                            }
                        }
                    } else {
                        null
                    }

                val mouseListener =
                    if (appWindowManager is WinAppWindowManager) {
                        object : NativeMouseListener {
                            override fun nativeMousePressed(nativeEvent: NativeMouseEvent) {
                                val session = currentWindowsFocusSession ?: return
                                val x = nativeEvent.x
                                val y = nativeEvent.y
                                // Candidate selection can commit before the deferred outside
                                // check runs. Sample before tray hit-testing and deferred dismissal.
                                val composing = appWindowManager.isSearchWindowComposing()
                                // Capture before queuing: releasing an overflow icon can close
                                // its panel before the EDT processes this press.
                                if (WindowsFocusUtils.isTrayIcon(x, y) || composing) return
                                session.deferOutsidePress(
                                    enqueue = { EventQueue.invokeLater(it) },
                                    shouldDismiss = {
                                        listenerActive.get() &&
                                            currentWindowsFocusSession === session &&
                                            window.isVisible &&
                                            appWindowManager.isOutsideSearchWindow(x, y)
                                    },
                                    dismiss = {
                                        appWindowManager.hideSearchWindow()
                                    },
                                )
                            }
                        }.also { GlobalScreen.addNativeMouseListener(it) }
                    } else {
                        null
                    }

                val windowListener =
                    object : WindowAdapter() {
                        override fun windowGainedFocus(e: WindowEvent) {
                            currentGainedFocus.set(true)
                            if (appWindowManager is WinAppWindowManager) {
                                val foreground = appWindowManager.isSearchWindowForeground()
                                currentWindowsFocusSession?.onFocusGained(foreground)
                            }
                            // On Linux, once mapped and focused, outside clicks are
                            // real dismissals even within the first second.
                            if (isLinux) {
                                currentIgnoreFocusLoss.set(false)
                            }
                            logger.info { "Search window gained focus" }
                            pasteSelectionViewModel.requestPasteListFocus()
                        }

                        override fun windowLostFocus(e: WindowEvent) {
                            val session = currentWindowsFocusSession
                            if (isWindows) {
                                val isBubbleWindow =
                                    appWindowManager.bubbleComposeWindow != null &&
                                        e.oppositeWindow == appWindowManager.bubbleComposeWindow
                                if (!isBubbleWindow) {
                                    // AWT focus can churn while the native window stays foreground.
                                    // Only a confirmed native departure can dismiss on Windows.
                                    session?.let { deferWindowsFocusLoss(it) }
                                }
                                return
                            }
                            if (currentIgnoreFocusLoss.get()) {
                                logger.info { "Ignored focus loss during startup grace period" }
                                return
                            }

                            logger.info { "Search window lost focus" }
                            val isBubbleWindow =
                                appWindowManager.bubbleComposeWindow != null &&
                                    e.oppositeWindow == appWindowManager.bubbleComposeWindow
                            if (!isBubbleWindow) {
                                // Let queued AWT focus changes settle. A transient
                                // loss followed by a gain must not close the window.
                                EventQueue.invokeLater {
                                    if (listenerActive.get() &&
                                        window.isVisible &&
                                        !window.isFocused &&
                                        appWindowManager.bubbleComposeWindow?.isFocused != true
                                    ) {
                                        appWindowManager.hideSearchWindow()
                                    }
                                }
                            }
                        }
                    }

                window.addWindowFocusListener(windowListener)

                onDispose {
                    listenerActive.set(false)
                    foregroundSubscription?.close()
                    mouseListener?.let { GlobalScreen.removeNativeMouseListener(it) }
                    window.removeWindowFocusListener(windowListener)
                    appWindowManager.searchComposeWindow = null
                }
            }

            LaunchedEffect(windowsFocusSession) {
                if (windowsFocusSession != null) {
                    pasteSelectionViewModel.resetToTop()
                    windowsFocusSession.activate {
                        appWindowManager.focusSearchWindow(searchWindowInfo.trigger)
                        (appWindowManager as WinAppWindowManager).isSearchWindowForeground()
                    }
                }
            }

            SearchWindowContext(searchWindowInfo) {
                SideSearchWindowContent()
            }
        }
    }
}

@Composable
fun MacAcrylicEffect(
    window: ComposeWindow,
    isDark: Boolean? = null,
) {
    LaunchedEffect(window, isDark) {
        snapshotFlow { window.isDisplayable }
            .first { it }

        if (isDark != null) {
            window.background = java.awt.Color(0, 0, 0, 0)
        }

        withContext(cpuDispatcher) {
            runCatching {
                val pointer = Pointer(window.windowHandle)
                MacAppUtils.setWindowLevelPopUpMenu(pointer)
                if (isDark != null) {
                    MacAppUtils.applyAcrylicBackground(pointer, isDark)
                }
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
