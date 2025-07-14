package com.crosspaste.ui.search.center

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import com.crosspaste.app.DesktopAppSize
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.platform.Platform
import com.crosspaste.ui.model.PasteSelectionViewModel
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUISize.small3XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.tiny5X
import com.crosspaste.ui.theme.DesktopAppUIColors
import com.crosspaste.utils.GlobalCoroutineScope.mainCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun CenterSearchWindowContent() {
    val appSize = koinInject<DesktopAppSize>()
    val appWindowManager = koinInject<DesktopAppWindowManager>()
    val pasteSelectionViewModel = koinInject<PasteSelectionViewModel>()
    val platform = koinInject<Platform>()

    val isLinux by remember { mutableStateOf(platform.isLinux()) }

    val showSearchWindow by appWindowManager.showSearchWindow.collectAsState()

    val focusManager = LocalFocusManager.current

    LaunchedEffect(showSearchWindow) {
        appWindowManager.searchComposeWindow?.let {
            if (showSearchWindow) {
                it.toFront()
                it.requestFocus()
                delay(160)
                pasteSelectionViewModel.requestSearchInputFocus()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            focusManager.clearFocus()
        }
    }

    val modifier =
        Modifier
            .background(
                if (isLinux) {
                    DesktopAppUIColors.searchBackground
                } else {
                    Color.Transparent
                },
            ).size(appSize.centerSearchWindowSize)
            .onKeyEvent {
                when (it.key) {
                    Key.Enter -> {
                        mainCoroutineDispatcher.launch {
                            pasteSelectionViewModel.toPaste()
                        }
                        true
                    }
                    Key.DirectionUp -> {
                        pasteSelectionViewModel.selectPrev()
                        true
                    }
                    Key.DirectionDown -> {
                        pasteSelectionViewModel.selectNext()
                        true
                    }
                    Key.N -> {
                        if (it.isCtrlPressed) {
                            pasteSelectionViewModel.selectNext()
                        }
                        true
                    }
                    Key.P -> {
                        if (it.isCtrlPressed) {
                            pasteSelectionViewModel.selectPrev()
                        }
                        true
                    }
                    else -> {
                        false
                    }
                }
            }

    Box(
        modifier =
            if (isLinux) {
                modifier
                    .border(tiny5X, AppUIColors.lightBorderColor)
            } else {
                modifier
                    .clip(small3XRoundedCornerShape)
                    .border(tiny5X, AppUIColors.lightBorderColor, small3XRoundedCornerShape)
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(DesktopAppUIColors.searchBackground),
            contentAlignment = Alignment.Center,
        ) {
            Column {
                SearchInputView()

                Row(modifier = Modifier.size(appSize.centerSearchCoreContentSize)) {
                    SearchListView {
                        pasteSelectionViewModel.clickSelectedIndex(it)
                    }
                    VerticalDivider(thickness = tiny5X)
                    DetailPasteDataView()
                }

                SearchFooterView()
            }
        }
    }
}
