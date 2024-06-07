package com.clipevery.ui.base

import androidx.compose.ui.graphics.Color

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

enum class MessageStyle(val iconFileName: String, val messageColor: Color) {
    Error("icon/toast/error.svg", Color.Red),
    Info("icon/toast/info.svg", Color.Blue),
    Success("icon/toast/success.svg", Color.Green),
    Warning("icon/toast/warning.svg", Color.Yellow),
}
