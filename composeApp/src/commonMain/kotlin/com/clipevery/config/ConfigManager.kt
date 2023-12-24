package com.clipevery.config

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Suppress("LeakingThis")
abstract class ConfigManager {

    var config: AppConfig

    init {
        config = try {
            loadConfig() ?: AppConfig()
        } catch (e: Exception) {
            AppConfig()
        }
    }

    protected abstract fun loadConfig(): AppConfig?

    protected abstract fun ioScope(): CoroutineScope

    @Synchronized
    fun updateConfig(updateAction: (AppConfig) -> AppConfig) {
        config = updateAction(config)
        ioScope().launch {
            saveConfig(config)
        }
    }

    @Synchronized
    fun saveConfig(config: AppConfig) {
        saveConfigImpl(config)
    }

    protected abstract fun saveConfigImpl(config: AppConfig)
}