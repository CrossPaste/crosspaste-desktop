package com.clipevery.config

import com.clipevery.AppConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

abstract class ConfigManager(private val ioScope: CoroutineScope) {

    lateinit var config: AppConfig

    fun initConfig(): ConfigManager {
        config = try {
            loadConfig() ?: AppConfig()
        } catch (e: Exception) {
            AppConfig()
        }
        return this
    }

    abstract fun loadConfig(): AppConfig?

    @Synchronized
    fun updateBindingState(bindingState: Boolean) {
        config = config.copy(bindingState = bindingState)
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