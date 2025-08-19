package com.crosspaste.ui.search.side

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.ui.model.PasteSelectionViewModel
import com.crosspaste.ui.paste.side.SidePasteboardContentView
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.CrossPasteTheme.Theme
import com.crosspaste.utils.GlobalCoroutineScope.mainCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun SideSearchWindowContent() {
    val appWindowManager = koinInject<DesktopAppWindowManager>()

    val pasteSelectionViewModel = koinInject<PasteSelectionViewModel>()

    val showSearchWindow by appWindowManager.showSearchWindow.collectAsState()

    val focusManager = LocalFocusManager.current

    LaunchedEffect(showSearchWindow) {
        appWindowManager.searchComposeWindow?.let {
            if (showSearchWindow) {
                it.toFront()
                it.requestFocus()
                delay(160)
                pasteSelectionViewModel.requestPasteListFocus()
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
                    .fillMaxSize()
                    .background(AppUIColors.generalBackground)
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            when (keyEvent.key) {
                                Key.Enter -> {
                                    mainCoroutineDispatcher.launch {
                                        pasteSelectionViewModel.toPaste()
                                    }
                                    true
                                }
                                Key.DirectionUp -> {
                                    pasteSelectionViewModel.requestSearchInputFocus()
                                    true
                                }
                                Key.DirectionLeft -> {
                                    pasteSelectionViewModel.selectPrev()
                                    true
                                }
                                Key.DirectionRight -> {
                                    pasteSelectionViewModel.selectNext()
                                    true
                                }
                                Key.N -> {
                                    if (keyEvent.isCtrlPressed) {
                                        pasteSelectionViewModel.selectNext()
                                    }
                                    true
                                }
                                Key.P -> {
                                    if (keyEvent.isCtrlPressed) {
                                        pasteSelectionViewModel.selectPrev()
                                    }
                                    true
                                }
                                else -> {
                                    false
                                }
                            }
                        } else {
                            false
                        }
                    },
        ) {
            Column(
                modifier =
                    Modifier.fillMaxSize(),
            ) {
                SideSearchInputView()
                SidePasteboardContentView()
            }
        }
    }
}
