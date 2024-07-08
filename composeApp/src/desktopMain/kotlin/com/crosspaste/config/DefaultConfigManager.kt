package com.crosspaste.config

import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.presist.OneFilePersist
import com.crosspaste.ui.base.MessageType
import com.crosspaste.ui.base.Toast
import com.crosspaste.ui.base.ToastManager
import com.crosspaste.utils.getDeviceUtils

class DefaultConfigManager(
    private val configFilePersist: OneFilePersist,
    private val toastManager: ToastManager,
    private val lazyCopywriter: Lazy<GlobalCopywriter>,
) : ConfigManager {
    override val deviceUtils = getDeviceUtils()

    override var config =
        try {
            loadConfig() ?: AppConfig(deviceUtils.createAppInstanceId())
        } catch (e: Exception) {
            AppConfig(deviceUtils.createAppInstanceId())
        }

    override fun loadConfig(): AppConfig? {
        return configFilePersist.read(AppConfig::class)
    }

    @Synchronized
    override fun updateConfig(updateAction: (AppConfig) -> AppConfig) {
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

    override fun saveConfig(config: AppConfig) {
        configFilePersist.save(config)
    }
}
