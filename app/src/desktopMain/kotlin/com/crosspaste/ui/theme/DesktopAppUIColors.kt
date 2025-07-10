package com.crosspaste.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.crosspaste.utils.ColorUtils

object DesktopAppUIColors {

    val searchBackground: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.surface

    val sideOnDarkColor: Color = Color(0xFFF7F8F8)

    val sideOnLightColor: Color = Color(0xFF1C1C1E)

    fun getSideTitleColor(background: Color): Color {
        return if (ColorUtils.isDarkColor(background)) {
            sideOnDarkColor
        } else {
            sideOnLightColor
        }
    }
}
