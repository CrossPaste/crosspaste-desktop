package com.crosspaste.config

import com.crosspaste.notification.NotificationManager
import com.crosspaste.utils.DeviceUtils
import kotlinx.coroutines.flow.StateFlow

interface ConfigManager {

    val deviceUtils: DeviceUtils

    val config: StateFlow<AppConfig>

    var notificationManager: NotificationManager?

    fun getCurrentConfig(): AppConfig {
        return config.value
    }

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

    fun saveConfig(
        key: String,
        value: Any,
        config: AppConfig,
    )
}
