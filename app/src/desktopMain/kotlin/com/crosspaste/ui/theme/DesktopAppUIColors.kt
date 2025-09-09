package com.crosspaste.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

object DesktopAppUIColors {

    val searchBackground: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surface
}
