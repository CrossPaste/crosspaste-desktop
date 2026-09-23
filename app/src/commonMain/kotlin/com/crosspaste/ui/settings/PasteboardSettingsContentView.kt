package com.crosspaste.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Archive
import com.composables.icons.materialsymbols.rounded.Content_paste
import com.composables.icons.materialsymbols.rounded.Download
import com.composables.icons.materialsymbols.rounded.Link
import com.composables.icons.materialsymbols.rounded.Music_note
import com.composables.icons.materialsymbols.rounded.Skip_next
import com.composables.icons.materialsymbols.rounded.Stacks
import com.composables.icons.materialsymbols.rounded.Title
import com.crosspaste.app.AppFileChooser
import com.crosspaste.app.FileSelectionMode
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.config.resolveLargeFileDestination
import com.crosspaste.config.validateLargeFileDestination
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.paste.PasteboardService
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.ui.LocalThemeExtState
import com.crosspaste.ui.base.Counter
import com.crosspaste.ui.base.IconData
import com.crosspaste.ui.theme.AppUISize.small2X
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.xxLarge
import com.crosspaste.ui.theme.AppUISize.xxxxLarge
import okio.Path
import org.koin.compose.koinInject

@Composable
fun PasteboardSettingsContentView(extContent: @Composable () -> Unit = {}) {
    val appFileChooser = koinInject<AppFileChooser>()
    val configManager = koinInject<CommonConfigManager>()
    val copywriter = koinInject<GlobalCopywriter>()
    val notificationManager = koinInject<NotificationManager>()
    val pasteboardService = koinInject<PasteboardService>()
    val userDataPathProvider = koinInject<UserDataPathProvider>()
    val themeExt = LocalThemeExtState.current

    val config by configManager.config.collectAsState()

    val largeFileDestination =
        remember(config.largeFileDestinationPath) {
            config.resolveLargeFileDestination()
        }

    val chooseLargeFileDestination = {
        appFileChooser.openFileChooser(
            FileSelectionMode.DIRECTORY_ONLY,
            largeFileDestination,
        ) { path ->
            val destination = path as Path
            validateLargeFileDestination(
                destination = destination,
                managedStoragePath = userDataPathProvider.getUserDataPath(),
            )?.let { errorMessage ->
                notificationManager.sendNotification(
                    title = { it.getText(errorMessage) },
                    messageType = MessageType.Error,
                    duration = null,
                )
            } ?: run {
                configManager.updateConfig("largeFileDestinationPath", destination.toString())
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(tiny),
    ) {
        item {
            SettingSectionCard {
                SettingListSwitchItem(
                    title = "clipboard_monitoring",
                    icon = IconData(MaterialSymbols.Rounded.Content_paste, themeExt.blueIconColor),
                    checked = config.enablePasteboardListening,
                ) {
                    pasteboardService.toggle()
                }
                HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))
                SettingListSwitchItem(
                    title = "paste_primary_type_only",
                    icon = IconData(MaterialSymbols.Rounded.Stacks, themeExt.amberIconColor),
                    checked = config.pastePrimaryTypeOnly,
                ) { newPastePrimaryTypeOnly ->
                    configManager.updateConfig(
                        "pastePrimaryTypeOnly",
                        newPastePrimaryTypeOnly,
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))
                SettingListSwitchItem(
                    title = "skip_pre_launch_pasteboard_content",
                    icon = IconData(MaterialSymbols.Rounded.Skip_next, themeExt.purpleIconColor),
                    checked = config.enableSkipPreLaunchPasteboardContent,
                ) { newEnableSkipPreLaunchPasteboardContent ->
                    configManager.updateConfig(
                        "enableSkipPreLaunchPasteboardContent",
                        newEnableSkipPreLaunchPasteboardContent,
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))
                SettingListSwitchItem(
                    title = "sound_effect",
                    icon = IconData(MaterialSymbols.Rounded.Music_note, themeExt.redIconColor),
                    checked = config.enableSoundEffect,
                ) { enableSoundEffect ->
                    configManager.updateConfig(
                        "enableSoundEffect",
                        enableSoundEffect,
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))
                SettingListSwitchItem(
                    title = "url_preview",
                    subtitle = "url_preview_desc",
                    icon = IconData(MaterialSymbols.Rounded.Link, themeExt.indigoIconColor),
                    checked = config.enableUrlPreview,
                ) { enableUrlPreview ->
                    configManager.updateConfig(
                        "enableUrlPreview",
                        enableUrlPreview,
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))
                SettingListItem(
                    title = "max_back_up_file_size",
                    icon = IconData(MaterialSymbols.Rounded.Archive, themeExt.greenIconColor),
                    trailingContent = {
                        Counter(defaultValue = config.maxBackupFileSize, unit = "MB", rule = {
                            it >= 0
                        }) { currentMaxStorage ->
                            configManager.updateConfig("maxBackupFileSize", currentMaxStorage)
                        }
                    },
                )
                HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))
                SettingListItem(
                    title = "large_file_destination",
                    subtitleContent = {
                        Text(
                            text = largeFileDestination.toString(),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    icon = IconData(MaterialSymbols.Rounded.Download, themeExt.amberIconColor),
                    trailingContent = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(tiny),
                        ) {
                            if (config.largeFileDestinationPath.isNotBlank()) {
                                TextButton(
                                    onClick = {
                                        configManager.updateConfig("largeFileDestinationPath", "")
                                    },
                                    modifier = Modifier.height(xxLarge),
                                    contentPadding = PaddingValues(horizontal = small2X),
                                ) {
                                    Text(
                                        text = copywriter.getText("use_default_folder"),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                            FilledTonalButton(
                                onClick = chooseLargeFileDestination,
                                modifier = Modifier.height(xxLarge),
                                contentPadding = PaddingValues(horizontal = small2X),
                            ) {
                                Text(
                                    text = copywriter.getText("change"),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    },
                )
                HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))
                SettingListItem(
                    title = "max_non_file_paste_size",
                    subtitle = "max_non_file_paste_size_desc",
                    icon = IconData(MaterialSymbols.Rounded.Title, themeExt.cyanIconColor),
                    trailingContent = {
                        Counter(defaultValue = config.maxNonFilePasteSize, unit = "MB", rule = {
                            it in 1..64
                        }) { currentMaxNonFilePasteSize ->
                            configManager.updateConfig("maxNonFilePasteSize", currentMaxNonFilePasteSize)
                        }
                    },
                )
                extContent()
            }
        }
    }
}
