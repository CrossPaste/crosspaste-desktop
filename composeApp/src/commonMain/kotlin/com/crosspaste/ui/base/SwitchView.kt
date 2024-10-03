package com.crosspaste.ui.base

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max

@Composable
fun CustomSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    checkedThumbColor: Color = MaterialTheme.colorScheme.primary,
    uncheckedThumbColor: Color = MaterialTheme.colorScheme.background,
) {
    val trackColor = if (checked) checkedThumbColor else Color(0xFFAFCBE1)

    Canvas(
        modifier =
            modifier.pointerInput(checked) {
                detectTapGestures(
                    onPress = {
                        onCheckedChange(!checked)
                    },
                )
            },
    ) {
        // Draw the track
        drawRoundRect(
            color = trackColor,
            size = size,
            cornerRadius = CornerRadius(x = size.height / 2, y = size.height / 2),
        )

        // Calculate the knob position
        val knobOffset = if (checked) size.width - size.height else 0f

        // Draw the knob
        drawCircle(
            color = uncheckedThumbColor,
            radius = size.height / 2 - 2.dp.toPx(),
            center = Offset(knobOffset + size.height / 2, size.height / 2),
        )
    }
}

@Composable
fun CustomRectangleSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle,
    checkedText: String = "ON",
    uncheckedText: String = "OFF",
) {
    val checkedThumbColor = MaterialTheme.colorScheme.primary

    val uncheckedThumbColor = MaterialTheme.colorScheme.background

    val maxWidth = max(measureTextWidth(checkedText, textStyle), measureTextWidth(uncheckedText, textStyle))

    Box(modifier = modifier) {
        Canvas(
            modifier =
                modifier
                    .clickable {
                        onCheckedChange(!checked)
                    }
                    .padding(horizontal = 4.dp),
        ) {
            val trackHeight = size.height
            val trackWidth = size.width
            val cornerRadius = CornerRadius(x = 4.dp.toPx(), y = 4.dp.toPx())

            val thumbWidth = size.width - maxWidth.toPx() - 12.dp.toPx()

            // Draw the background track
            drawRoundRect(
                color = checkedThumbColor,
                size = Size(width = trackWidth, height = trackHeight),
                cornerRadius = cornerRadius,
            )

            val knobOffset = if (checked) maxWidth.toPx() + 8.dp.toPx() else 4.dp.toPx()

            // Draw the knob
            drawRoundRect(
                topLeft = Offset(knobOffset, 2.dp.toPx()),
                color = uncheckedThumbColor,
                size = Size(width = thumbWidth, height = trackHeight - 4.dp.toPx()),
                cornerRadius = cornerRadius,
            )
        }

        if (checked) {
            Row(
                modifier = modifier,
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(modifier = Modifier.padding(4.dp))
                Text(
                    text = checkedText,
                    style = textStyle,
                    color = Color.White,
                )
            }
        } else {
            Row(
                modifier = modifier,
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = uncheckedText,
                    style = textStyle,
                    color = Color.White,
                )
                Spacer(modifier = Modifier.padding(4.dp))
            }
        }
    }
}
