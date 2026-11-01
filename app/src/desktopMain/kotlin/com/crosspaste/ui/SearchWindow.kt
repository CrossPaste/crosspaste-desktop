package com.crosspaste.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.awt.SwingWindow
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
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
import java.awt.Desktop
import java.awt.EventQueue
import java.awt.KeyboardFocusManager
import java.awt.Window
import java.awt.desktop.AppForegroundEvent
import java.awt.desktop.AppForegroundListener
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
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
    val bubbleWindowInfo by appWindowManager.bubbleWindowInfo.collectAsState()

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
            AtomicBoolean(searchWindowInfo.show && !isWindows && !isMac && !(isLinux && gainedFocus.get()))
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
    val macFocusSession =
        remember(searchWindowInfo.show, searchWindowInfo.trigger) {
            if (isMac && searchWindowInfo.show && searchWindowInfo.trigger != WindowTrigger.PREVIEW) {
                MacSearchWindowFocusSession(appWindowManager.searchComposeWindow?.isFocused == true)
            } else {
                null
            }
        }
    val currentMacFocusSession by rememberUpdatedState(macFocusSession)
    DisposableEffect(macFocusSession) {
        onDispose { macFocusSession?.close() }
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
        if (isMac) {
            logger.info {
                "Mac search visibility: show=${searchWindowInfo.show}, trigger=${searchWindowInfo.trigger}, " +
                    "session=${macFocusSession?.let { System.identityHashCode(it) }}, " +
                    "mainShow=${appWindowManager.getCurrentMainWindowInfo().show}"
            }
        }
        // Windows and macOS activation starts after the window is registered and showing.
        // Their focus sessions protect startup without a timed grace period.
        if (isWindows || isMac) return@LaunchedEffect
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
        // settings window (AWT setVisible would otherwise auto-request focus).
        // While the modal bubble is open, a click must not activate the search window
        // either: it would flash as it takes focus and hands it straight back.
        focusable = searchWindowInfo.trigger != WindowTrigger.PREVIEW && !bubbleWindowInfo.show,
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

                fun macWindowLabel(value: Any?): String =
                    when (value) {
                        null -> "null"
                        window -> "search"
                        appWindowManager.bubbleComposeWindow -> "bubble"
                        appWindowManager.mainComposeWindow -> "main"
                        else -> "${value.javaClass.simpleName}@${System.identityHashCode(value)}"
                    }

                fun deferMacFocusLoss(
                    session: MacSearchWindowFocusSession,
                    source: String,
                ) {
                    val sessionId = System.identityHashCode(session)
                    val appearance = appWindowManager.getCurrentSearchWindowInfo()
                    logger.info {
                        "Mac search focus loss requested: source=$source, session=$sessionId, " +
                            "show=${appWindowManager.getCurrentSearchWindowInfo().show}"
                    }
                    session.deferFocusLoss(
                        enqueue = { check ->
                            logger.info { "Mac search focus recheck queued: source=$source, session=$sessionId" }
                            EventQueue.invokeLater {
                                logger.info {
                                    "Mac search focus recheck dispatched: source=$source, session=$sessionId, " +
                                        "currentSession=${currentMacFocusSession?.let { System.identityHashCode(it) }}"
                                }
                                check()
                            }
                        },
                        // Visibility can change twice before Compose replaces the focus session.
                        // Reject the old appearance even when hide/show was coalesced into one frame.
                        isCurrentAppearance = {
                            appearance.show && appWindowManager.getCurrentSearchWindowInfo() === appearance
                        },
                        shouldDismiss = {
                            val eligible =
                                listenerActive.get() &&
                                    currentMacFocusSession === session &&
                                    window.isShowing
                            if (eligible) {
                                val startedAt = System.nanoTime()
                                val focus =
                                    MacSearchWindowFocusState(
                                        appActive = MacAppUtils.isApplicationActive(),
                                        searchFocused = window.hasFocusedWindow(),
                                        bubbleFocused =
                                            appWindowManager.bubbleComposeWindow?.hasFocusedWindow() == true,
                                        mainFocused = appWindowManager.mainComposeWindow?.hasFocusedWindow() == true,
                                        pointerOnStatusItem = MacAppUtils.isPointerOnStatusItem(),
                                    )
                                val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000.0
                                logger.info {
                                    "Mac search focus recheck result: source=$source, session=$sessionId, " +
                                        "elapsedMs=$elapsedMs, state=$focus, dismiss=${focus.shouldDismiss}, " +
                                        "show=${appWindowManager.getCurrentSearchWindowInfo().show}"
                                }
                                focus.shouldDismiss
                            } else {
                                logger.info {
                                    "Mac search focus recheck ineligible: source=$source, session=$sessionId, " +
                                        "listenerActive=${listenerActive.get()}, " +
                                        "currentSession=${currentMacFocusSession === session}, showing=${window.isShowing}"
                                }
                                false
                            }
                        },
                        dismiss = {
                            logger.info {
                                "Search window lost focus after macOS focus recheck: source=$source, session=$sessionId"
                            }
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

                val macFocusListener =
                    if (isMac) {
                        logger.info {
                            "Mac search diagnostics enabled: osVersion=${System.getProperty("os.version")}, " +
                                "javaRuntime=${System.getProperty("java.runtime.version")}, " +
                                "javaVendor=${System.getProperty("java.vendor")}, " +
                                "appEnv=${System.getProperty("appEnv")}"
                        }
                        object : AppForegroundListener, PropertyChangeListener {
                            override fun appRaisedToForeground(e: AppForegroundEvent) {
                                logger.info { "Mac application raised to foreground" }
                            }

                            override fun appMovedToBackground(e: AppForegroundEvent) {
                                logger.info { "Mac application moved to background" }
                                // App deactivation may follow key-window loss without another
                                // windowLostFocus event (for example, after a tray menu closes).
                                currentMacFocusSession?.let { deferMacFocusLoss(it, "appMovedToBackground") }
                            }

                            override fun propertyChange(e: PropertyChangeEvent) {
                                logger.info {
                                    "Mac focusedWindow changed: old=${macWindowLabel(e.oldValue)}, " +
                                        "new=${macWindowLabel(e.newValue)}"
                                }
                                // A tray menu may open the main window after the search
                                // window has already lost key-window focus.
                                if (e.newValue != null) {
                                    currentMacFocusSession?.let { deferMacFocusLoss(it, "focusedWindow") }
                                }
                            }
                        }.also {
                            Desktop.getDesktop().addAppEventListener(it)
                            KeyboardFocusManager
                                .getCurrentKeyboardFocusManager()
                                .addPropertyChangeListener("focusedWindow", it)
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
                            currentMacFocusSession?.onFocusGained()
                            if (isMac) {
                                logger.info {
                                    "Mac search gained focus: opposite=${macWindowLabel(e.oppositeWindow)}, " +
                                        "session=${currentMacFocusSession?.let { System.identityHashCode(it) }}"
                                }
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
                            if (isMac) {
                                logger.info {
                                    "Mac search lost focus: opposite=${macWindowLabel(e.oppositeWindow)}, " +
                                        "session=${currentMacFocusSession?.let { System.identityHashCode(it) }}"
                                }
                                val macSession = currentMacFocusSession ?: return
                                val bubble = appWindowManager.bubbleComposeWindow
                                if (bubble != null && e.oppositeWindow == bubble) return
                                deferMacFocusLoss(macSession, "windowLostFocus")
                                return
                            }
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
                    macFocusListener?.let {
                        Desktop.getDesktop().removeAppEventListener(it)
                        KeyboardFocusManager
                            .getCurrentKeyboardFocusManager()
                            .removePropertyChangeListener("focusedWindow", it)
                    }
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

            LaunchedEffect(macFocusSession) {
                if (macFocusSession != null) {
                    pasteSelectionViewModel.resetToTop()
                    awaitWindowShowing(window)
                    appWindowManager.focusSearchWindow(searchWindowInfo.trigger)
                }
            }

            // The search window cannot take focus while the bubble is open, so the OS may
            // not hand focus back to it when the bubble closes: take it back explicitly.
            // When focus moves to another app, hideSearchWindow() closes the bubble and
            // then the search window in one go, so show is already false here and nothing
            // is reactivated. A preview never takes focus. SYSTEM rather than the opening
            // trigger: on X11 a SHORTCUT trigger would resend that shortcut's old
            // timestamp, which the window manager is more likely to refuse by now.
            var bubbleWasShown by remember { mutableStateOf(false) }
            LaunchedEffect(bubbleWindowInfo.show) {
                if (bubbleWindowInfo.show) {
                    bubbleWasShown = true
                } else if (bubbleWasShown) {
                    bubbleWasShown = false
                    val current = appWindowManager.getCurrentSearchWindowInfo()
                    if (current.show && current.trigger != WindowTrigger.PREVIEW && !window.isFocused) {
                        appWindowManager.focusSearchWindow(WindowTrigger.SYSTEM)
                    }
                }
            }

            SearchWindowContext(searchWindowInfo) {
                Box {
                    SideSearchWindowContent()
                    if (bubbleWindowInfo.show) {
                        BubbleModalBarrier(onPress = appWindowManager::requestBubbleAttention)
                    }
                }
            }
        }
    }
}

/**
 * Swallows every pointer event over the search window while the bubble is open, so
 * the search window cannot be used until the bubble is handled or closed.
 */
@Composable
private fun BubbleModalBarrier(onPress: () -> Unit) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type == PointerEventType.Press) {
                                onPress()
                            }
                            event.changes.forEach { it.consume() }
                        }
                    }
                },
    )
}

/** Owned popup windows can take AWT focus while interaction stays within the search UI. */
internal fun Window.hasFocusedWindow(): Boolean = isFocused || ownedWindows.any { it.hasFocusedWindow() }

@Composable
fun MacAcrylicEffect(
    window: ComposeWindow,
    isDark: Boolean? = null,
    levelOffset: Int = 0,
) {
    LaunchedEffect(window, isDark, levelOffset) {
        snapshotFlow { window.isDisplayable }
            .first { it }

        if (isDark != null) {
            window.background = java.awt.Color(0, 0, 0, 0)
        }

        withContext(cpuDispatcher) {
            runCatching {
                val pointer = Pointer(window.windowHandle)
                MacAppUtils.setWindowLevelPopUpMenu(pointer, levelOffset)
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
