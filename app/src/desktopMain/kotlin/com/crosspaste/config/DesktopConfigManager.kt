package com.crosspaste.config

import androidx.datastore.preferences.core.Preferences
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.presist.FilePersist
import com.crosspaste.utils.LocaleUtils
import com.crosspaste.utils.ioDispatcher
import com.crosspaste.utils.namedScope
import kotlinx.coroutines.CoroutineScope
import okio.Path

/**
 * Desktop configuration, stored as DataStore preferences files under [configDir]
 * (one per [ConfigScope]). A legacy `appConfig.json` found there is imported on
 * the first launch and archived.
 */
class DesktopConfigManager(
    configDir: Path,
    localeUtils: LocaleUtils,
    scope: CoroutineScope = namedScope(ioDispatcher, "DesktopConfigManager"),
) : DataStoreConfigManager<DesktopAppConfig>(
        repository = createRepository(configDir, scope),
        legacyImporter =
            LegacyJsonConfigImporter(
                FilePersist.createOneFilePersist(configDir.resolve(LegacyJsonConfigImporter.FILE_NAME)),
            ),
        createConfig = configFactory(localeUtils),
        scope = scope,
    ) {

    var notificationManager: NotificationManager? = null

    override fun onSaveFailure(error: Throwable) {
        notificationManager?.sendNotification(
            title = { it.getText("failed_to_save_config") },
            messageType = MessageType.Error,
        )
    }

    companion object {

        private fun createRepository(
            configDir: Path,
            scope: CoroutineScope,
        ): ConfigRepository =
            ConfigRepository(
                schema = DesktopConfigKeys.schema,
                stores = ConfigScope.entries.associateWith { ConfigStore(configDir.resolve(it.fileName), scope) },
            )

        private fun configFactory(localeUtils: LocaleUtils): (Preferences) -> DesktopAppConfig {
            val defaults = DesktopAppConfig(language = localeUtils.getLanguage())
            return { preferences -> DesktopAppConfig.fromPreferences(preferences, defaults) }
        }
    }
}
