package com.clipevery.ui.base

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogState
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.DialogWindowScope
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import com.clipevery.LocalKoinApplication
import com.clipevery.app.AppWindowManager
import com.clipevery.i18n.GlobalCopywriter

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

@Composable
fun DialogButtonsView(
    cancelTitle: String = "No",
    confirmTitle: String = "Yes",
    cancelAction: () -> Unit,
    confirmAction: () -> Unit,
) {
    val current = LocalKoinApplication.current
    val copywriter = current.koin.get<GlobalCopywriter>()
    Column(
        modifier = Modifier.wrapContentSize(),
        verticalArrangement = Arrangement.Center,
    ) {
        Divider(modifier = Modifier.fillMaxWidth().width(1.dp))
        Row(
            modifier = Modifier.fillMaxWidth().height(40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier =
                    Modifier.weight(0.5f)
                        .height(40.dp)
                        .clickable {
                            cancelAction()
                        },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = copywriter.getText(cancelTitle),
                    color = MaterialTheme.colors.primary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Divider(modifier = Modifier.fillMaxHeight().width(1.dp))
            Row(
                modifier =
                    Modifier.weight(0.5f)
                        .height(40.dp)
                        .clickable {
                            confirmAction()
                        },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = copywriter.getText(confirmTitle),
                    color = MaterialTheme.colors.primary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
