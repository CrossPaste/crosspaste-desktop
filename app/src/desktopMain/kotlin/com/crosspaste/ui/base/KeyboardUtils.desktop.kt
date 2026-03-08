package com.crosspaste.ui.base

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType

@Composable
actual fun numberKeyboardOptions(imeAction: ImeAction): KeyboardOptions =
    KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = imeAction)
