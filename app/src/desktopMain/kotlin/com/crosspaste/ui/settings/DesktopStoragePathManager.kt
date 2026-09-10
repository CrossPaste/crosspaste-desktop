package com.crosspaste.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Folder
import com.crosspaste.app.AppFileChooser
import com.crosspaste.app.FileSelectionMode
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.path.DesktopMigration
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.ui.LocalThemeExtState
import com.crosspaste.ui.base.IconData
import com.crosspaste.ui.base.SectionHeader
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.small2X
import com.crosspaste.ui.theme.AppUISize.xxLarge
import okio.Path
import org.koin.compose.koinInject

class DesktopStoragePathManager : StoragePathManager {
    @Composable
    override fun StoragePathHeader() {
        SectionHeader("storage_directory", topPadding = medium)
    }

    /**
     * Shows where user data currently lives and offers an explicit "Change"
     * action that picks a new directory and hands it to [MigrationStorageDialog].
     *
     * Migration is one-way (the old directory keeps non-user files, so it can
     * never be selected again as an empty target), which is why this is an
     * action button rather than a switch that pretends the choice is reversible.
     */
    @Composable
    override fun StoragePathContentView() {
        val appFileChooser = koinInject<AppFileChooser>()
        val configManager = koinInject<CommonConfigManager>()
        val copywriter = koinInject<GlobalCopywriter>()
        val desktopMigration = koinInject<DesktopMigration>()
        val notificationManager = koinInject<NotificationManager>()
        val userDataPathProvider = koinInject<UserDataPathProvider>()
        val themeExt = LocalThemeExtState.current

        val config by configManager.config.collectAsState()

        var migrationPath by remember { mutableStateOf<Path?>(null) }

        val currentStoragePath =
            remember(config) {
                userDataPathProvider.getUserDataPath()
            }

        migrationPath?.let { path ->
            MigrationStorageDialog(path) {
                migrationPath = null
            }
        }

        val chooseMigrationPath = {
            appFileChooser.openFileChooser(
                FileSelectionMode.DIRECTORY_ONLY,
                currentStoragePath,
            ) { path ->
                desktopMigration.checkMigrationPath(path as Path)?.let { errorMessage ->
                    notificationManager.sendNotification(
                        title = { it.getText(errorMessage) },
                        messageType = MessageType.Error,
                        duration = null,
                    )
                } ?: run {
                    migrationPath = path
                }
            }
        }

        SettingSectionCard {
            SettingListItem(
                titleContent = {
                    Text(
                        text = currentStoragePath.toString(),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                subtitle =
                    if (config.useDefaultStoragePath) {
                        "storage_path_default"
                    } else {
                        "storage_path_custom"
                    },
                icon = IconData(MaterialSymbols.Rounded.Folder, themeExt.amberIconColor),
                trailingContent = {
                    FilledTonalButton(
                        onClick = chooseMigrationPath,
                        modifier = Modifier.height(xxLarge),
                        contentPadding = PaddingValues(horizontal = small2X),
                    ) {
                        Text(
                            text = copywriter.getText("change"),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
            )
        }
    }
}
