package com.crosspaste.ui.base

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.text.font.FontFamily
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.utils.ioDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

object FontSources {
    const val FILE_PROTOCOL = "file"
    const val RESOURCE_PROTOCOL = "resource"
    const val SYSTEM_PROTOCOL = "system"
}

abstract class FontManager(
    private val configManager: CommonConfigManager,
) {
    companion object {
        const val DEFAULT_FONT_ID = "default"
        val defaultFontInfo =
            FontInfo(
                id = DEFAULT_FONT_ID,
                uri = "",
                name = "Default",
                fontFamily = FontFamily.Default,
            )
    }

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    private val _selectableFonts = MutableStateFlow(listOf(defaultFontInfo))

    val selectableFontsFlow: StateFlow<List<FontInfo>> = _selectableFonts.asStateFlow()

    val selectableFonts: List<FontInfo> get() = _selectableFonts.value

    init {
        scope.launch {
            val fonts =
                buildList {
                    add(defaultFontInfo)
                    addAll(getSystemFonts())
                    addAll(userDefinedFonts())
                }
            _selectableFonts.value = fonts
        }
    }

    abstract fun getFontByUri(uri: String): FontFamily?

    val currentFontInfo =
        combine(
            configManager.config,
            _selectableFonts,
        ) { config, fonts ->
            fonts.find { it.uri == config.font } ?: defaultFontInfo
        }

    fun getCurrentFontInfo(font: String = configManager.config.value.font): FontInfo =
        selectableFonts.find { it.uri == font }
            ?: defaultFontInfo

    fun setFont(fontId: String?) {
        val fontId = fontId ?: DEFAULT_FONT_ID
        val font =
            selectableFonts
                .find { it.id == fontId }
                ?: defaultFontInfo

        configManager.updateConfig(
            "font",
            font.uri,
        )
    }

    /**
     * Returns a list of system fonts available on the device.
     * This method should be overridden in platform-specific implementations.
     */
    abstract fun getSystemFonts(): List<FontInfo>

    /**
     * Returns a list of user-defined fonts.
     * or custom fonts that bundled with the application.
     */
    open fun userDefinedFonts(): List<FontInfo> = emptyList()
}

/**
 * This is for demonstration purposes of a font
 * @param uri:
 *  this value can be used to save the font in the storage
 *  then we can detect its source by the uri scheme @see [FontSources]
 */
@Immutable
data class FontInfo(
    val id: String,
    val uri: String,
    val name: String,
    val fontFamily: FontFamily,
)

@Composable
fun rememberUserSelectedFont(): State<FontInfo> {
    val fontManager = koinInject<FontManager>()
    return fontManager.currentFontInfo.collectAsState(
        fontManager.getCurrentFontInfo(),
    )
}

fun FontInfo.customFontOrNull(): FontFamily? =
    this
        .takeIf {
            it != FontManager.defaultFontInfo
        }?.fontFamily

fun Typography.withCustomFonts(fontInfo: FontInfo): Typography {
    val customFontFamily = fontInfo.customFontOrNull() ?: return this
    return copy(
        displayLarge = displayLarge.copy(fontFamily = customFontFamily),
        displayMedium = displayMedium.copy(fontFamily = customFontFamily),
        displaySmall = displaySmall.copy(fontFamily = customFontFamily),
        headlineLarge = headlineLarge.copy(fontFamily = customFontFamily),
        headlineMedium = headlineMedium.copy(fontFamily = customFontFamily),
        headlineSmall = headlineSmall.copy(fontFamily = customFontFamily),
        titleLarge = titleLarge.copy(fontFamily = customFontFamily),
        titleMedium = titleMedium.copy(fontFamily = customFontFamily),
        titleSmall = titleSmall.copy(fontFamily = customFontFamily),
        bodyLarge = bodyLarge.copy(fontFamily = customFontFamily),
        bodyMedium = bodyMedium.copy(fontFamily = customFontFamily),
        bodySmall = bodySmall.copy(fontFamily = customFontFamily),
        labelLarge = labelLarge.copy(fontFamily = customFontFamily),
        labelMedium = labelMedium.copy(fontFamily = customFontFamily),
        labelSmall = labelSmall.copy(fontFamily = customFontFamily),
    )
}
