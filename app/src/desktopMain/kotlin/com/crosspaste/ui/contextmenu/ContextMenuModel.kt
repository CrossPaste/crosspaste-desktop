package com.crosspaste.ui.contextmenu

import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class ContextMenuMeasurements(
    val minWidth: Dp = 112.dp,
    val maxWidth: Dp = 280.dp,
    val itemMinHeight: Dp = 48.dp,
    val itemPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    val itemHorizontalMargin: Dp = 4.dp,
    val itemShape: Shape = RoundedCornerShape(4.dp),
    val menuTopPadding: Dp = 8.dp,
    val menuBottomPadding: Dp = 8.dp,
    val iconPadding: Dp = 12.dp,
    val submenuIconSize: Dp = 16.dp,
    val elevation: Dp = 8.dp,
    val windowMargin: Dp = 4.dp,
    val popupShape: Shape = RoundedCornerShape(8.dp),
    val dividerLineHeight: Dp = 1.dp,
    val dividerHeight: Dp = 16.dp,
)

@Immutable
data class ContextMenuColors(
    val surface: Color,
    val text: Color,
    val itemHover: Color,
    val itemHoverText: Color = text,
    val divider: Color = text.copy(alpha = 0.12f),
)

@Immutable
data class ContextMenuParams(
    val measurements: ContextMenuMeasurements,
    val colors: ContextMenuColors,
    val showScrollbarOnOverflow: Boolean = true,
)

object ContextMenuDivider : ContextMenuItem(
    label = "",
    onClick = { error("divider should not be clickable") },
)

open class MaterialContextMenuItem(
    label: String,
    onClick: () -> Unit,
    val leadingIcon: (@Composable () -> Unit)? = null,
    val trailingIcon: (@Composable () -> Unit)? = null,
) : ContextMenuItem(label = label, onClick = onClick)

open class ContextMenuGroup(
    label: String,
    val items: () -> List<ContextMenuItem>,
) : ContextMenuItem(label = label, onClick = { error("group should not be clickable") })

abstract class GenericContextMenuItem :
    ContextMenuItem(
        label = "",
        onClick = { error("generic item should not be clickable") },
    ) {
    @Composable
    abstract fun Content(
        onDismissRequest: () -> Unit,
        params: ContextMenuParams,
        modifier: Modifier,
    )
}
