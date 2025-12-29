package com.crosspaste.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.crosspaste.app.AppFileChooser
import com.crosspaste.app.FileSelectionMode
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.path.DesktopMigration
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.ui.LocalDesktopAppSizeValueState
import com.crosspaste.ui.base.CustomSwitch
import com.crosspaste.ui.base.CustomTextField
import com.crosspaste.ui.base.archive
import com.crosspaste.ui.theme.AppUISize.large2X
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.small2X
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny2X
import com.crosspaste.ui.theme.DesktopAppUIFont.StorePathTextStyle
import okio.Path
import org.koin.compose.koinInject

@Composable
fun SetStoragePathView() {
    val appFileChooser = koinInject<AppFileChooser>()
    val configManager = koinInject<CommonConfigManager>()
    val copywriter = koinInject<GlobalCopywriter>()
    val desktopMigration = koinInject<DesktopMigration>()
    val notificationManager = koinInject<NotificationManager>()
    val userDataPathProvider = koinInject<UserDataPathProvider>()

    val appSizeValue = LocalDesktopAppSizeValueState.current

    val config by configManager.config.collectAsState()

    var migrationPath by remember { mutableStateOf<Path?>(null) }

    var showMigrateStorageDialog by remember { mutableStateOf(false) }

    if (showMigrateStorageDialog) {
        migrationPath?.let {
            MigrationStorageDialog(it) {
                showMigrateStorageDialog = false
            }
        }
    }

    Column(
        modifier =
            Modifier
                .wrapContentSize()
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        SettingItemsTitleView("storage_directory")

        var useDefaultStoragePath by remember { mutableStateOf(config.useDefaultStoragePath) }

        val currentStoragePath by remember(config) {
            mutableStateOf(
                userDataPathProvider.getUserDataPath(),
            )
        }

        if (useDefaultStoragePath) {
            SettingItemView(
                painter = archive(),
                text = "use_default_storage_path",
            ) {
                CustomSwitch(
                    modifier =
                        Modifier
                            .width(medium * 2)
                            .height(large2X),
                    checked = useDefaultStoragePath,
                    onCheckedChange = {
                        useDefaultStoragePath = !useDefaultStoragePath
                    },
                )
            }
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(appSizeValue.settingsItemHeight)
                    .padding(horizontal = small2X, vertical = tiny2X),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CustomTextField(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
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
                singleLine = true,
                textStyle = StorePathTextStyle(useDefaultStoragePath),
                colors =
                    TextFieldDefaults.colors(
                        focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                contentPadding = PaddingValues(horizontal = tiny),
            )
        }
    }
}
