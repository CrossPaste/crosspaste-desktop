package com.clipevery.config

import com.clipevery.app.AppEnv
import com.clipevery.presist.OneFilePersist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

abstract class ConfigManager(private val configFilePersist: OneFilePersist,
                             private val appEnv: AppEnv) {

    var config: AppConfig

    init {
        config = try {
            loadConfig() ?: AppConfig(appEnv)
        } catch (e: Exception) {
            AppConfig(appEnv)
        }
    }

    private fun loadConfig(): AppConfig? {
        return configFilePersist.read(AppConfig::class)?.let {
            if (it.appEnv != this.appEnv) {
                return AppConfig(it, this.appEnv)
            }
            return it
        }
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