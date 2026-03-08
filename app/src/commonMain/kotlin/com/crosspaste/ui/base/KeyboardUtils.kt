package com.crosspaste.ui.base

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.input.ImeAction

@Composable
expect fun numberKeyboardOptions(imeAction: ImeAction = ImeAction.Default): KeyboardOptions
