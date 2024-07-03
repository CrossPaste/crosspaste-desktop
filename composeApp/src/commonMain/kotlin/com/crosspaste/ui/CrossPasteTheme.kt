package com.crosspaste.ui

import androidx.compose.material.Colors
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.crosspaste.LocalKoinApplication

private val LightColorPalette =
    lightColors(
        primary = Color(0xFF167DFF),
        surface = Color(0xFFF0F0F0),
    )

private val DarkColorPalette =
    darkColors(
        primary = Color(0xFFBB86FC),
        background = Color(0xFF23272A),
        surface = Color(0xFF323232),
    )

@Composable
fun CrossPasteTheme(content: @Composable () -> Unit) {
    val current = LocalKoinApplication.current
    val themeDetector = current.koin.get<ThemeDetector>()

    val colors =
        if (themeDetector.isFollowSystem()) {
            if (themeDetector.isSystemInDark()) {
                DarkColorPalette
            } else {
                LightColorPalette
            }
        } else {
            if (themeDetector.isUserInDark()) {
                DarkColorPalette
            } else {
                LightColorPalette
            }
        }
    MaterialTheme(
        colors = colors,
        content = content,
    )
}

interface ThemeDetector {
    fun isSystemInDark(): Boolean

    fun isFollowSystem(): Boolean

    fun isUserInDark(): Boolean

    fun isCurrentThemeDark(): Boolean {
        return isFollowSystem() && isSystemInDark() || !isFollowSystem() && isUserInDark()
    }

    fun setThemeConfig(
        isFollowSystem: Boolean,
        isUserInDark: Boolean = false,
    )

    fun addListener(listener: (Boolean) -> Unit)
}

fun Color.darken(amount: Float): Color {
    return copy(
        red = (red * (1 - amount)).coerceAtLeast(0f),
        green = (green * (1 - amount)).coerceAtLeast(0f),
        blue = (blue * (1 - amount)).coerceAtLeast(0f),
    )
}

fun decorationColor(): Color {
    return Color(0xFF121314)
}

fun favoriteColor(): Color {
    return Color(0xFFFFCE34)
}

fun Colors.selectColor(): Color {
    return if (isLight) {
        Color(0xFFEBF6FF)
    } else {
        Color(0xFF2F446F)
    }
}

fun connectedColor(): Color {
    return Color(0xFF95EC69)
}

fun connectingColor(): Color {
    return Color(0xFFE6C44D)
}

fun disconnectedColor(): Color {
    return Color(0xFFFF6969)
}

fun unmatchedColor(): Color {
    return Color(0xFF9A69EC)
}

fun unverifiedColor(): Color {
    return Color(0xFF69A9EC)
}

fun grantPermissionColor(): Color {
    return Color(0xFF95EC69)
}
