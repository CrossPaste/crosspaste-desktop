package com.crosspaste.config

import com.crosspaste.utils.DeviceUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class TestConfigManager(
    override val deviceUtils: DeviceUtils,
    private val initialConfig: AppConfig,
) : CommonConfigManager {

    private val _config = MutableStateFlow(initialConfig)

    override val config: StateFlow<AppConfig> = _config

    override fun loadConfig(): AppConfig = _config.value

    override fun updateConfig(
        key: String,
        value: Any,
    ) {
        _config.value = _config.value.copy(key, value)
    }

    override fun updateConfig(
        keys: List<String>,
        values: List<Any>,
    ) {
        var current = _config.value
        for (i in keys.indices) {
            current = current.copy(keys[i], values[i])
        }
        _config.value = current
    }
}
