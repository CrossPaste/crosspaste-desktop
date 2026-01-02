package com.crosspaste.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.net.NetworkInterfaceInfo
import com.crosspaste.net.NetworkInterfaceService
import com.crosspaste.ui.base.SectionHeader
import com.crosspaste.ui.devices.SyncScopeFactory
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.xxxxLarge
import com.crosspaste.utils.getJsonUtils
import org.koin.compose.koinInject

@Composable
fun NetworkSettingsContentView() {
    val configManager = koinInject<CommonConfigManager>()
    val copywriter = koinInject<GlobalCopywriter>()
    val networkInterfaceService = koinInject<NetworkInterfaceService>()
    val syncScopeFactory = koinInject<SyncScopeFactory>()

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
                    icon = Icons.Default.WifiFind,
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
                    list = networkInterfaces.map { it.toString() },
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
                            listOf(newUseNetworkInterfacesJson, useNetworkInterfaces.isNotEmpty()),
                        )
                    },
                )
                HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))
                SettingListItem(
                    title = "port",
                    icon = Icons.Default.Cable,
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
            SectionHeader("blacklist")
        }

        if (blacklist.isEmpty()) {
            item {
                SettingSectionCard {
                    SettingListItem(
                        title = "empty",
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
