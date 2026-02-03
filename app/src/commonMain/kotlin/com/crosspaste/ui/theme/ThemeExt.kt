package com.crosspaste.ui.theme

import androidx.compose.ui.graphics.Color
import com.crosspaste.paste.PasteTypeExt

data class ThemeExt(
    val success: SemanticColorGroup,
    val info: SemanticColorGroup,
    val neutral: SemanticColorGroup,
    val warning: SemanticColorGroup,
    val textPasteTypeExt: PasteTypeExt,
    val imagePasteTypeExt: PasteTypeExt,
    val filePasteTypeExt: PasteTypeExt,
    val urlPasteTypeExt: PasteTypeExt,
    val colorPasteTypeExt: PasteTypeExt,
    val htmlPasteTypeExt: PasteTypeExt,
    val rtfPasteTypeExt: PasteTypeExt,
    val mutedText: Color,
) {
    companion object {
        fun buildThemeExt(isDark: Boolean): ThemeExt =
            ThemeExt(
                success =
                    if (isDark) SemanticColorGroup.DARK_SUCCESS else SemanticColorGroup.LIGHT_SUCCESS,
                info =
                    if (isDark) SemanticColorGroup.DARK_INFO else SemanticColorGroup.LIGHT_INFO,
                neutral =
                    if (isDark) SemanticColorGroup.DARK_NEUTRAL else SemanticColorGroup.LIGHT_NEUTRAL,
                warning =
                    if (isDark) SemanticColorGroup.DARK_WARNING else SemanticColorGroup.LIGHT_WARNING,
                textPasteTypeExt =
                    if (isDark) PasteTypeExt.DARK_TEXT_PASTE_TYPE_EXT else PasteTypeExt.LIGHT_TEXT_PASTE_TYPE_EXT,
                imagePasteTypeExt =
                    if (isDark) PasteTypeExt.DARK_IMAGE_PASTE_TYPE_EXT else PasteTypeExt.LIGHT_IMAGE_PASTE_TYPE_EXT,
                filePasteTypeExt =
                    if (isDark) PasteTypeExt.DARK_FILE_PASTE_TYPE_EXT else PasteTypeExt.LIGHT_FILE_PASTE_TYPE_EXT,
                urlPasteTypeExt =
                    if (isDark) PasteTypeExt.DARK_URL_PASTE_TYPE_EXT else PasteTypeExt.LIGHT_URL_PASTE_TYPE_EXT,
                colorPasteTypeExt =
                    if (isDark) PasteTypeExt.DARK_COLOR_PASTE_TYPE_EXT else PasteTypeExt.LIGHT_COLOR_PASTE_TYPE_EXT,
                htmlPasteTypeExt =
                    if (isDark) PasteTypeExt.DARK_HTML_PASTE_TYPE_EXT else PasteTypeExt.LIGHT_HTML_PASTE_TYPE_EXT,
                rtfPasteTypeExt =
                    if (isDark) PasteTypeExt.DARK_RTF_PASTE_TYPE_EXT else PasteTypeExt.LIGHT_RTF_PASTE_TYPE_EXT,
                mutedText =
                    if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280),
            )
    }
}
