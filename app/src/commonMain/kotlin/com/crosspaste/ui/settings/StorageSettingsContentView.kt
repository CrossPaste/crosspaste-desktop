package com.crosspaste.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Auto_delete
import com.composables.icons.materialsymbols.rounded.Docs
import com.composables.icons.materialsymbols.rounded.Hourglass_empty
import com.composables.icons.materialsymbols.rounded.Image
import com.composables.icons.materialsymbols.rounded.Percent
import com.composables.icons.materialsymbols.rounded.Storage
import com.crosspaste.clean.CleanTime
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.LocalThemeExtState
import com.crosspaste.ui.base.Counter
import com.crosspaste.ui.base.FilledDropdown
import com.crosspaste.ui.base.IconData
import com.crosspaste.ui.base.SectionHeader
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.xxxxLarge
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
                if (config.enableExpirationCleanup) {
                    HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))
                    SettingListItem(
                        title = "image_retention_period",
                        icon = IconData(MaterialSymbols.Rounded.Image, themeExt.greenIconColor),
                        trailingContent = {
                            val cleanTimeMenuTexts by remember(copywriter.language()) {
                                mutableStateOf(
                                    CleanTime.entries.map { cleanTime ->
                                        "${cleanTime.quantity} ${copywriter.getText(cleanTime.unit)}"
                                    },
                                )
                            }

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
                            val cleanTimeMenuTexts by remember(copywriter.language()) {
                                mutableStateOf(
                                    CleanTime.entries.map { cleanTime ->
                                        "${cleanTime.quantity} ${copywriter.getText(cleanTime.unit)}"
                                    },
                                )
                            }

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
                if (config.enableThresholdCleanup) {
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
