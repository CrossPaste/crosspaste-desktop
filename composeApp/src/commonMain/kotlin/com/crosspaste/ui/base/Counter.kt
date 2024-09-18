package com.crosspaste.ui.base

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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

    Row(
        modifier = Modifier.wrapContentSize(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            modifier = Modifier.wrapContentHeight().width(36.dp),
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
                color = Color.White,
                style =
                    TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Light,
                        fontFamily = FontFamily.SansSerif,
                    ),
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        DefaultTextField(
            value = "$count",
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
            style =
                TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                ),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Button(
            modifier = Modifier.wrapContentHeight().width(36.dp),
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
                color = Color.White,
                style =
                    TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Light,
                        fontFamily = FontFamily.SansSerif,
                    ),
            )
        }
    }
}
