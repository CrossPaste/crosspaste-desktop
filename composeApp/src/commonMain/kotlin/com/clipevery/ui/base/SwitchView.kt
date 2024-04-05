package com.clipevery.ui.base

import androidx.compose.foundation.Canvas
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CustomSwitch(checked: Boolean,
                 onCheckedChange: (Boolean) -> Unit,
                 modifier: Modifier = Modifier,
                 checkedThumbColor: Color = MaterialTheme.colors.primary,
                 uncheckedThumbColor: Color = MaterialTheme.colors.background) {

    val trackColor = if (checked) checkedThumbColor else Color(0xFFAFCBE1)

    Canvas(modifier = modifier
        .onPointerEvent(PointerEventType.Press) {
            onCheckedChange(!checked)
        }
    ) {
        // Draw the track
        drawRoundRect(
            color = trackColor,
            size = size,
            cornerRadius = CornerRadius(x = size.height / 2, y = size.height / 2)
        )

        // Calculate the knob position
        val knobOffset = if (checked) size.width - size.height else 0f

        // Draw the knob
        drawCircle(
            color = uncheckedThumbColor,
            radius = size.height / 2 - 2.dp.toPx(),
            center = Offset(knobOffset + size.height / 2, size.height / 2)
        )
    }
}