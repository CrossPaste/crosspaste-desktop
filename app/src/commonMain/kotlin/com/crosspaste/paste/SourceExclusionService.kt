package com.crosspaste.paste

import com.crosspaste.config.CommonConfigManager
import com.crosspaste.utils.getJsonUtils

interface SourceExclusionService {

    val configManager: CommonConfigManager

    fun isExcluded(source: String?): Boolean {
        if (source == null) return false
        val exclusions: List<String> =
            getJsonUtils().JSON.decodeFromString<List<String>>(
                configManager.getCurrentConfig().sourceExclusions,
            )
        return source in exclusions
    }

    fun addExclusion(source: String) {
        val jsonUtils = getJsonUtils()
        val current: MutableList<String> =
            jsonUtils.JSON
                .decodeFromString<List<String>>(
                    configManager.getCurrentConfig().sourceExclusions,
                ).toMutableList()
        if (source !in current) {
            current.add(source)
            configManager.updateConfig(
                "sourceExclusions",
                jsonUtils.JSON.encodeToString(current),
            )
        }
    }

    fun removeExclusion(source: String) {
        val jsonUtils = getJsonUtils()
        val current: List<String> =
            jsonUtils.JSON
                .decodeFromString<List<String>>(
                    configManager.getCurrentConfig().sourceExclusions,
                ).filter { it != source }
        configManager.updateConfig(
            "sourceExclusions",
            jsonUtils.JSON.encodeToString(current),
        )
    }

    fun getExclusions(): List<String> =
        getJsonUtils().JSON.decodeFromString<List<String>>(
            configManager.getCurrentConfig().sourceExclusions,
        )
}
