package com.crosspaste.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import com.crosspaste.app.AppSize
import com.crosspaste.app.AppTokenService
import com.crosspaste.app.DesktopAppSize
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.app.DesktopAppWindowManager.Companion.MAIN_WINDOW_TITLE
import com.crosspaste.app.ExitMode
import com.crosspaste.listener.GlobalListener
import com.crosspaste.ui.base.DialogService
import com.crosspaste.ui.base.ToastManager
import com.crosspaste.ui.base.ToastView
import com.crosspaste.ui.devices.TokenView
import com.crosspaste.utils.GlobalCoroutineScope
import com.crosspaste.utils.GlobalCoroutineScopeImpl.mainCoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

@Composable
fun CrossPasteMainWindow(
    exitApplication: (ExitMode) -> Unit,
    windowIcon: Painter?,
) {
    val appWindowManager = koinInject<DesktopAppWindowManager>()
    val globalListener = koinInject<GlobalListener>()

    val showMainWindow by appWindowManager.showMainWindow.collectAsState()
    val currentMainWindowState by appWindowManager.mainWindowState.collectAsState()

    Window(
        onCloseRequest = { exitApplication(ExitMode.EXIT) },
        visible = showMainWindow,
        state = currentMainWindowState,
        title = MAIN_WINDOW_TITLE,
        icon = windowIcon,
        alwaysOnTop = true,
        undecorated = true,
        transparent = true,
        resizable = false,
    ) {
        DisposableEffect(Unit) {
            appWindowManager.mainComposeWindow = window

            globalListener.start()

            val windowListener =
                object : WindowAdapter() {
                    override fun windowGainedFocus(e: WindowEvent?) {
                        appWindowManager.setShowMainWindow(true)
                    }

                    override fun windowLostFocus(e: WindowEvent?) {
                        mainCoroutineDispatcher.launch(CoroutineName("Hide CrossPaste")) {
                            if (appWindowManager.getShowMainWindow() &&
                                !appWindowManager.getShowMainDialog() &&
                                !appWindowManager.getShowMainDialog()
                            ) {
                                appWindowManager.unActiveMainWindow()
                            }
                        }
                    }
                }

            window.addWindowFocusListener(windowListener)

            onDispose {
                appWindowManager.mainComposeWindow = null
                window.removeWindowFocusListener(windowListener)
            }
        }
        CrossPasteMainWindowContent { appWindowManager.unActiveMainWindow() }
    }
}

@Composable
fun CrossPasteMainWindowContent(hideWindow: suspend () -> Unit) {
    val appSize = koinInject<AppSize>() as DesktopAppSize
    val appWindowManager = koinInject<DesktopAppWindowManager>()
    val appTokenService = koinInject<AppTokenService>()
    val toastManager = koinInject<ToastManager>()
    val dialogService = koinInject<DialogService>()
    val globalCoroutineScope = koinInject<GlobalCoroutineScope>()
    val mainCoroutineDispatcher = globalCoroutineScope.mainCoroutineDispatcher
    val toast by toastManager.toast

    CrossPasteTheme {
        Box(
            modifier =
                Modifier
                    .background(Color.Transparent)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                mainCoroutineDispatcher.launch(CoroutineName("Hide CrossPaste")) {
                                    hideWindow()
                                }
                            },
                            onTap = {
                                mainCoroutineDispatcher.launch(CoroutineName("Hide CrossPaste")) {
                                    hideWindow()
                                }
                            },
                            onLongPress = {
                                mainCoroutineDispatcher.launch(CoroutineName("Hide CrossPaste")) {
                                    hideWindow()
                                }
                            },
                            onPress = {},
                        )
                    }
                    .clip(RoundedCornerShape(appSize.mainShadowSize))
                    .fillMaxSize()
                    .padding(appSize.mainShadowPaddingValues),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .shadow(appSize.mainShadowSize, appSize.appRoundedCornerShape)
                        .fillMaxSize()
                        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {},
                                onTap = {},
                                onLongPress = {},
                                onPress = {},
                            )
                        },
                contentAlignment = Alignment.Center,
            ) {
                ThemeBackground()

                Column(
                    Modifier
                        .clip(appSize.appRoundedCornerShape)
                        .fillMaxWidth()
                        .focusTarget()
                        .focusRequester(appWindowManager.mainFocusRequester),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CrossPasteScreen()
                }

                toast?.let {
                    ToastView(toast = it) {
                        toastManager.cancel()
                    }
                }

                dialogService.dialogs.firstOrNull()?.content()

                if (appTokenService.showToken) {
                    TokenView()
                }
            }
        }
    }
}
