package com.clipevery.config

import com.clipevery.app.AppEnv
import com.clipevery.presist.OneFilePersist
import com.clipevery.utils.getDeviceUtils
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

abstract class ConfigManager(
    private val configFilePersist: OneFilePersist,
) {
    private val appEnv = AppEnv.CURRENT

    private val deviceUtils = getDeviceUtils()

    var config: AppConfig

    init {
        config =
            try {
                loadConfig() ?: AppConfig(appEnv, deviceUtils.createAppInstanceId())
            } catch (e: Exception) {
                AppConfig(appEnv, deviceUtils.createAppInstanceId())
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
        val oldConfig = config
        config = updateAction(oldConfig)
        ioScope().launch(CoroutineName("UpdateConfig")) {
            try {
                saveConfig(config)
            } catch (e: Exception) {
                // todo Pop-up window prompts user
                config = oldConfig
            }
        }
    }

    @Synchronized
    fun saveConfig(config: AppConfig) {
        saveConfigImpl(config)
    }

    protected abstract fun saveConfigImpl(config: AppConfig)
}
