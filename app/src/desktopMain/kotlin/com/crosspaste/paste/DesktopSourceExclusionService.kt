package com.crosspaste.paste

import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.utils.getJsonUtils

/**
 * Decides which clipboard sources are not recorded. Two persisted lists:
 * exact source names (`sourceExclusions`) and match rules
 * (`sourceExclusionPatterns`), plain case-insensitive substrings for apps
 * whose exact source name the user cannot pick, e.g. a Linux app whose
 * `WM_CLASS` differs from its desktop entry.
 */
class DesktopSourceExclusionService(
    private val configManager: DesktopConfigManager,
) {

    private val jsonUtils = getJsonUtils()

    fun isExcluded(source: String?): Boolean {
        if (source == null) return false
        if (source in getExclusions()) return true
        return getPatterns().any { source.contains(it, ignoreCase = true) }
    }

    fun getExclusions(): List<String> = decode(configManager.getCurrentConfig().sourceExclusions)

    fun addExclusion(source: String) {
        val current = getExclusions()
        if (source !in current) {
            configManager.updateConfig("sourceExclusions", jsonUtils.JSON.encodeToString(current + source))
        }
    }

    fun removeExclusion(source: String) {
        configManager.updateConfig(
            "sourceExclusions",
            jsonUtils.JSON.encodeToString(getExclusions().filter { it != source }),
        )
    }

    fun getPatterns(): List<String> = decode(configManager.getCurrentConfig().sourceExclusionPatterns)

    /** Adds a match rule; blank input and duplicates (ignoring case) are dropped. Returns whether it was added. */
    fun addPattern(pattern: String): Boolean {
        val trimmed = pattern.trim()
        if (trimmed.isEmpty()) return false
        val current = getPatterns()
        if (current.any { it.equals(trimmed, ignoreCase = true) }) return false
        configManager.updateConfig("sourceExclusionPatterns", jsonUtils.JSON.encodeToString(current + trimmed))
        return true
    }

    fun removePattern(pattern: String) {
        configManager.updateConfig(
            "sourceExclusionPatterns",
            jsonUtils.JSON.encodeToString(getPatterns().filter { it != pattern }),
        )
    }

    private fun decode(json: String): List<String> = jsonUtils.JSON.decodeFromString<List<String>>(json)
}
