package com.crosspaste.ui.base

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults.buttonColors
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.crosspaste.ui.theme.AppUISize.medium

@Composable
fun DialogActionButton(
    text: String,
    type: DialogButtonType,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    onClick: () -> Unit,
) {
    val effectiveEnabled = enabled && !isLoading
    val content: @Composable () -> Unit = {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(medium))
        } else {
            Text(text)
        }
    }

    when (type) {
        DialogButtonType.FILLED -> {
            Button(onClick = onClick, enabled = effectiveEnabled, content = { content() })
        }
        DialogButtonType.TONAL -> {
            FilledTonalButton(onClick = onClick, enabled = effectiveEnabled, content = { content() })
        }
        DialogButtonType.ERROR -> {
            Button(
                onClick = onClick,
                colors =
                    buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                enabled = effectiveEnabled,
                content = { content() },
            )
        }
        else -> {
            TextButton(onClick = onClick, enabled = effectiveEnabled, content = { content() })
        }
    }
}
