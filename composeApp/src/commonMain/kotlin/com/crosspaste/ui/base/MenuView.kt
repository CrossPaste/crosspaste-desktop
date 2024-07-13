package com.crosspaste.ui.base

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crosspaste.ui.devices.measureTextWidth
import com.crosspaste.ui.selectColor

val menuItemTextStyle =
    TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Light,
        fontFamily = FontFamily.SansSerif,
    )

@Composable
fun MenuItem(
    text: String,
    textStyle: TextStyle = menuItemTextStyle,
    paddingValues: PaddingValues = PaddingValues(16.dp, 8.dp, 16.dp, 8.dp),
    enabledInteraction: Boolean = true,
    reminder: Boolean = false,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val backgroundColor = if (enabledInteraction && isHovered) MaterialTheme.colors.selectColor() else Color.Transparent

    var modifier =
        Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .hoverable(interactionSource = interactionSource)
            .background(backgroundColor)

    if (enabledInteraction) {
        modifier = modifier.clickable(onClick = onClick)
    }

    Row(
        modifier = modifier.padding(paddingValues),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = MaterialTheme.colors.onBackground,
            style = textStyle,
        )
        if (reminder) {
            Spacer(modifier = Modifier.weight(16f))
            Icon(
                painter = circle(),
                contentDescription = "reminder",
                modifier = Modifier.size(8.dp),
                tint = Color.Red,
            )
        }
    }
}

@Composable
fun getMenWidth(
    array: Array<String>,
    textStyle: TextStyle = menuItemTextStyle,
    paddingValues: PaddingValues = PaddingValues(16.dp, 8.dp, 16.dp, 8.dp),
    extendFunction: (Int) -> Dp = { 0.dp },
): Dp {
    var maxWidth = 0.dp
    array.forEachIndexed { index, text ->
        maxWidth = maxOf(maxWidth, measureTextWidth(text, textStyle) + extendFunction(index))
    }
    return maxWidth +
        paddingValues.calculateLeftPadding(LayoutDirection.Ltr) +
        paddingValues.calculateRightPadding(LayoutDirection.Ltr)
}
