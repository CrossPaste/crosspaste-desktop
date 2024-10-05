package com.crosspaste.config

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.presist.OneFilePersist
import com.crosspaste.ui.base.MessageType
import com.crosspaste.ui.base.NotificationManager
import com.crosspaste.utils.DeviceUtils
import com.crosspaste.utils.LocaleUtils

class DefaultConfigManager(
    private val configFilePersist: OneFilePersist,
    override val deviceUtils: DeviceUtils,
    private val localeUtils: LocaleUtils,
) : ConfigManager {

    override var config by mutableStateOf(
        try {
            loadConfig() ?: createDefaultAppConfig()
        } catch (e: Exception) {
            createDefaultAppConfig()
        },
    )

    override var notificationManager: NotificationManager? = null

    override var copywriter: GlobalCopywriter? = null

    override fun loadConfig(): AppConfig? {
        return configFilePersist.read(AppConfig::class)
    }

    private fun createDefaultAppConfig(): AppConfig {
        return AppConfig(
            appInstanceId = deviceUtils.createAppInstanceId(),
            language = localeUtils.getLanguage(),
        )
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
