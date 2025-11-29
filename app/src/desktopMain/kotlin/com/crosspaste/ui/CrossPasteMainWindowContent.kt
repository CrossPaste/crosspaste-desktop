package com.crosspaste.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.crosspaste.app.DesktopAppSize
import com.crosspaste.ui.base.DialogView
import com.crosspaste.ui.theme.AppUIColors
import org.koin.compose.koinInject

@Composable
fun CrossPasteMainWindowContent() {
    val appSize = koinInject<DesktopAppSize>()
    val screenProvider = koinInject<DesktopScreenProvider>()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier =
                Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxSize(),
            ) {
                Row(
                    modifier =
                        Modifier
                            .width(appSize.mainMenuSize.width)
                            .fillMaxHeight()
                            .background(AppUIColors.generalBackground),
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(appSize.windowDecorationHeight)
                                    .offset(y = -appSize.windowDecorationHeight)
                                    .background(AppUIColors.generalBackground),
                        ) {}

                        MainMenuView()
                    }
                }
                Box(
                    modifier =
                        Modifier
                            .width(appSize.mainContentSize.width)
                            .fillMaxHeight()
                            .background(AppUIColors.appBackground),
                ) {
                    screenProvider.screen()
                }
            }
        }

        DialogView()

        screenProvider.ToastView()

        screenProvider.TokenView()

        screenProvider.DragTargetView()
    }
}
