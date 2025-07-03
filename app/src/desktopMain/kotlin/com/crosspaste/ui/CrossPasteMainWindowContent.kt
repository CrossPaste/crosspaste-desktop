package com.crosspaste.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import com.crosspaste.app.DesktopAppSize
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.ui.base.DialogView
import com.crosspaste.ui.base.ToastListView
import com.crosspaste.ui.devices.TokenView
import com.crosspaste.ui.theme.AppUIColors
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

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

    Box(
        modifier =
            Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier =
                Modifier.fillMaxSize()
                    .focusable()
                    .focusRequester(focusRequester),
        ) {
            Row(
                modifier =
                    Modifier.width(appSize.mainMenuSize.width)
                        .fillMaxHeight()
                        .background(AppUIColors.generalBackground),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .height(appSize.windowDecorationHeight)
                                .offset(y = -appSize.windowDecorationHeight)
                                .background(AppUIColors.generalBackground),
                    ) {}

                    MainMenuView()
                }
            }
            Box(
                modifier =
                    Modifier.width(appSize.mainContentSize.width)
                        .fillMaxHeight()
                        .background(AppUIColors.appBackground),
            ) {
                screenProvider.CrossPasteScreen()
            }
        }

        ToastListView()

        DialogView()

        TokenView()
    }
}
