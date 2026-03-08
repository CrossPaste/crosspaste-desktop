package com.crosspaste.ui.base

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Add
import com.composables.icons.materialsymbols.rounded.Remove
import com.crosspaste.ui.theme.AppUISize.giant
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny5X
import com.crosspaste.ui.theme.AppUISize.tinyRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.xLarge
import com.crosspaste.ui.theme.AppUISize.xxxLarge

@Composable
fun Counter(
    defaultValue: Long,
    unit: String = "",
    rule: (Long) -> Boolean,
    onChange: (Long) -> Unit,
) {
    var count by remember { mutableStateOf(defaultValue) }
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier.wrapContentSize(),
        shape = tinyRoundedCornerShape,
        color = colorScheme.surface,
        border = BorderStroke(tiny5X, colorScheme.outlineVariant),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.height(xxxLarge),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(xxxLarge)
                        .clip(
                            RoundedCornerShape(
                                topStart = tiny,
                                bottomStart = tiny,
                            ),
                        ).clickable {
                            val newCount = count - 1
                            if (rule(newCount)) {
                                count = newCount
                                onChange(newCount)
                            }
                        },
            ) {
                Icon(
                    imageVector = MaterialSymbols.Rounded.Remove,
                    contentDescription = "Decrease",
                    modifier = Modifier.size(medium),
                    tint = colorScheme.onSurfaceVariant,
                )
            }

            VerticalDivider(
                thickness = tiny5X,
                color = colorScheme.outlineVariant,
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.widthIn(min = giant),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    BasicTextField(
                        value = count.toString(),
                        onValueChange = { s ->
                            if (s.isNotEmpty() && s.all { it.isDigit() }) {
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
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.End,
                            ),
                        cursorBrush = SolidColor(colorScheme.primary),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(IntrinsicSize.Min).widthIn(min = xLarge),
                    )

                    if (unit.isNotEmpty()) {
                        Text(
                            text = " $unit",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = colorScheme.onSurface,
                        )
                    }
                }
            }

            VerticalDivider(
                thickness = tiny5X,
                color = colorScheme.outlineVariant,
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(xxxLarge)
                        .clip(
                            RoundedCornerShape(
                                topEnd = tiny,
                                bottomEnd = tiny,
                            ),
                        ).clickable {
                            val newCount = count + 1
                            if (rule(newCount)) {
                                count = newCount
                                onChange(newCount)
                            }
                        },
            ) {
                Icon(
                    imageVector = MaterialSymbols.Rounded.Add,
                    contentDescription = "Increase",
                    modifier = Modifier.size(medium),
                    tint = colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
