package com.crosspaste.config

import com.crosspaste.utils.DeviceUtils

interface ConfigManager {
    val deviceUtils: DeviceUtils

    var config: AppConfig

    fun loadConfig(): AppConfig?

    fun updateConfig(
        key: String,
        value: Any,
    )

    fun updateConfig(
        keys: List<String>,
        values: List<Any>,
    ) {
        keys.forEachIndexed { index, key ->
            updateConfig(key, values[index])
        }
    }

    fun saveConfig(config: AppConfig)
}
