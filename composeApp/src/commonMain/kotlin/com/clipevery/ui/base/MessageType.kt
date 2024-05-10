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
            Error -> MessageStyle.error
            Info -> MessageStyle.info
            Success -> MessageStyle.success
            Warning -> MessageStyle.warning
        }
    }
}

enum class MessageStyle(val iconFileName: String, val messageColor: Color) {
    error("icon/toast/error.svg", Color.Red),
    info("icon/toast/info.svg", Color.Blue),
    success("icon/toast/success.svg", Color.Green),
    warning("icon/toast/warning.svg", Color.Yellow),
}
