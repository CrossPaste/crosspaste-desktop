package com.crosspaste.ui.base

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Counter(
    defaultValue: Long,
    unit: String = "",
    rule: (Long) -> Boolean,
    onChange: (Long) -> Unit,
) {
    var count by remember { mutableStateOf(defaultValue) }
    val colorScheme = MaterialTheme.colorScheme

    // M3 Container: Rounded Pill shape with Surface Variant background
    Surface(
        modifier = Modifier.wrapContentSize(),
        shape = CircleShape,
        color = colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            // Minus Button
            IconButton(
                onClick = {
                    val newCount = count - 1
                    if (rule(newCount)) {
                        count = newCount
                        onChange(newCount)
                    }
                },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Decrease",
                    tint = colorScheme.primary,
                )
            }

            // Numeric Input and Unit
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // We use BasicTextField for a cleaner "no-border" look inside the pill
                BasicTextField(
                    value = count.toString(),
                    onValueChange = { s ->
                        if (s.isEmpty()) {
                            // Handle empty state if needed, or set to 0
                        } else if (s.all { it.isDigit() }) {
                            val newCount = s.toLongOrNull() ?: count
                            if (rule(newCount)) {
                                count = newCount
                                onChange(newCount)
                            }
                        }
                    },
                    textStyle =
                        TextStyle(
                            color = colorScheme.onSurface,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.End,
                        ),
                    cursorBrush = SolidColor(colorScheme.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(IntrinsicSize.Min).widthIn(min = 32.dp),
                )

                if (unit.isNotEmpty()) {
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 4.dp, end = 4.dp),
                    )
                }
            }

            // Plus Button
            IconButton(
                onClick = {
                    val newCount = count + 1
                    if (rule(newCount)) {
                        count = newCount
                        onChange(newCount)
                    }
                },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Increase",
                    tint = colorScheme.primary,
                )
            }
        }
    }
}
