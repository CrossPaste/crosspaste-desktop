package com.crosspaste.plugin

import com.crosspaste.paste.PasteType
import kotlinx.serialization.Serializable

/**
 * The `manifest.json` at the root of a plugin directory. [runtime], [output] and
 * [pasteTypes] stay plain strings so a manifest written for a newer app still
 * parses and can be reported as incompatible instead of unreadable.
 */
@Serializable
data class PluginManifest(
    val id: String,
    val version: String,
    val minAppVersion: String? = null,
    val name: Map<String, String> = emptyMap(),
    val pasteTypes: List<String> = emptyList(),
    val runtime: String,
    val headless: Boolean = false,
    val hosts: List<String> = emptyList(),
    val platforms: Map<String, PluginPlatform> = emptyMap(),
    val output: String,
    /** When present, the menu entry becomes a submenu with one item per action. */
    val actions: List<PluginAction> = emptyList(),
) {

    fun displayName(language: String): String = localizedName(name, language) ?: id

    fun supportedPasteTypes(): Set<PasteType> = pasteTypes.mapNotNull { PUBLIC_PASTE_TYPES[it] }.toSet()

    companion object {
        /**
         * Public paste type identifiers. They are a published contract: never
         * rename one, even where the internal [PasteType.name] differs.
         */
        val PUBLIC_PASTE_TYPES: Map<String, PasteType> =
            mapOf(
                "text" to PasteType.TEXT_TYPE,
                "html" to PasteType.HTML_TYPE,
                "rtf" to PasteType.RTF_TYPE,
                "url" to PasteType.URL_TYPE,
                "color" to PasteType.COLOR_TYPE,
                "image" to PasteType.IMAGE_TYPE,
                "files" to PasteType.FILE_TYPE,
            )
    }
}

/** A submenu item; the chosen [id] reaches the process as `CROSSPASTE_ACTION`. */
@Serializable
data class PluginAction(
    val id: String,
    val name: Map<String, String> = emptyMap(),
) {
    fun displayName(language: String): String = localizedName(name, language) ?: id
}

/**
 * Label for [language] (a copywriter code such as `zh` or `zh_hant`): exact
 * match, then normalized/hyphenated variations, then the base language,
 * then English, then any declared name.
 */
private fun localizedName(
    name: Map<String, String>,
    language: String,
): String? {
    val baseLang = language.substringBefore('_').substringBefore('-')
    val normalized = language.replace('-', '_')
    val hyphenated = language.replace('_', '-')
    return name[language]
        ?: name[normalized]
        ?: name[hyphenated]
        ?: name[baseLang]
        ?: name["en"]
        ?: name.values.firstOrNull()
}

@Serializable
data class PluginPlatform(
    /** Normalized architectures (`arm64`, `x64`); null means every architecture. */
    val arch: List<String>? = null,
    /** argv, never parsed by a shell; relative paths resolve against the plugin directory. */
    val command: List<String> = emptyList(),
    /** Executables that must be on PATH. */
    val requires: List<String> = emptyList(),
)

enum class PluginOutput(
    val wireName: String,
) {
    REPLACE_CLIPBOARD("replaceClipboard"),
    NEW_PASTE("newPaste"),
    NOTIFICATION("notification"),
    OPEN_URL("openUrl"),
    ;

    companion object {
        fun fromWireName(wireName: String): PluginOutput? = entries.firstOrNull { it.wireName == wireName }
    }
}
