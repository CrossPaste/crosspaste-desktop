package com.crosspaste.config

import com.crosspaste.utils.DeviceUtils
import kotlinx.coroutines.flow.StateFlow

typealias CommonConfigManager = ConfigManager<AppConfig>

interface ConfigManager<T : AppConfig> {

    val deviceUtils: DeviceUtils

    val config: StateFlow<T>

    fun getCurrentConfig(): T = config.value

    fun loadConfig(): T?

    fun updateConfig(
        key: String,
        value: Any,
    )

    fun updateConfig(
        keys: List<String>,
        values: List<Any>,
    )
}
