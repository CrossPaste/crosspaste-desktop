package com.crosspaste.ui.base

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny3X
import com.crosspaste.ui.theme.AppUISize.xxLarge
import com.crosspaste.ui.theme.AppUISize.zero

val countTextStyle =
    TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        textAlign = TextAlign.Center,
    )

@Composable
fun Counter(
    defaultValue: Long,
    unit: String = "",
    rule: (Long) -> Boolean,
    onChange: (Long) -> Unit,
) {
    var count by remember { mutableStateOf(defaultValue) }

    val buttonColors = ButtonDefaults.buttonColors()

    Row(
        modifier = Modifier.wrapContentSize(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            modifier = Modifier.size(xxLarge),
            shape = RectangleShape,
            contentPadding = PaddingValues(zero),
            onClick = {
                val newCount = count - 1
                if (rule(newCount)) {
                    count = newCount
                    onChange(newCount)
                }
            },
        ) {
            Text(
                text = "-",
                color = buttonColors.contentColor,
                style = countTextStyle,
            )
        }
        Spacer(modifier = Modifier.width(tiny3X))

        val width =
            measureTextWidth(
                "$count",
                countTextStyle,
            )

        DefaultTextField(
            modifier =
                Modifier.width(width + medium)
                    .height(xxLarge),
            value = "$count",
            contentPadding = PaddingValues(horizontal = tiny),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            onValueChange = {
                if (it.matches(Regex("^\\d+$"))) {
                    val newCount = it.toLong()
                    if (rule(newCount)) {
                        count = newCount
                        onChange(newCount)
                    }
                }
            },
        )
        Spacer(modifier = Modifier.width(tiny3X))
        Text(
            text = unit,
            color = MaterialTheme.colorScheme.primary,
            style = countTextStyle,
        )
        Spacer(modifier = Modifier.width(tiny3X))
        Button(
            modifier = Modifier.size(xxLarge),
            shape = RectangleShape,
            contentPadding = PaddingValues(zero),
            onClick = {
                val newCount = count + 1
                if (rule(newCount)) {
                    count = newCount
                    onChange(newCount)
                }
            },
        ) {
            Text(
                text = "+",
                color = buttonColors.contentColor,
                style = countTextStyle,
            )
        }
    }
}
