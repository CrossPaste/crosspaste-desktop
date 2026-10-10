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
        MessageType.Error ->
            MaterialTheme.colorScheme.let { Pair(it.errorContainer, it.onErrorContainer) }
        MessageType.Info ->
            LocalThemeExtState.current.info.let { Pair(it.container, it.onContainer) }
        MessageType.Success ->
            LocalThemeExtState.current.success.let { Pair(it.container, it.onContainer) }
        MessageType.Warning ->
            LocalThemeExtState.current.warning.let { Pair(it.container, it.onContainer) }
    }

fun MessageType.getMessageImageVector(): ImageVector =
    when (getMessageStyle()) {
        MessageStyle.Error -> MaterialSymbols.RoundedFilled.Error
        MessageStyle.Info -> MaterialSymbols.RoundedFilled.Info
        MessageStyle.Success -> MaterialSymbols.RoundedFilled.Check_circle
        MessageStyle.Warning -> MaterialSymbols.RoundedFilled.Warning
    }
