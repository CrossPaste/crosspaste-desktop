package com.crosspaste.config

import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.presist.OneFilePersist
import com.crosspaste.utils.LocaleUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.SerializationException

class DesktopConfigManager(
    private val configFilePersist: OneFilePersist,
    private val localeUtils: LocaleUtils,
) : ConfigManager<DesktopAppConfig> {

    private val logger = KotlinLogging.logger {}

    // Declared before _config: its initializer sets this flag.
    private var saveBlocked = false

    private val _config: MutableStateFlow<DesktopAppConfig> = MutableStateFlow(loadInitialConfig())

    override val config: StateFlow<DesktopAppConfig> = _config

    var notificationManager: NotificationManager? = null

    override fun loadConfig(): DesktopAppConfig? = configFilePersist.read(DesktopAppConfig::class)

    private fun createDefaultAppConfig(): DesktopAppConfig =
        DesktopAppConfig(
            language = localeUtils.getLanguage(),
        )

    /**
     * The defaults used on a failed load get written back by the next save, and
     * background writers (e.g. pasteboard services on stop) save on every exit. So a
     * user's config may only be replaced once it is safe:
     * - Corrupt content is moved aside to `.corrupt` first, then defaults may be saved.
     * - Any other failure (file locked, permission denied) may be transient, so this
     *   session runs on defaults in memory and never saves over the file.
     */
    private fun loadInitialConfig(): DesktopAppConfig =
        try {
            loadConfig() ?: createDefaultAppConfig()
        } catch (e: SerializationException) {
            runCatching { configFilePersist.quarantine() }
                .onSuccess { backupPath ->
                    logger.error(e) { "App config is corrupt; backed it up to $backupPath and using defaults" }
                }.onFailure { moveError ->
                    saveBlocked = true
                    logger.error(e) { "App config is corrupt and could not be backed up: $moveError" }
                }
            createDefaultAppConfig()
        } catch (e: Exception) {
            saveBlocked = true
            logger.error(e) { "Failed to read app config; using defaults without saving them this session" }
            createDefaultAppConfig()
        }

    override fun updateConfig(
        key: String,
        value: Any,
    ) {
        updateConfig(listOf(key), listOf(value))
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
        if (saveBlocked) {
            logger.warn { "Not saving config change to $keys: app config could not be read at startup" }
            return
        }
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
