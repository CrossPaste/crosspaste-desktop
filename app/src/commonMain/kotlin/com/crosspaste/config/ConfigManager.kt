package com.crosspaste.config

import com.crosspaste.notification.NotificationManager
import com.crosspaste.utils.DeviceUtils

interface ConfigManager {

    val deviceUtils: DeviceUtils

    var config: AppConfig

    var notificationManager: NotificationManager?

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
