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
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
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
import com.crosspaste.LocalExitApplication
import com.crosspaste.app.AppExitService
import com.crosspaste.app.AppRestartService
import com.crosspaste.app.AppWindowManager
import com.crosspaste.app.ExitMode
import com.crosspaste.config.ConfigManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.realm.RealmManager
import com.crosspaste.ui.base.CustomSwitch
import com.crosspaste.ui.base.CustomTextField
import com.crosspaste.ui.base.DialogButtonsView
import com.crosspaste.ui.base.DialogService
import com.crosspaste.ui.base.MessageType
import com.crosspaste.ui.base.NotificationManager
import com.crosspaste.ui.base.PasteDialog
import com.crosspaste.ui.base.archive
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

    Text(
        modifier =
            Modifier.wrapContentSize()
                .padding(start = 32.dp, top = 5.dp, bottom = 5.dp),
        text = copywriter.getText("storage_path"),
        color = MaterialTheme.colors.onBackground,
        style = MaterialTheme.typography.h6,
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp,
    )

    Column(
        modifier =
            Modifier.wrapContentSize()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colors.background),
    ) {
        var useDefaultStoragePath by remember { mutableStateOf(configManager.config.useDefaultStoragePath) }

        val currentStoragePath by remember {
            mutableStateOf(
                userDataPathProvider.getUserDataPath().toString(),
            )
        }

        if (configManager.config.useDefaultStoragePath) {
            SettingItemView(
                painter = archive(),
                text = "use_default_storage_path",
                tint = Color(0xFF41B06E),
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
                            val chooseText = copywriter.getText("selecting_storage_directory")
                            appWindowManager.openFileChooser(chooseText, currentStoragePath, { path ->
                                dialogService.pushDialog(
                                    PasteDialog(
                                        key = "storagePath",
                                        title = "determining_the_new_storage_path",
                                        width = 320.dp,
                                    ) {
                                        SetStoragePathDialogView(path)
                                    },
                                )
                            }) {
                                notificationManager.addNotification(
                                    message = it,
                                    messageType = MessageType.Error,
                                )
                            }
                        },
                value = currentStoragePath,
                onValueChange = {},
                enabled = useDefaultStoragePath,
                readOnly = useDefaultStoragePath,
                singleLine = true,
                textStyle =
                    LocalTextStyle.current.copy(
                        textAlign = TextAlign.Start,
                        color = if (!useDefaultStoragePath) MaterialTheme.colors.primary else Color.LightGray,
                        fontSize = if (!useDefaultStoragePath) 12.sp else 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 14.sp,
                    ),
                colors =
                    TextFieldDefaults.textFieldColors(
                        focusedIndicatorColor = MaterialTheme.colors.primary,
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
    val realmManager = koinInject<RealmManager>()
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
            try {
                userDataPathProvider.migration(path) {
                    realmManager.writeCopyTo(it)
                }
                coroutineScope.launch {
                    progress = 1f
                    delay(500)
                    isMigration = false
                }
                isMigration = false
            } catch (e: Exception) {
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
                        color = MaterialTheme.colors.primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 14.sp,
                    ),
                colors =
                    TextFieldDefaults.textFieldColors(
                        focusedIndicatorColor = MaterialTheme.colors.primary,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                contentPadding = PaddingValues(horizontal = 8.dp),
            )
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            if (isMigration) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(5.dp),
                    progress = progress,
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
