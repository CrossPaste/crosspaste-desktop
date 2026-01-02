package com.crosspaste.ui.base

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.crosspaste.ui.theme.AppUISize.small3X
import com.crosspaste.ui.theme.AppUISize.tiny2X
import com.crosspaste.ui.theme.AppUISize.tiny5X
import com.crosspaste.ui.theme.AppUISize.tinyRoundedCornerShape

const val enter: String = "↵"

@Composable
fun KeyboardView(key: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = tinyRoundedCornerShape,
        modifier =
            Modifier.border(
                width = tiny5X,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = tinyRoundedCornerShape,
            ),
    ) {
        Text(
            text = key,
            modifier = Modifier.padding(horizontal = small3X, vertical = tiny2X),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
