package com.clipevery.ui.base

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.clipevery.ui.devices.measureTextWidth

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CustomSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    checkedThumbColor: Color = MaterialTheme.colors.primary,
    uncheckedThumbColor: Color = MaterialTheme.colors.background,
) {
    val trackColor = if (checked) checkedThumbColor else Color(0xFFAFCBE1)

    Canvas(
        modifier =
            modifier
                .onPointerEvent(PointerEventType.Press) {
                    onCheckedChange(!checked)
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
    val checkedThumbColor = MaterialTheme.colors.primary

    val uncheckedThumbColor = MaterialTheme.colors.background

    val maxWidth = max(measureTextWidth(checkedText, textStyle), measureTextWidth(uncheckedText, textStyle))

    println("maxWidth = $maxWidth")

    Box(modifier = modifier) {
        Canvas(
            modifier =
                modifier
                    .clickable(onClick = { onCheckedChange(!checked) })
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
