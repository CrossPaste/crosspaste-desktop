package com.crosspaste.paste

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Article
import com.composables.icons.materialsymbols.rounded.Code
import com.composables.icons.materialsymbols.rounded.Docs
import com.composables.icons.materialsymbols.rounded.Image
import com.composables.icons.materialsymbols.rounded.Link
import com.composables.icons.materialsymbols.rounded.Palette
import com.composables.icons.materialsymbols.rounded.Title
import com.crosspaste.ui.LocalThemeExtState
import com.crosspaste.ui.base.IconData
import io.github.oshai.kotlinlogging.KotlinLogging

object PasteTypeExt {
    // Text
    val LIGHT_TEXT_PASTE_TYPE_EXT =
        IconData(
            imageVector = MaterialSymbols.Rounded.Title,
            color = Color(0xFF1B6EF3),
            bgColor = Color(0xFFE8F0FE),
        )
    val DARK_TEXT_PASTE_TYPE_EXT =
        IconData(
            imageVector = MaterialSymbols.Rounded.Title,
            color = Color(0xFF60A5FA),
            bgColor = Color(0xFF1E3A5F),
        )

    // Image
    val LIGHT_IMAGE_PASTE_TYPE_EXT =
        IconData(
            imageVector = MaterialSymbols.Rounded.Image,
            color = Color(0xFFEF4444),
            bgColor = Color(0xFFFEE2E2),
        )
    val DARK_IMAGE_PASTE_TYPE_EXT =
        IconData(
            imageVector = MaterialSymbols.Rounded.Image,
            color = Color(0xFFF87171),
            bgColor = Color(0xFF5C1A1A),
        )

    // File
    val LIGHT_FILE_PASTE_TYPE_EXT =
        IconData(
            imageVector = MaterialSymbols.Rounded.Docs,
            color = Color(0xFF7C3AED),
            bgColor = Color(0xFFF3E8FF),
        )
    val DARK_FILE_PASTE_TYPE_EXT =
        IconData(
            imageVector = MaterialSymbols.Rounded.Docs,
            color = Color(0xFFA78BFA),
            bgColor = Color(0xFF3B1F6E),
        )

    // URL
    val LIGHT_URL_PASTE_TYPE_EXT =
        IconData(
            imageVector = MaterialSymbols.Rounded.Link,
            color = Color(0xFF1EA446),
            bgColor = Color(0xFFE6F4EA),
        )
    val DARK_URL_PASTE_TYPE_EXT =
        IconData(
            imageVector = MaterialSymbols.Rounded.Link,
            color = Color(0xFF4ADE80),
            bgColor = Color(0xFF14412A),
        )

    // Color
    val LIGHT_COLOR_PASTE_TYPE_EXT =
        IconData(
            imageVector = MaterialSymbols.Rounded.Palette,
            color = Color(0xFFD97706),
            bgColor = Color(0xFFFEF3C7),
        )
    val DARK_COLOR_PASTE_TYPE_EXT =
        IconData(
            imageVector = MaterialSymbols.Rounded.Palette,
            color = Color(0xFFFBBF24),
            bgColor = Color(0xFF4A3310),
        )

    // HTML
    val LIGHT_HTML_PASTE_TYPE_EXT =
        IconData(
            imageVector = MaterialSymbols.Rounded.Code,
            color = Color(0xFFE8710A),
            bgColor = Color(0xFFFEF0E1),
        )
    val DARK_HTML_PASTE_TYPE_EXT =
        IconData(
            imageVector = MaterialSymbols.Rounded.Code,
            color = Color(0xFFFB923C),
            bgColor = Color(0xFF4A2A0A),
        )

    // RTF
    val LIGHT_RTF_PASTE_TYPE_EXT =
        IconData(
            imageVector = MaterialSymbols.Rounded.Article,
            color = Color(0xFF0891B2),
            bgColor = Color(0xFFE0F7FA),
        )
    val DARK_RTF_PASTE_TYPE_EXT =
        IconData(
            imageVector = MaterialSymbols.Rounded.Article,
            color = Color(0xFF22D3EE),
            bgColor = Color(0xFF164E63),
        )
}

private val logger = KotlinLogging.logger {}

@Composable
fun PasteType.getIconData(): IconData {
    val themeExt = LocalThemeExtState.current
    return when (this) {
        PasteType.TEXT_TYPE -> themeExt.textTypeIconData
        PasteType.URL_TYPE -> themeExt.urlTypeIconData
        PasteType.HTML_TYPE -> themeExt.htmlTypeIconData
        PasteType.FILE_TYPE -> themeExt.fileTypeIconData
        PasteType.IMAGE_TYPE -> themeExt.imageTypeIconData
        PasteType.RTF_TYPE -> themeExt.rtfTypeIconData
        PasteType.COLOR_TYPE -> themeExt.colorTypeIconData
        else -> {
            logger.warn { "Unknown PasteType: $this, falling back to textTypeIconData" }
            themeExt.textTypeIconData
        }
    }
}
