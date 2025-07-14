package com.crosspaste.config

import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.presist.OneFilePersist
import com.crosspaste.utils.DeviceUtils
import com.crosspaste.utils.LocaleUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class DesktopConfigManager(
    private val configFilePersist: OneFilePersist,
    override val deviceUtils: DeviceUtils,
    private val localeUtils: LocaleUtils,
) : ConfigManager<DesktopAppConfig> {

    private val _config: MutableStateFlow<DesktopAppConfig> =
        MutableStateFlow(
            runCatching {
                loadConfig() ?: createDefaultAppConfig()
            }.getOrElse {
                createDefaultAppConfig()
            },
        )

    override val config: StateFlow<DesktopAppConfig> = _config

    override var notificationManager: NotificationManager? = null

    override fun loadConfig(): DesktopAppConfig? = configFilePersist.read(DesktopAppConfig::class)

    private fun createDefaultAppConfig(): DesktopAppConfig =
        DesktopAppConfig(
            appInstanceId = deviceUtils.createAppInstanceId(),
            language = localeUtils.getLanguage(),
        )

    @Synchronized
    override fun updateConfig(
        key: String,
        value: Any,
    ) {
        val oldConfig = _config.value
        _config.value = oldConfig.copy(key, value)
        runCatching {
            saveConfig(key, value, _config.value)
        }.onFailure {
            notificationManager?.let { manager ->
                manager.sendNotification(
                    title = { it.getText("failed_to_save_config") },
                    messageType = MessageType.Error,
                )
            }
            _config.value = oldConfig
        }
    }

    override fun saveConfig(
        key: String,
        value: Any,
        config: DesktopAppConfig,
    ) {
        configFilePersist.save(config)
    }
}
