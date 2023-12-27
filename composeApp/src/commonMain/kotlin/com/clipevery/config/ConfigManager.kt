package com.clipevery.config

import com.clipevery.presist.OneFilePersist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

abstract class ConfigManager(private val configFilePersist: OneFilePersist) {

    var config: AppConfig

    init {
        config = try {
            loadConfig() ?: AppConfig()
        } catch (e: Exception) {
            AppConfig()
        }
    }

    private fun loadConfig(): AppConfig? {
        return configFilePersist.read(AppConfig::class)
    }

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