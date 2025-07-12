package com.crosspaste.ui

import com.crosspaste.config.CommonConfigManager
import com.crosspaste.ui.base.FontInfo
import com.crosspaste.ui.base.FontManager
import com.crosspaste.ui.base.FontSources
import java.awt.GraphicsEnvironment
import java.net.URI

class DesktopFontManager(
    configManager: CommonConfigManager,
) : FontManager(configManager = configManager) {
    override fun getSystemFonts(): List<FontInfo> {
        return getUsableFontFamilyNamesOfSystem()
            .mapNotNull {
                val uri = URI(FontSources.SYSTEM_PROTOCOL, it, null).toString()
                val fontFamily = getFontByUri(uri)
                if (fontFamily == null) {
                    return@mapNotNull null
                }
                FontInfo(
                    id = uri,
                    uri = uri,
                    name = it,
                    fontFamily = fontFamily,
                )
            }
    }

    companion object {
        fun getUsableFontFamilyNamesOfSystem(): List<String> {
            return GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames
                .toList()
        }
    }
}
