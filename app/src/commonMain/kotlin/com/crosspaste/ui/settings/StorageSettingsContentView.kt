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
import com.composables.icons.materialsymbols.rounded.Auto_delete
import com.composables.icons.materialsymbols.rounded.Docs
import com.composables.icons.materialsymbols.rounded.Download
import com.composables.icons.materialsymbols.rounded.Hourglass_empty
import com.composables.icons.materialsymbols.rounded.Image
import com.composables.icons.materialsymbols.rounded.Percent
import com.composables.icons.materialsymbols.rounded.Storage
import com.composables.icons.materialsymbols.rounded.Title
import com.crosspaste.app.AppFileChooser
import com.crosspaste.app.FileSelectionMode
import com.crosspaste.clean.CleanTime
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.config.resolveLargeFileDestination
import com.crosspaste.config.validateLargeFileDestination
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.ui.LocalThemeExtState
import com.crosspaste.ui.base.Counter
import com.crosspaste.ui.base.FilledDropdown
import com.crosspaste.ui.base.IconData
import com.crosspaste.ui.base.SectionHeader
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.small2X
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.xxLarge
import com.crosspaste.ui.theme.AppUISize.xxxxLarge
import okio.Path
import org.koin.compose.koinInject

@Composable
fun StorageSettingsContentView(storagePathManager: StoragePathManager? = null) {
    val configManager = koinInject<CommonConfigManager>()
    val copywriter = koinInject<GlobalCopywriter>()
    val pasteDao = koinInject<PasteDao>()
    val themeExt = LocalThemeExtState.current

    val config by configManager.config.collectAsState()

    val storageStatisticsScope = remember { StorageStatisticsScope(pasteDao) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(tiny),
    ) {
        item {
            storageStatisticsScope.StorageStatisticsHeader()
        }

        item {
            storageStatisticsScope.StorageStatisticsContentView()
        }

        storagePathManager?.let {
            item {
                it.StoragePathHeader()
            }

            item {
                it.StoragePathContentView()
            }
        }

        item {
            SectionHeader("size_limits", topPadding = medium)
        }

        item {
            SizeLimitsCard()
        }

        item {
            SectionHeader("auto_cleanup_settings", topPadding = medium)
        }

        item {
            SettingSectionCard {
                SettingListSwitchItem(
                    title = "expiration_cleanup",
                    icon = IconData(MaterialSymbols.Rounded.Hourglass_empty, themeExt.amberIconColor),
                    checked = config.enableExpirationCleanup,
                ) {
                    configManager.updateConfig("enableExpirationCleanup", it)
                }
                DependentSettings(visible = config.enableExpirationCleanup) {
                    val cleanTimeMenuTexts =
                        remember(copywriter.language()) {
                            CleanTime.entries.map { cleanTime ->
                                "${cleanTime.quantity} ${copywriter.getText(cleanTime.unit)}"
                            }
                        }

                    HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))
                    SettingListItem(
                        title = "image_retention_period",
                        icon = IconData(MaterialSymbols.Rounded.Image, themeExt.greenIconColor),
                        trailingContent = {
                            FilledDropdown(
                                selectedIndex = config.imageCleanTimeIndex,
                                options = cleanTimeMenuTexts,
                                onSelected = { index ->
                                    configManager.updateConfig("imageCleanTimeIndex", index)
                                },
                            )
                        },
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))
                    SettingListItem(
                        title = "file_retention_period",
                        icon = IconData(MaterialSymbols.Rounded.Docs, themeExt.purpleIconColor),
                        trailingContent = {
                            FilledDropdown(
                                selectedIndex = config.fileCleanTimeIndex,
                                options = cleanTimeMenuTexts,
                                onSelected = { index ->
                                    configManager.updateConfig("fileCleanTimeIndex", index)
                                },
                            )
                        },
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))
                SettingListSwitchItem(
                    title = "threshold_cleanup",
                    icon = IconData(MaterialSymbols.Rounded.Auto_delete, themeExt.redIconColor),
                    checked = config.enableThresholdCleanup,
                ) {
                    configManager.updateConfig("enableThresholdCleanup", it)
                }
                DependentSettings(visible = config.enableThresholdCleanup) {
                    HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))
                    SettingListItem(
                        title = "maximum_storage",
                        icon = IconData(MaterialSymbols.Rounded.Storage, themeExt.blueIconColor),
                        trailingContent = {
                            Counter(defaultValue = config.maxStorage, unit = "MB", rule = { it >= 256 }) {
                                configManager.updateConfig("maxStorage", it)
                            }
                        },
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))
                    SettingListItem(
                        title = "cleanup_percentage",
                        icon = IconData(MaterialSymbols.Rounded.Percent, themeExt.yellowIconColor),
                        trailingContent = {
                            Counter(
                                defaultValue = config.cleanupPercentage.toLong(),
                                unit = "%",
                                rule = { it in 10..50 },
                            ) {
                                configManager.updateConfig("cleanupPercentage", it.toInt())
                            }
                        },
                    )
                }
            }
        }
    }
}

/**
 * The thresholds that decide what enters managed storage in the first place.
 *
 * The large file destination row is always shown, never folded behind the
 * backup size: at 0 MB every file is treated as large, so that is exactly when
 * the destination matters most.
 */
@Composable
private fun SizeLimitsCard() {
    val appFileChooser = koinInject<AppFileChooser>()
    val configManager = koinInject<CommonConfigManager>()
    val copywriter = koinInject<GlobalCopywriter>()
    val notificationManager = koinInject<NotificationManager>()
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

    SettingSectionCard {
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
        HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))
        SettingListItem(
            title = "file_storage_limit",
            subtitle = "file_storage_limit_desc",
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
    }
}
