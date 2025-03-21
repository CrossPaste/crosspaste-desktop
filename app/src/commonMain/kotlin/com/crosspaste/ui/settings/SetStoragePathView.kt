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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crosspaste.app.AppExitService
import com.crosspaste.app.AppRestartService
import com.crosspaste.app.AppWindowManager
import com.crosspaste.app.ExitMode
import com.crosspaste.app.FileSelectionMode
import com.crosspaste.config.ConfigManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.ui.LocalExitApplication
import com.crosspaste.ui.base.CustomSwitch
import com.crosspaste.ui.base.CustomTextField
import com.crosspaste.ui.base.DialogButtonsView
import com.crosspaste.ui.base.DialogService
import com.crosspaste.ui.base.PasteDialog
import com.crosspaste.ui.base.archive
import com.crosspaste.utils.getFileUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okio.Path
import org.koin.compose.koinInject

@Composable
fun SetStoragePathView() {
    val appWindowManager = koinInject<AppWindowManager>()
    val configManager = koinInject<ConfigManager>()
    val copywriter = koinInject<GlobalCopywriter>()
    val userDataPathProvider = koinInject<UserDataPathProvider>()
    val notificationManager = koinInject<NotificationManager>()
    val dialogService = koinInject<DialogService>()

    val fileUtils = getFileUtils()

    Text(
        modifier =
            Modifier.wrapContentSize()
                .padding(start = 16.dp, top = 5.dp, bottom = 5.dp),
        text = copywriter.getText("storage_directory"),
        color = MaterialTheme.colorScheme.onBackground,
        style = MaterialTheme.typography.headlineSmall,
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp,
    )

    Column(
        modifier =
            Modifier.wrapContentSize()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        var useDefaultStoragePath by remember { mutableStateOf(configManager.config.useDefaultStoragePath) }

        val currentStoragePath by remember {
            mutableStateOf(
                userDataPathProvider.getUserDataPath(),
            )
        }

        if (configManager.config.useDefaultStoragePath) {
            SettingItemView(
                painter = archive(),
                text = "use_default_storage_path",
            ) {
                CustomSwitch(
                    modifier =
                        Modifier.width(32.dp)
                            .height(20.dp),
                    checked = useDefaultStoragePath,
                    onCheckedChange = {
                        useDefaultStoragePath = !useDefaultStoragePath
                    },
                )
            }
        }

        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .height(40.dp)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CustomTextField(
                modifier =
                    Modifier.fillMaxWidth().wrapContentHeight()
                        .clickable {
                            val action: (Path) -> Unit = { path ->
                                dialogService.pushDialog(
                                    PasteDialog(
                                        key = "storagePath",
                                        title = "determining_the_new_storage_path",
                                        width = 320.dp,
                                    ) {
                                        SetStoragePathDialogView(path)
                                    },
                                )
                            }
                            val errorAction: (String) -> Unit = { message ->
                                notificationManager.sendNotification(
                                    title = { it.getText(message) },
                                    messageType = MessageType.Error,
                                )
                            }
                            val chooseText = copywriter.getText("selecting_storage_directory")
                            appWindowManager.openFileChooser(
                                FileSelectionMode.DIRECTORY_ONLY,
                                chooseText,
                                currentStoragePath,
                            ) { path ->
                                if (path.toString()
                                        .startsWith(currentStoragePath.normalized().toString())
                                ) {
                                    errorAction("cant_select_child_directory")
                                } else if (!fileUtils.existFile(path)) {
                                    errorAction("directory_not_exist")
                                } else if (fileUtils.listFiles(path) { it ->
                                        !it.name.startsWith(".")
                                    }.isNotEmpty()
                                ) {
                                    errorAction("directory_not_empty")
                                } else {
                                    action(path)
                                }
                            }
                        },
                value = currentStoragePath.toString(),
                onValueChange = {},
                enabled = useDefaultStoragePath,
                readOnly = useDefaultStoragePath,
                singleLine = true,
                textStyle =
                    LocalTextStyle.current.copy(
                        textAlign = TextAlign.Start,
                        color =
                            if (!useDefaultStoragePath) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.LightGray
                            },
                        fontSize = if (!useDefaultStoragePath) 12.sp else 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 14.sp,
                    ),
                colors =
                    TextFieldDefaults.colors(
                        focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                contentPadding = PaddingValues(horizontal = 8.dp),
            )
        }
    }
}

@Composable
fun SetStoragePathDialogView(path: Path) {
    val exitApplication = LocalExitApplication.current
    val dialogService = koinInject<DialogService>()
    val userDataPathProvider = koinInject<UserDataPathProvider>()
    val appExitService = koinInject<AppExitService>()
    val appRestartService = koinInject<AppRestartService>()
    var isError by remember { mutableStateOf(false) }
    var isMigration by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0.0f) }
    val coroutineScope = rememberCoroutineScope()

    val confirmAction = {
        appExitService.beforeExitList.clear()
        appExitService.beforeReleaseLockList.clear()

        appExitService.beforeExitList.add {
            isMigration = true
            coroutineScope.launch {
                while (progress < 0.99f && isMigration) {
                    progress += 0.01f
                    delay(100)
                }
            }
        }

        appExitService.beforeReleaseLockList.add {
            runCatching {
                userDataPathProvider.migration(path)
                coroutineScope.launch {
                    progress = 1f
                    delay(500)
                    isMigration = false
                }
                isMigration = false
            }.onFailure {
                coroutineScope.launch {
                    isMigration = false
                    isError = true
                }
            }
        }
        appRestartService.restart { exitApplication(ExitMode.MIGRATION) }
    }

    val cancelAction = {
        dialogService.popDialog()
    }

    Column {
        Row(modifier = Modifier.fillMaxWidth().height(40.dp)) {
            CustomTextField(
                modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                value = path.toString(),
                onValueChange = {},
                enabled = false,
                readOnly = true,
                singleLine = true,
                textStyle =
                    LocalTextStyle.current.copy(
                        textAlign = TextAlign.Start,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 14.sp,
                    ),
                colors =
                    TextFieldDefaults.colors(
                        focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                contentPadding = PaddingValues(horizontal = 8.dp),
            )
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            if (isMigration) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(5.dp),
                    progress = { progress },
                )
            } else {
                DialogButtonsView(
                    confirmTitle = "migrate_and_then_restart_the_app",
                    height = 40.dp,
                    cancelAction = cancelAction,
                    confirmAction = confirmAction,
                )
            }
        }
    }
}
