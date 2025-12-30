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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.crosspaste.ui.theme.AppUIColors
import org.koin.compose.koinInject

@Composable
fun CrossPasteMainWindowContent() {
    val screenProvider = koinInject<DesktopScreenProvider>()

    val appSizeValue = LocalDesktopAppSizeValueState.current

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
                            .width(appSizeValue.mainMenuSize.width)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(appSizeValue.windowDecorationHeight)
                                    .offset(y = -appSizeValue.windowDecorationHeight)
                                    .background(MaterialTheme.colorScheme.surfaceContainer),
                        ) {}

                        MainMenuView()
                    }
                }
                Box(
                    modifier =
                        Modifier
                            .width(appSizeValue.mainContentSize.width)
                            .fillMaxHeight()
                            .background(AppUIColors.appBackground),
                ) {
                    WindowDecoration()
                    screenProvider.screen()
                }
            }
        }

        NotificationHost()

        screenProvider.TokenView()

        screenProvider.DragTargetView()
    }
}
