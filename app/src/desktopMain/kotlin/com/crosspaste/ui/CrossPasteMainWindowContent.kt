package com.crosspaste.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import com.crosspaste.app.DesktopAppSize
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.ui.base.DialogView
import com.crosspaste.ui.base.ToastListView
import com.crosspaste.ui.devices.TokenView
import com.crosspaste.ui.theme.AppUISize.small3XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.tiny5X
import com.crosspaste.ui.theme.CrossPasteTheme.Theme
import com.crosspaste.utils.GlobalCoroutineScope.mainCoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

fun hideWindow(appWindowManager: DesktopAppWindowManager) {
    mainCoroutineDispatcher.launch(CoroutineName("Hide CrossPaste")) {
        appWindowManager.unActiveMainWindow()
    }
}

@Composable
fun CrossPasteMainWindowContent() {
    val appSize = koinInject<DesktopAppSize>()
    val appWindowManager = koinInject<DesktopAppWindowManager>()
    val screenProvider = koinInject<ScreenProvider>()

    val showMainWindow by appWindowManager.showMainWindow.collectAsState()

    val focusManager = LocalFocusManager.current

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(showMainWindow) {
        appWindowManager.mainComposeWindow?.let {
            if (showMainWindow) {
                it.toFront()
                it.requestFocus()
                delay(160)
                focusRequester.requestFocus()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            focusManager.clearFocus()
        }
    }

    Theme {
        Box(
            modifier =
                Modifier
                    .background(Color.Transparent)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                hideWindow(appWindowManager)
                            },
                            onTap = {
                                hideWindow(appWindowManager)
                            },
                            onLongPress = {
                                hideWindow(appWindowManager)
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
                        .border(
                            width = tiny5X,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            shape = small3XRoundedCornerShape,
                        )
                        .pointerInput(Unit) {
                            // Avoid click-through
                            detectTapGestures()
                        },
                contentAlignment = Alignment.Center,
            ) {
                ThemeBackground()

                Column(
                    Modifier
                        .clip(appSize.appRoundedCornerShape)
                        .fillMaxWidth()
                        .focusable()
                        .focusRequester(focusRequester),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    screenProvider.CrossPasteScreen()
                }

                ToastListView()

                DialogView()

                TokenView()
            }
        }
    }
}
