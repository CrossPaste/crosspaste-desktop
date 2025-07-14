package com.crosspaste.ui

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.FileFont
import androidx.compose.ui.text.platform.ResourceFont
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.ui.base.FontInfo
import com.crosspaste.ui.base.FontManager
import com.crosspaste.ui.base.FontSources.FILE_PROTOCOL
import com.crosspaste.ui.base.FontSources.RESOURCE_PROTOCOL
import com.crosspaste.ui.base.FontSources.SYSTEM_PROTOCOL
import io.github.oshai.kotlinlogging.KotlinLogging
import java.awt.GraphicsEnvironment
import java.net.URI
import kotlin.io.path.toPath

class DesktopFontManager(
    configManager: CommonConfigManager,
) : FontManager(configManager = configManager) {

    private val logger = KotlinLogging.logger { }

    override fun getFontByUri(uri: String): FontFamily? =
        runCatching {
            fromUri(URI.create(uri))
        }.onFailure { e ->
            logger.warn(e) { "Could not load font $uri" }
        }.getOrNull()

    override fun getSystemFonts(): List<FontInfo> {
        return getUsableFontFamilyNamesOfSystem()
            .mapNotNull {
                val uri = URI(SYSTEM_PROTOCOL, it, null).toString()
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
        fun getUsableFontFamilyNamesOfSystem(): List<String> =
            GraphicsEnvironment
                .getLocalGraphicsEnvironment()
                .availableFontFamilyNames
                .toList()

        @OptIn(ExperimentalTextApi::class)
        private fun fromUri(uri: URI): FontFamily {
            return when (uri.scheme) {
                FILE_PROTOCOL -> {
                    return FontFamily(
                        FileFont(uri.toPath().toFile()),
                    )
                }

                RESOURCE_PROTOCOL -> {
                    val path = uri.schemeSpecificPart
                    require(path.isNotEmpty())
                    FontFamily(
                        ResourceFont(uri.schemeSpecificPart),
                    )
                }

                SYSTEM_PROTOCOL -> {
                    // This is a system font, we can use it directly
                    val name = uri.schemeSpecificPart
                    require(name.isNotEmpty())
                    FontFamily(name)
                }

                else -> throw IllegalArgumentException("Unsupported font URI scheme: ${uri.scheme}")
            }
        }
    }
}
