package com.crosspaste.ui.base

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import com.crosspaste.ui.theme.AppUISize.small3XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.xxxLarge

data class IconColor(
    val bgColor: Color,
    val color: Color,
) {
    companion object {
        // Amber
        val LIGHT_AMBER =
            IconColor(
                bgColor = Color(0xFFFEF3E8),
                color = Color(0xFFD97706),
            )
        val DARK_AMBER =
            IconColor(
                bgColor = Color(0xFF78350F),
                color = Color(0xFFFBBF24),
            )

        // Blue
        val LIGHT_BLUE =
            IconColor(
                bgColor = Color(0xFFDBEAFE),
                color = Color(0xFF3B82F6),
            )
        val DARK_BLUE =
            IconColor(
                bgColor = Color(0xFF1E3A8A),
                color = Color(0xFF93C5FD),
            )

        // Cyan
        val LIGHT_CYAN =
            IconColor(
                bgColor = Color(0xFFE0F2FE),
                color = Color(0xFF0EA5E9),
            )
        val DARK_CYAN =
            IconColor(
                bgColor = Color(0xFF164E63),
                color = Color(0xFF7DD3FC),
            )

        // Green
        val LIGHT_GREEN =
            IconColor(
                bgColor = Color(0xFFDCFCE7),
                color = Color(0xFF22C55E),
            )
        val DARK_GREEN =
            IconColor(
                bgColor = Color(0xFF14532D),
                color = Color(0xFF86EFAC),
            )

        // Indigo
        val LIGHT_INDIGO =
            IconColor(
                bgColor = Color(0xFFEEF2FF),
                color = Color(0xFF6366F1),
            )
        val DARK_INDIGO =
            IconColor(
                bgColor = Color(0xFF312E81),
                color = Color(0xFFA5B4FC),
            )

        // Purple
        val LIGHT_PURPLE =
            IconColor(
                bgColor = Color(0xFFF3E8FF),
                color = Color(0xFF8B5CF6),
            )
        val DARK_PURPLE =
            IconColor(
                bgColor = Color(0xFF581C87),
                color = Color(0xFFC4B5FD),
            )

        // Red
        val LIGHT_RED =
            IconColor(
                bgColor = Color(0xFFFEE2E2),
                color = Color(0xFFEF4444),
            )
        val DARK_RED =
            IconColor(
                bgColor = Color(0xFF7F1D1D),
                color = Color(0xFFFCA5A5),
            )

        // Rose
        val LIGHT_ROSE =
            IconColor(
                bgColor = Color(0xFFFFE4E6),
                color = Color(0xFFF43F5E),
            )
        val DARK_ROSE =
            IconColor(
                bgColor = Color(0xFF881337),
                color = Color(0xFFFDA4AF),
            )

        // Violet
        val LIGHT_VIOLET =
            IconColor(
                bgColor = Color(0xFFEDE9FE),
                color = Color(0xFF7C3AED),
            )
        val DARK_VIOLET =
            IconColor(
                bgColor = Color(0xFF4C1D95),
                color = Color(0xFFC4B5FD),
            )

        // Yellow
        val LIGHT_YELLOW =
            IconColor(
                bgColor = Color(0xFFFEF3C7),
                color = Color(0xFFF59E0B),
            )
        val DARK_YELLOW =
            IconColor(
                bgColor = Color(0xFF713F12),
                color = Color(0xFFFCD34D),
            )
    }
}

data class IconData(
    val imageVector: ImageVector,
    val bgColor: Color,
    val color: Color,
) {

    constructor(imageVector: ImageVector, iconColor: IconColor) : this(
        imageVector = imageVector,
        bgColor = iconColor.bgColor,
        color = iconColor.color,
    )

    @Composable
    fun IconContent() {
        Box(
            modifier =
                Modifier
                    .size(xxxLarge)
                    .clip(small3XRoundedCornerShape)
                    .background(bgColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = null,
                tint = color,
            )
        }
    }
}

data class PainterData(
    val painter: Painter,
    val iconBg: Color,
    val tint: Color,
) {
    @Composable
    fun IconContent() {
        Box(
            modifier =
                Modifier
                    .size(xxxLarge)
                    .background(iconBg)
                    .clip(small3XRoundedCornerShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painter,
                contentDescription = null,
                tint = tint,
            )
        }
    }
}
