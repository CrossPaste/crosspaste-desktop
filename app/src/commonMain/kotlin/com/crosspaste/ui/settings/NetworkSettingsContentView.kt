package com.crosspaste.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Docs
import com.composables.icons.materialsymbols.rounded.Power
import com.composables.icons.materialsymbols.rounded.Shield
import com.composables.icons.materialsymbols.rounded.Sync_alt
import com.composables.icons.materialsymbols.rounded.Visibility
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.net.NetworkInterfaceInfo
import com.crosspaste.net.NetworkInterfaceService
import com.crosspaste.ui.LocalThemeExtState
import com.crosspaste.ui.base.Counter
import com.crosspaste.ui.base.IconData
import com.crosspaste.ui.base.SectionHeader
import com.crosspaste.ui.devices.SyncScopeFactory
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.xxxxLarge
import com.crosspaste.utils.getJsonUtils
import org.koin.compose.koinInject

@Composable
fun NetworkSettingsContentView(syncExtContent: @Composable () -> Unit = {}) {
    val configManager = koinInject<CommonConfigManager>()
    val copywriter = koinInject<GlobalCopywriter>()
    val networkInterfaceService = koinInject<NetworkInterfaceService>()
    val syncScopeFactory = koinInject<SyncScopeFactory>()
    val themeExt = LocalThemeExtState.current

    val jsonUtils = getJsonUtils()

    var port by remember { mutableStateOf<String?>(null) }

    val config by configManager.config.collectAsState()

    var networkInterfaces by remember { mutableStateOf(listOf<NetworkInterfaceInfo>()) }

    val useNetworkInterfaces: List<String> = jsonUtils.JSON.decodeFromString(config.useNetworkInterfaces)

    val blacklist =
        remember(config) {
            val list: List<SyncInfo> = jsonUtils.JSON.decodeFromString(config.blacklist)
            mutableStateListOf(*list.toTypedArray())
        }

    LaunchedEffect(Unit) {
        networkInterfaces = networkInterfaceService.getAllNetworkInterfaceInfo()
        val currentPort = config.port
        port =
            if (currentPort <= 0) {
                copywriter.getText("unknown")
            } else {
                currentPort.toString()
            }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(tiny),
    ) {
        item {
            SettingSectionCard {
                SettingListSwitchItem(
                    title = "allow_discovery_by_new_devices",
                    icon = IconData(MaterialSymbols.Rounded.Visibility, themeExt.blueIconColor),
                    checked = useNetworkInterfaces.isNotEmpty(),
                ) { enableDiscovery ->
                    if (enableDiscovery) {
                        networkInterfaceService.getPreferredNetworkInterface()?.let {
                            val newUseNetworkInterfaces = listOf(it.name)
                            val newUseNetworkInterfacesJson =
                                jsonUtils.JSON.encodeToString(newUseNetworkInterfaces)
                            configManager.updateConfig(
                                listOf("useNetworkInterfaces", "enableDiscovery"),
                                listOf(newUseNetworkInterfacesJson, true),
                            )
                        }
                    } else {
                        configManager.updateConfig(
                            listOf("useNetworkInterfaces", "enableDiscovery"),
                            listOf("[]", false),
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))
                SettingCheckboxView(
                    list = networkInterfaces.map { it.name },
                    getCurrentCheckboxValue = { index ->
                        config.useNetworkInterfaces.contains(networkInterfaces.map { it.name }[index])
                    },
                    onChange = { index, isChecked ->
                        val currentInterface = networkInterfaces.map { it.name }[index]
                        val newUseNetworkInterfaces =
                            if (isChecked) {
                                useNetworkInterfaces + currentInterface
                            } else {
                                useNetworkInterfaces - currentInterface
                            }
                        val newUseNetworkInterfacesJson = jsonUtils.JSON.encodeToString(newUseNetworkInterfaces)
                        configManager.updateConfig(
                            listOf("useNetworkInterfaces", "enableDiscovery"),
                            listOf(newUseNetworkInterfacesJson, newUseNetworkInterfaces.isNotEmpty()),
                        )
                    },
                    trailingContent = { index ->
                        val info = networkInterfaces[index]
                        Text(
                            text = "${info.hostAddress}/${info.networkPrefixLength}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
                HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))
                SettingListItem(
                    title = "port",
                    icon = IconData(MaterialSymbols.Rounded.Power, themeExt.purpleIconColor),
                    trailingContent = {
                        port?.let {
                            Text(text = it)
                        } ?: run {
                            CircularProgressIndicator()
                        }
                    },
                )
            }
        }

        item {
            SectionHeader("sync_settings", topPadding = medium)
        }

        item {
            SettingSectionCard {
                SettingListSwitchItem(
                    title = "encrypted_sync",
                    icon = IconData(MaterialSymbols.Rounded.Shield, themeExt.greenIconColor),
                    checked = config.enableEncryptSync,
                ) {
                    configManager.updateConfig("enableEncryptSync", it)
                }
                HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))
                SettingListSwitchItem(
                    title = "sync_file_size_limit",
                    icon = IconData(MaterialSymbols.Rounded.Sync_alt, themeExt.indigoIconColor),
                    checked = config.enabledSyncFileSizeLimit,
                ) { newEnabledSyncFileSizeLimit ->
                    configManager.updateConfig("enabledSyncFileSizeLimit", newEnabledSyncFileSizeLimit)
                }
                HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))
                SettingListItem(
                    title = "max_sync_file_size",
                    icon = IconData(MaterialSymbols.Rounded.Docs, themeExt.redIconColor),
                    trailingContent = {
                        Counter(defaultValue = config.maxSyncFileSize, unit = "MB", rule = {
                            it >= 0
                        }) { currentMaxSyncFileSize ->
                            configManager.updateConfig("maxSyncFileSize", currentMaxSyncFileSize)
                        }
                    },
                )
                syncExtContent()
            }
        }

        item {
            SectionHeader("sync_content_types", topPadding = medium)
        }

        item {
            SettingSectionCard {
                SettingListSwitchItem(
                    title = "sync_text",
                    icon = themeExt.textTypeIconData,
                    checked = config.enableSyncText,
                ) { enableSyncText ->
                    configManager.updateConfig("enableSyncText", enableSyncText)
                }
                HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))
                SettingListSwitchItem(
                    title = "sync_url",
                    icon = themeExt.urlTypeIconData,
                    checked = config.enableSyncUrl,
                ) { enableSyncUrl ->
                    configManager.updateConfig("enableSyncUrl", enableSyncUrl)
                }
                HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))
                SettingListSwitchItem(
                    title = "sync_html",
                    icon = themeExt.htmlTypeIconData,
                    checked = config.enableSyncHtml,
                ) { enableSyncHtml ->
                    configManager.updateConfig("enableSyncHtml", enableSyncHtml)
                }
                HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))
                SettingListSwitchItem(
                    title = "sync_rtf",
                    icon = themeExt.rtfTypeIconData,
                    checked = config.enableSyncRtf,
                ) { enableSyncRtf ->
                    configManager.updateConfig("enableSyncRtf", enableSyncRtf)
                }
                HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))
                SettingListSwitchItem(
                    title = "sync_image",
                    icon = themeExt.imageTypeIconData,
                    checked = config.enableSyncImage,
                ) { enableSyncImage ->
                    configManager.updateConfig("enableSyncImage", enableSyncImage)
                }
                HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))
                SettingListSwitchItem(
                    title = "sync_file",
                    icon = themeExt.fileTypeIconData,
                    checked = config.enableSyncFile,
                ) { enableSyncFile ->
                    configManager.updateConfig("enableSyncFile", enableSyncFile)
                }
                HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))
                SettingListSwitchItem(
                    title = "sync_color",
                    icon = themeExt.colorTypeIconData,
                    checked = config.enableSyncColor,
                ) { enableSyncColor ->
                    configManager.updateConfig("enableSyncColor", enableSyncColor)
                }
            }
        }

        item {
            SectionHeader("blacklist", topPadding = medium)
        }

        if (blacklist.isEmpty()) {
            item {
                SettingSectionCard {
                    SettingListItem(
                        title = "empty",
                        icon = null,
                        trailingContent = null,
                    )
                }
            }
        } else {
            itemsIndexed(blacklist) { index, syncInfo ->
                val currentIndex by rememberUpdatedState(index)
                val currentSyncInfo by rememberUpdatedState(syncInfo)

                val scope =
                    remember(currentSyncInfo) {
                        syncScopeFactory.createSyncScope(currentSyncInfo)
                    }

                scope.BlackListDeviceView()

                if (currentIndex != blacklist.size - 1) {
                    Spacer(modifier = Modifier.height(tiny))
                }
            }
        }
    }
}
