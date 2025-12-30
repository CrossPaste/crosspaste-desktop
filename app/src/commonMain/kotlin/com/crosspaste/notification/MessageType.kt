package com.crosspaste.notification

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

enum class MessageType {
    Error,
    Info,
    Success,
    Warning,
    ;

    fun getMessageStyle(): MessageStyle =
        when (this) {
            Error -> MessageStyle.Error
            Info -> MessageStyle.Info
            Success -> MessageStyle.Success
            Warning -> MessageStyle.Warning
        }
}

enum class MessageStyle {
    Error,
    Info,
    Success,
    Warning,
}

@Composable
fun getMessageImageVector(messageStyle: MessageStyle): ImageVector =
    when (messageStyle) {
        MessageStyle.Error -> Icons.Default.Error
        MessageStyle.Info -> Icons.Default.Info
        MessageStyle.Success -> Icons.Default.CheckCircle
        MessageStyle.Warning -> Icons.Default.Warning
    }
