package com.crosspaste.ui.devices

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import com.crosspaste.ui.base.numberKeyboardOptions
import com.crosspaste.ui.theme.AppUISize.xxxLarge

@Composable
actual fun PlatformTokenTextField(
    value: String,
    enabled: Boolean,
    index: Int,
    tokenCount: Int,
    onValueChange: (String) -> Unit,
    onBackspaceWhenEmpty: () -> Unit,
    modifier: Modifier,
    textStyle: TextStyle,
    cursorBrush: Brush,
) {
    BasicTextField(
        value = value,
        enabled = enabled,
        onValueChange = {
            if (it.length <= 1) {
                onValueChange(it)
            }
        },
        modifier =
            modifier
                .wrapContentSize()
                .onKeyEvent { event ->
                    if (!enabled) return@onKeyEvent false
                    if (event.key == Key.Backspace && value.isEmpty()) {
                        onBackspaceWhenEmpty()
                        true
                    } else {
                        false
                    }
                },
        textStyle = textStyle,
        singleLine = true,
        keyboardOptions =
            numberKeyboardOptions(
                imeAction = if (index == tokenCount - 1) ImeAction.Done else ImeAction.Next,
            ),
        cursorBrush = cursorBrush,
        decorationBox = { innerTextField ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.defaultMinSize(minWidth = xxxLarge),
            ) {
                innerTextField()
            }
        },
    )
}
