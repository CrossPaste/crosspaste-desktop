package com.crosspaste.notification

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import com.crosspaste.ui.base.error
import com.crosspaste.ui.base.info
import com.crosspaste.ui.base.success
import com.crosspaste.ui.base.warning

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

enum class MessageStyle(val messageColor: Color, val targetHue: Float) {
    Error(Color.Red, 0f),
    Info(Color.Blue, 240f),
    Success(Color.Green, 120f),
    Warning(Color.Yellow, 60f),
}

@Composable
fun getMessagePainter(messageStyle: MessageStyle): Painter {
    return when (messageStyle) {
        MessageStyle.Error -> error()
        MessageStyle.Info -> info()
        MessageStyle.Success -> success()
        MessageStyle.Warning -> warning()
    }
}
