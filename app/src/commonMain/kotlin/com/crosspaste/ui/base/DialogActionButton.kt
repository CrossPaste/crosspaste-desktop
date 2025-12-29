package com.crosspaste.ui.base

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults.buttonColors
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun DialogActionButton(
    text: String,
    type: DialogButtonType,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    when (type) {
        DialogButtonType.FILLED -> {
            Button(onClick = onClick, enabled = enabled) {
                Text(text)
            }
        }
        DialogButtonType.TONAL -> {
            FilledTonalButton(onClick = onClick, enabled = enabled) {
                Text(text)
            }
        }
        DialogButtonType.ERROR -> {
            Button(
                onClick = onClick,
                colors =
                    buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                enabled = enabled,
            ) {
                Text(text)
            }
        }
        else -> {
            TextButton(onClick = onClick, enabled = enabled) {
                Text(text)
            }
        }
    }
}
