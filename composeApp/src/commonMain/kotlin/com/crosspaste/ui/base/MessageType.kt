package com.crosspaste.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import com.crosspaste.ui.base.MessageStyle.Error
import com.crosspaste.ui.base.MessageStyle.Info
import com.crosspaste.ui.base.MessageStyle.Success
import com.crosspaste.ui.base.MessageStyle.Warning

enum class MessageType {
    Error,
    Info,
    Success,
    Warning,
    ;

    fun getMessageStyle(): MessageStyle {
        return when (this) {
            Error -> MessageStyle.Error
            Info -> MessageStyle.Info
            Success -> MessageStyle.Success
            Warning -> MessageStyle.Warning
        }
    }
}

enum class MessageStyle(val messageColor: Color) {
    Error(Color.Red),
    Info(Color.Blue),
    Success(Color.Green),
    Warning(Color.Yellow),
}

@Composable
fun getMessagePainter(messageStyle: MessageStyle): Painter {
    return when (messageStyle) {
        Error -> error()
        Info -> info()
        Success -> success()
        Warning -> warning()
    }
}
