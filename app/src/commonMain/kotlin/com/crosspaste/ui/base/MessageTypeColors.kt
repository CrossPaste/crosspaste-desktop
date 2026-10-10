package com.crosspaste.ui.base

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.roundedfilled.Check_circle
import com.composables.icons.materialsymbols.roundedfilled.Error
import com.composables.icons.materialsymbols.roundedfilled.Info
import com.composables.icons.materialsymbols.roundedfilled.Warning
import com.crosspaste.notification.MessageStyle
import com.crosspaste.notification.MessageType
import com.crosspaste.ui.LocalThemeExtState

/**
 * Compose presentation of [MessageType] / [MessageStyle]. Kept apart from the notification
 * model so that package stays free of Compose types.
 */
@Composable
fun MessageType.getMessageColor(): Pair<Color, Color> =
    when (this) {
        MessageType.Error -> Pair(ErrorContainer(), OnErrorContainer())
        MessageType.Info -> Pair(InfoContainer(), OnInfoContainer())
        MessageType.Success -> Pair(SuccessContainer(), OnSuccessContainer())
        MessageType.Warning -> Pair(WarningContainer(), OnWarningContainer())
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
