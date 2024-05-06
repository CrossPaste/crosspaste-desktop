package com.clipevery.ui

import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.clipevery.LocalKoinApplication

private val LightColorPalette =
    lightColors(
        primary = Color(0xFF1672FF),
        primaryVariant = Color(0xFF247CFF),
        secondaryVariant = Color(0xFFE8F5FF),
        surface = Color(0xFFF3F2F7),
    )

private val DarkColorPalette =
    darkColors(
        primary = Color(0xFFBB86FC),
        primaryVariant = Color(0xFF3700B3),
        secondary = Color(0xFF363B3E),
        secondaryVariant = Color(0xFF363B3E),
        background = Color(0xFF202326),
        surface = Color(0xFF2F3338),
    )

@Composable
fun ClipeveryTheme(content: @Composable () -> Unit) {
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
}

fun Color.darken(amount: Float): Color {
    return copy(
        red = (red * (1 - amount)).coerceAtLeast(0f),
        green = (green * (1 - amount)).coerceAtLeast(0f),
        blue = (blue * (1 - amount)).coerceAtLeast(0f),
    )
}
