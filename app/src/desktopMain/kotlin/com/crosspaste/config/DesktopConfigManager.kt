package com.crosspaste.config

import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.presist.OneFilePersist
import com.crosspaste.utils.DeviceUtils
import com.crosspaste.utils.LocaleUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class DesktopConfigManager(
    private val configFilePersist: OneFilePersist,
    override val deviceUtils: DeviceUtils,
    private val localeUtils: LocaleUtils,
) : ConfigManager<DesktopAppConfig> {

    private val logger = KotlinLogging.logger {}

    private val _config: MutableStateFlow<DesktopAppConfig> =
        MutableStateFlow(
            runCatching {
                loadConfig() ?: createDefaultAppConfig()
            }.getOrElse {
                createDefaultAppConfig()
            },
        )

    override val config: StateFlow<DesktopAppConfig> = _config

    var notificationManager: NotificationManager? = null

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
            saveConfig(_config.value)
        }.onFailure { e ->
            logger.error(e) { "Failed to save config" }
            notificationManager?.let { manager ->
                manager.sendNotification(
                    title = { it.getText("failed_to_save_config") },
                    messageType = MessageType.Error,
                )
            }
            _config.value = oldConfig
        }
    }

    @Synchronized
    override fun updateConfig(
        keys: List<String>,
        values: List<Any>,
    ) {
        require(keys.size == values.size)
        val oldConfig = _config.value
        var newConfig = oldConfig
        for (i in keys.indices) {
            newConfig = newConfig.copy(key = keys[i], value = values[i])
        }
        _config.value = newConfig
        runCatching {
            saveConfig(_config.value)
        }.onFailure { e ->
            logger.error(e) { "Failed to save config" }
            notificationManager?.let { manager ->
                manager.sendNotification(
                    title = { it.getText("failed_to_save_config") },
                    messageType = MessageType.Error,
                )
            }
            _config.value = oldConfig
        }
    }

    fun saveConfig(config: DesktopAppConfig) {
        configFilePersist.save(config)
    }
}
