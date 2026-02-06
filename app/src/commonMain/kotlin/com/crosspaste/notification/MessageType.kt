package com.crosspaste.notification

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.roundedfilled.Check_circle
import com.composables.icons.materialsymbols.roundedfilled.Error
import com.composables.icons.materialsymbols.roundedfilled.Info
import com.composables.icons.materialsymbols.roundedfilled.Warning
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
        MessageStyle.Error -> MaterialSymbols.RoundedFilled.Error
        MessageStyle.Info -> MaterialSymbols.RoundedFilled.Info
        MessageStyle.Success -> MaterialSymbols.RoundedFilled.Check_circle
        MessageStyle.Warning -> MaterialSymbols.RoundedFilled.Warning
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
