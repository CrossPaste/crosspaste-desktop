package com.crosspaste.paste

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class PasteTypeExt(
    val imageVector: ImageVector,
    val color: Color,
    val bgColor: Color,
) {
    companion object {
        // Text
        val LIGHT_TEXT_PASTE_TYPE_EXT =
            PasteTypeExt(
                imageVector = Icons.Outlined.TextFields,
                color = Color(0xFF1B6EF3),
                bgColor = Color(0xFFE8F0FE),
            )
        val DARK_TEXT_PASTE_TYPE_EXT =
            PasteTypeExt(
                imageVector = Icons.Outlined.TextFields,
                color = Color(0xFF60A5FA),
                bgColor = Color(0xFF1E3A5F),
            )

        // Image
        val LIGHT_IMAGE_PASTE_TYPE_EXT =
            PasteTypeExt(
                imageVector = Icons.Outlined.Image,
                color = Color(0xFFEF4444),
                bgColor = Color(0xFFFEE2E2),
            )
        val DARK_IMAGE_PASTE_TYPE_EXT =
            PasteTypeExt(
                imageVector = Icons.Outlined.Image,
                color = Color(0xFFF87171),
                bgColor = Color(0xFF5C1A1A),
            )

        // File
        val LIGHT_FILE_PASTE_TYPE_EXT =
            PasteTypeExt(
                imageVector = Icons.Outlined.Description,
                color = Color(0xFF7C3AED),
                bgColor = Color(0xFFF3E8FF),
            )
        val DARK_FILE_PASTE_TYPE_EXT =
            PasteTypeExt(
                imageVector = Icons.Outlined.Description,
                color = Color(0xFFA78BFA),
                bgColor = Color(0xFF3B1F6E),
            )

        // URL
        val LIGHT_URL_PASTE_TYPE_EXT =
            PasteTypeExt(
                imageVector = Icons.Outlined.Link,
                color = Color(0xFF1EA446),
                bgColor = Color(0xFFE6F4EA),
            )
        val DARK_URL_PASTE_TYPE_EXT =
            PasteTypeExt(
                imageVector = Icons.Outlined.Link,
                color = Color(0xFF4ADE80),
                bgColor = Color(0xFF14412A),
            )

        // Color
        val LIGHT_COLOR_PASTE_TYPE_EXT =
            PasteTypeExt(
                imageVector = Icons.Outlined.Palette,
                color = Color(0xFFD97706),
                bgColor = Color(0xFFFEF3C7),
            )
        val DARK_COLOR_PASTE_TYPE_EXT =
            PasteTypeExt(
                imageVector = Icons.Outlined.Palette,
                color = Color(0xFFFBBF24),
                bgColor = Color(0xFF4A3310),
            )

        // HTML
        val LIGHT_HTML_PASTE_TYPE_EXT =
            PasteTypeExt(
                imageVector = Icons.Outlined.Code,
                color = Color(0xFFE8710A),
                bgColor = Color(0xFFFEF0E1),
            )
        val DARK_HTML_PASTE_TYPE_EXT =
            PasteTypeExt(
                imageVector = Icons.Outlined.Code,
                color = Color(0xFFFB923C),
                bgColor = Color(0xFF4A2A0A),
            )

        // RTF
        val LIGHT_RTF_PASTE_TYPE_EXT =
            PasteTypeExt(
                imageVector = Icons.AutoMirrored.Outlined.Article,
                color = Color(0xFF0891B2),
                bgColor = Color(0xFFE0F7FA),
            )
        val DARK_RTF_PASTE_TYPE_EXT =
            PasteTypeExt(
                imageVector = Icons.AutoMirrored.Outlined.Article,
                color = Color(0xFF22D3EE),
                bgColor = Color(0xFF164E63),
            )
    }
}
