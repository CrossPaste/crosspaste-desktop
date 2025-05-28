package com.crosspaste.ui.base

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.crosspaste.ui.theme.AppUISize.large2X
import com.crosspaste.ui.theme.AppUISize.small
import com.crosspaste.ui.theme.AppUISize.smallRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.tiny3X
import com.crosspaste.ui.theme.AppUISize.tiny4X

@Composable
fun CustomSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    colors: SwitchColors = SwitchDefaults.colors(),
) {
    val thumbColor = if (checked) colors.checkedThumbColor else colors.uncheckedThumbColor

    val trackColor = if (checked) colors.checkedTrackColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.36f)

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
            color = thumbColor,
            radius = size.height / 2 - tiny4X.toPx(),
            center = Offset(knobOffset + size.height / 2, size.height / 2),
        )
    }
}

@Composable
fun CustomTextSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle =
        MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.Light,
        ),
    checkedText: String = "ON",
    uncheckedText: String = "OFF",
    checkedThumbColor: Color = MaterialTheme.colorScheme.primary,
    uncheckedThumbColor: Color = MaterialTheme.colorScheme.secondary,
) {
    val switchPadding = tiny3X
    val thumbSize = large2X

    val maxText = if (checked) checkedText else uncheckedText
    val maxTextWidth = measureTextWidth(maxText, textStyle)

    Box(
        modifier =
            modifier
                .height(28.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier =
                Modifier
                    .width(maxTextWidth + thumbSize + switchPadding * 4)
                    .height(small * 2)
                    .clip(smallRoundedCornerShape)
                    .background(
                        if (checked) {
                            checkedThumbColor
                        } else {
                            uncheckedThumbColor
                        },
                    )
                    .clickable { onCheckedChange(!checked) },
            contentAlignment = Alignment.Center,
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(
                    modifier =
                        Modifier.width(
                            if (!checked) {
                                switchPadding * 2
                            } else {
                                (thumbSize + switchPadding * 2)
                            },
                        ),
                )
                Row(
                    modifier = Modifier.width(maxTextWidth),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (checked) checkedText else uncheckedText,
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = textStyle,
                        maxLines = 1,
                    )
                }
                Spacer(
                    modifier =
                        Modifier.width(
                            if (checked) {
                                switchPadding * 2
                            } else {
                                (thumbSize + switchPadding * 2)
                            },
                        ),
                )
            }
            Box(
                modifier =
                    Modifier
                        .padding(switchPadding)
                        .size(thumbSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.background)
                        .align(if (checked) Alignment.CenterStart else Alignment.CenterEnd),
            )
        }
    }
}
