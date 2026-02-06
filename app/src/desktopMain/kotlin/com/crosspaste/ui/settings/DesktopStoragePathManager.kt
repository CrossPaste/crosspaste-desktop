package com.crosspaste.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Archive
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
import com.crosspaste.ui.theme.AppUISize.giant
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.small2XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.xxxxLarge
import okio.Path
import org.koin.compose.koinInject

class DesktopStoragePathManager : StoragePathManager {
    @Composable
    override fun StoragePathHeader() {
        SectionHeader("storage_directory", topPadding = medium)
    }

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

        var showMigrateStorageDialog by remember { mutableStateOf(false) }

        var useDefaultStoragePath by remember { mutableStateOf(config.useDefaultStoragePath) }

        val currentStoragePath by remember(config) {
            mutableStateOf(
                userDataPathProvider.getUserDataPath(),
            )
        }

        if (showMigrateStorageDialog) {
            migrationPath?.let {
                MigrationStorageDialog(it) {
                    showMigrateStorageDialog = false
                }
            }
        }

        SettingSectionCard {
            if (useDefaultStoragePath) {
                SettingListSwitchItem(
                    title = "use_default_storage_path",
                    icon = IconData(MaterialSymbols.Rounded.Archive, themeExt.amberIconColor),
                    checked = useDefaultStoragePath,
                ) {
                    useDefaultStoragePath = !useDefaultStoragePath
                }
                HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))
            }
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(giant)
                        .padding(horizontal = medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .clip(small2XRoundedCornerShape)
                            .clickable {
                                val action: (Path) -> Unit = { path ->
                                    migrationPath = path
                                    showMigrateStorageDialog = true
                                }
                                val errorAction: (String) -> Unit = { message ->
                                    notificationManager.sendNotification(
                                        title = { it.getText(message) },
                                        messageType = MessageType.Error,
                                        duration = null,
                                    )
                                }
                                val chooseText = copywriter.getText("selecting_storage_directory")
                                appFileChooser.openFileChooser(
                                    FileSelectionMode.DIRECTORY_ONLY,
                                    chooseText,
                                    currentStoragePath,
                                ) { path ->

                                    desktopMigration.checkMigrationPath(path as Path)?.let { errorMessage ->
                                        errorAction(errorMessage)
                                    } ?: run {
                                        action(path)
                                    }
                                }
                            },
                    value = currentStoragePath.toString(),
                    onValueChange = {},
                    enabled = useDefaultStoragePath,
                    readOnly = useDefaultStoragePath,
                    textStyle =
                        MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.primary,
                        ),
                    singleLine = false,
                    minLines = 1,
                    maxLines = 5,
                    shape = MaterialTheme.shapes.medium,
                )
            }
        }
    }
}
