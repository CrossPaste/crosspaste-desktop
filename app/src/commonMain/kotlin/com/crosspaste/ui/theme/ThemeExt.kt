package com.crosspaste.ui.theme

import androidx.compose.ui.graphics.Color
import com.crosspaste.paste.PasteTypeExt
import com.crosspaste.ui.base.IconColor
import com.crosspaste.ui.base.IconData

data class ThemeExt(
    val success: SemanticColorGroup,
    val info: SemanticColorGroup,
    val neutral: SemanticColorGroup,
    val warning: SemanticColorGroup,
    val textTypeIconData: IconData,
    val imageTypeIconData: IconData,
    val fileTypeIconData: IconData,
    val urlTypeIconData: IconData,
    val colorTypeIconData: IconData,
    val htmlTypeIconData: IconData,
    val rtfTypeIconData: IconData,
    val amberIconColor: IconColor,
    val blueIconColor: IconColor,
    val cyanIconColor: IconColor,
    val greenIconColor: IconColor,
    val indigoIconColor: IconColor,
    val purpleIconColor: IconColor,
    val redIconColor: IconColor,
    val roseIconColor: IconColor,
    val violetIconColor: IconColor,
    val yellowIconColor: IconColor,
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
                textTypeIconData =
                    if (isDark) PasteTypeExt.DARK_TEXT_PASTE_TYPE_EXT else PasteTypeExt.LIGHT_TEXT_PASTE_TYPE_EXT,
                imageTypeIconData =
                    if (isDark) PasteTypeExt.DARK_IMAGE_PASTE_TYPE_EXT else PasteTypeExt.LIGHT_IMAGE_PASTE_TYPE_EXT,
                fileTypeIconData =
                    if (isDark) PasteTypeExt.DARK_FILE_PASTE_TYPE_EXT else PasteTypeExt.LIGHT_FILE_PASTE_TYPE_EXT,
                urlTypeIconData =
                    if (isDark) PasteTypeExt.DARK_URL_PASTE_TYPE_EXT else PasteTypeExt.LIGHT_URL_PASTE_TYPE_EXT,
                colorTypeIconData =
                    if (isDark) PasteTypeExt.DARK_COLOR_PASTE_TYPE_EXT else PasteTypeExt.LIGHT_COLOR_PASTE_TYPE_EXT,
                htmlTypeIconData =
                    if (isDark) PasteTypeExt.DARK_HTML_PASTE_TYPE_EXT else PasteTypeExt.LIGHT_HTML_PASTE_TYPE_EXT,
                rtfTypeIconData =
                    if (isDark) PasteTypeExt.DARK_RTF_PASTE_TYPE_EXT else PasteTypeExt.LIGHT_RTF_PASTE_TYPE_EXT,
                amberIconColor =
                    if (isDark) IconColor.DARK_AMBER else IconColor.LIGHT_AMBER,
                blueIconColor =
                    if (isDark) IconColor.DARK_BLUE else IconColor.LIGHT_BLUE,
                cyanIconColor =
                    if (isDark) IconColor.DARK_CYAN else IconColor.LIGHT_CYAN,
                greenIconColor =
                    if (isDark) IconColor.DARK_GREEN else IconColor.LIGHT_GREEN,
                indigoIconColor =
                    if (isDark) IconColor.DARK_INDIGO else IconColor.LIGHT_INDIGO,
                purpleIconColor =
                    if (isDark) IconColor.DARK_PURPLE else IconColor.LIGHT_PURPLE,
                redIconColor =
                    if (isDark) IconColor.DARK_RED else IconColor.LIGHT_RED,
                roseIconColor =
                    if (isDark) IconColor.DARK_ROSE else IconColor.LIGHT_ROSE,
                violetIconColor =
                    if (isDark) IconColor.DARK_VIOLET else IconColor.LIGHT_VIOLET,
                yellowIconColor =
                    if (isDark) IconColor.DARK_YELLOW else IconColor.LIGHT_YELLOW,
                mutedText =
                    if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280),
            )
    }
}
