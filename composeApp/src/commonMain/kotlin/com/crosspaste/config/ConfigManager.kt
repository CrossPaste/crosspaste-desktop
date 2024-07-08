package com.crosspaste.config

import com.crosspaste.utils.DeviceUtils

interface ConfigManager {
    val deviceUtils: DeviceUtils

    var config: AppConfig

    fun loadConfig(): AppConfig?

    fun updateConfig(updateAction: (AppConfig) -> AppConfig)

    fun saveConfig(config: AppConfig)
}
