package com.crosspaste.config

import com.crosspaste.app.AppEnv
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.presist.OneFilePersist
import com.crosspaste.ui.base.MessageType
import com.crosspaste.ui.base.Toast
import com.crosspaste.ui.base.ToastManager
import com.crosspaste.utils.getDeviceUtils
import kotlinx.coroutines.CoroutineScope

abstract class ConfigManager(
    private val configFilePersist: OneFilePersist,
    private val toastManager: ToastManager,
    private val lazyCopywriter: Lazy<GlobalCopywriter>,
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
        try {
            saveConfig(config)
        } catch (e: Exception) {
            toastManager.setToast(
                Toast(
                    message = lazyCopywriter.value.getText("Failed_to_save_config"),
                    messageType = MessageType.Error,
                ),
            )
            config = oldConfig
        }
    }

    @Synchronized
    fun saveConfig(config: AppConfig) {
        saveConfigImpl(config)
    }

    protected abstract fun saveConfigImpl(config: AppConfig)
}
