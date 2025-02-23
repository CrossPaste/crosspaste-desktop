package com.crosspaste.ui.base

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
            modifier = Modifier.wrapContentHeight().width(36.dp),
            shape = RectangleShape,
            contentPadding = PaddingValues(0.dp),
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
        Spacer(modifier = Modifier.width(4.dp))

        val width =
            measureTextWidth(
                "$count",
                countTextStyle,
            )

        DefaultTextField(
            modifier = Modifier.width(width + 16.dp),
            value = "$count",
            contentPadding = PaddingValues(horizontal = 8.dp),
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
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = unit,
            color = MaterialTheme.colorScheme.primary,
            style = countTextStyle,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Button(
            modifier = Modifier.wrapContentHeight().width(36.dp),
            shape = RectangleShape,
            contentPadding = PaddingValues(0.dp),
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
