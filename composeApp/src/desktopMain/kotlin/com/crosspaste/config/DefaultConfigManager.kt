package com.crosspaste.config

import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.presist.OneFilePersist
import com.crosspaste.ui.base.MessageType
import com.crosspaste.ui.base.NotificationManager
import com.crosspaste.utils.getDeviceUtils

class DefaultConfigManager(
    private val configFilePersist: OneFilePersist,
) : ConfigManager {
    override val deviceUtils = getDeviceUtils()

    override var config =
        try {
            loadConfig() ?: AppConfig(deviceUtils.createAppInstanceId())
        } catch (e: Exception) {
            AppConfig(deviceUtils.createAppInstanceId())
        }

    override var notificationManager: NotificationManager? = null

    override var copywriter: GlobalCopywriter? = null

    override fun loadConfig(): AppConfig? {
        return configFilePersist.read(AppConfig::class)
    }

    @Synchronized
    override fun updateConfig(
        key: String,
        value: Any,
    ) {
        val oldConfig = config
        config = oldConfig.copy(key, value)
        try {
            saveConfig(key, value, config)
        } catch (e: Exception) {
            notificationManager?.let { manager ->
                copywriter?.let {
                    manager.addNotification(
                        message = it.getText("failed_to_save_config"),
                        messageType = MessageType.Error,
                    )
                }
            }
            config = oldConfig
        }
    }

    override fun saveConfig(
        key: String,
        value: Any,
        config: AppConfig,
    ) {
        configFilePersist.save(config)
    }
}
