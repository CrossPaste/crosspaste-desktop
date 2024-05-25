package com.clipevery.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.DialogState
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.DialogWindowScope
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import com.clipevery.LocalKoinApplication
import com.clipevery.app.AppWindowManager

@Composable
fun ClipDialogWindowView(
    onCloseRequest: () -> Unit,
    title: String,
    size: DpSize,
    content: @Composable DialogWindowScope.() -> Unit,
) {
    val current = LocalKoinApplication.current
    val appWindowManager = current.koin.get<AppWindowManager>()
    val state: DialogState = rememberDialogState()

    state.size = size
    state.position =
        getDialogWindowPosition(
            mainWindowPosition = appWindowManager.mainWindowPosition,
            mainWindowSize = appWindowManager.mainWindowDpSize,
            size = size,
        )

    DisposableEffect(Unit) {
        appWindowManager.showMainDialog = true
        onDispose {
            appWindowManager.showMainDialog = false
        }
    }

    DialogWindow(
        onCloseRequest = onCloseRequest,
        title = title,
        state = state,
        resizable = false,
        alwaysOnTop = true,
    ) {
        content()
    }
}

fun getDialogWindowPosition(
    mainWindowPosition: WindowPosition,
    mainWindowSize: DpSize,
    size: DpSize,
): WindowPosition {
    return WindowPosition(
        x = mainWindowPosition.x + (mainWindowSize.width - size.width) / 2,
        y = mainWindowPosition.y + (mainWindowSize.height - size.height) / 2,
    )
}
