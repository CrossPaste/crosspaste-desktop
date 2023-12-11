package com.clipevery.config

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Suppress("LeakingThis")
abstract class ConfigManager(private val ioScope: CoroutineScope) {

    var config: AppConfig

    init {
        config = try {
            loadConfig() ?: AppConfig()
        } catch (e: Exception) {
            AppConfig()
        }
    }

    protected abstract fun loadConfig(): AppConfig?

    @Synchronized
    fun updateConfig(updateAction: (AppConfig) -> AppConfig) {
        config = updateAction(config)
        ioScope.launch {
            saveConfig(config)
        }
    }

    @Synchronized
    fun saveConfig(config: AppConfig) {
        saveConfigImpl(config)
    }

    protected abstract fun saveConfigImpl(config: AppConfig)
}