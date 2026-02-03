package com.crosspaste.ui.theme

import androidx.compose.ui.graphics.Color
import com.crosspaste.paste.PasteTypeExt

data class ThemeExt(
    val success: SemanticColorGroup,
    val info: SemanticColorGroup,
    val neutral: SemanticColorGroup,
    val warning: SemanticColorGroup,
    val special: SemanticColorGroup,
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
        private val COLOR_SUCCESS = Color(0xFF2E7D32)
        private val COLOR_INFO = Color(0xFF0288D1)
        private val COLOR_NEUTRAL = Color(0xFF747775)
        private val COLOR_WARNING = Color(0xFFFBC02D)
        private val COLOR_SPECIAL = Color(0xFF6750A4)

        fun buildThemeExt(
            primary: Color,
            isDark: Boolean,
        ): ThemeExt {
            fun createGroup(
                source: Color,
                policy: SemanticColorPolicy,
                isWarning: Boolean = false,
            ) = SemanticColorGroup.create(source, primary, isDark, policy, isWarning)

            return ThemeExt(
                success = createGroup(COLOR_SUCCESS, SemanticColorPolicy.Dynamic),
                info = createGroup(COLOR_INFO, SemanticColorPolicy.Dynamic),
                neutral = createGroup(COLOR_NEUTRAL, SemanticColorPolicy.Dynamic),
                warning = createGroup(COLOR_WARNING, SemanticColorPolicy.FixedHue, isWarning = true),
                special = createGroup(COLOR_SPECIAL, SemanticColorPolicy.Dynamic),
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
}
