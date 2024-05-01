package com.clipevery.ui.base

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipevery.ui.devices.measureTextWidth

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
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val backgroundColor = if (isHovered) MaterialTheme.colors.secondaryVariant else Color.Transparent

    Text(
        text = text,
        color = MaterialTheme.colors.onBackground,
        style = textStyle,
        modifier =
            Modifier
                .fillMaxWidth()
                .hoverable(interactionSource = interactionSource)
                .background(backgroundColor)
                .clickable(onClick = onClick)
                .padding(paddingValues),
    )
}

@Composable
fun getMenWidth(
    array: Array<String>,
    textStyle: TextStyle = menuItemTextStyle,
    paddingValues: PaddingValues = PaddingValues(16.dp, 8.dp, 16.dp, 8.dp),
): Dp {
    var maxWidth = 0.dp
    for (text in array) {
        maxWidth = maxOf(maxWidth, measureTextWidth(text, textStyle))
    }
    return maxWidth + paddingValues.calculateLeftPadding(LayoutDirection.Ltr) + paddingValues.calculateRightPadding(LayoutDirection.Ltr)
}
