package com.crosspaste.notification

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.crosspaste.ui.LocalThemeExtState

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

    @Composable
    fun getMessageColor(): Pair<Color, Color> =
        when (this) {
            Error -> Pair(ErrorContainer(), OnErrorContainer())
            Info -> Pair(InfoContainer(), OnInfoContainer())
            Success -> Pair(SuccessContainer(), OnSuccessContainer())
            Warning -> Pair(WarningContainer(), OnWarningContainer())
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

@Composable
fun SuccessContainer(): Color = LocalThemeExtState.current.success.container

@Composable
fun OnSuccessContainer(): Color = LocalThemeExtState.current.success.onContainer

@Composable
fun ErrorContainer(): Color = MaterialTheme.colorScheme.errorContainer

@Composable
fun OnErrorContainer(): Color = MaterialTheme.colorScheme.onErrorContainer

@Composable
fun WarningContainer(): Color = LocalThemeExtState.current.warning.container

@Composable
fun OnWarningContainer(): Color = LocalThemeExtState.current.warning.onContainer

@Composable
fun InfoContainer(): Color = LocalThemeExtState.current.info.container

@Composable
fun OnInfoContainer(): Color = LocalThemeExtState.current.info.onContainer
