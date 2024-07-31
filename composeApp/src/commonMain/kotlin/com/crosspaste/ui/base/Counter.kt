package com.crosspaste.ui.base

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crosspaste.ui.devices.measureTextWidth

@Composable
fun Counter(
    defaultValue: Long,
    unit: String = "",
    rule: (Long) -> Boolean,
    onChange: (Long) -> Unit,
) {
    var count by remember { mutableStateOf(defaultValue) }

    val textWidth =
        measureTextWidth(
            "$count",
            LocalTextStyle.current.copy(
                textAlign = TextAlign.Center,
                color = MaterialTheme.colors.primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            ),
        )

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
        CustomTextField(
            modifier = Modifier.width(textWidth + 16.dp).wrapContentHeight(),
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
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle =
                LocalTextStyle.current.copy(
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colors.primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 10.sp,
                ),
            colors =
                TextFieldDefaults.textFieldColors(
                    focusedIndicatorColor = MaterialTheme.colors.primary,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            contentPadding = PaddingValues(0.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = unit,
            color = MaterialTheme.colors.primary,
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
