package com.crosspaste.paste

import com.crosspaste.config.CommonConfigManager
import com.crosspaste.config.DesktopAppConfig
import com.crosspaste.utils.getJsonUtils

class DesktopSourceExclusionService(
    private val configManager: CommonConfigManager,
) {

    private fun getSourceExclusions(): String = (configManager.getCurrentConfig() as DesktopAppConfig).sourceExclusions

    fun isExcluded(source: String?): Boolean {
        if (source == null) return false
        val exclusions: List<String> =
            getJsonUtils().JSON.decodeFromString<List<String>>(
                getSourceExclusions(),
            )
        return source in exclusions
    }

    fun addExclusion(source: String) {
        val jsonUtils = getJsonUtils()
        val current: MutableList<String> =
            jsonUtils.JSON
                .decodeFromString<List<String>>(
                    getSourceExclusions(),
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
                    getSourceExclusions(),
                ).filter { it != source }
        configManager.updateConfig(
            "sourceExclusions",
            jsonUtils.JSON.encodeToString(current),
        )
    }

    fun getExclusions(): List<String> =
        getJsonUtils().JSON.decodeFromString<List<String>>(
            getSourceExclusions(),
        )
}
