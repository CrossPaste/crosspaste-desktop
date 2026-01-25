package com.crosspaste.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun ImageFileFormat(
    format: String?,
    modifier: Modifier = Modifier,
) {
    if (format.isNullOrBlank()) {
        return
    }

    ImageInfoLabel(
        text = format.uppercase(),
        modifier = modifier,
    )
}
