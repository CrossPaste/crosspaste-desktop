package com.crosspaste.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.net.NetworkInterfaceInfo
import com.crosspaste.net.NetworkInterfaceService
import com.crosspaste.ui.LocalDesktopAppSizeValueState
import com.crosspaste.ui.base.CustomSwitch
import com.crosspaste.ui.base.link
import com.crosspaste.ui.base.wifi
import com.crosspaste.ui.devices.SyncScopeFactory
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUISize.large2X
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.small2X
import com.crosspaste.ui.theme.AppUISize.xLarge
import com.crosspaste.ui.theme.AppUISize.xxxLarge
import com.crosspaste.utils.getJsonUtils
import org.koin.compose.koinInject

@Composable
fun NetSettingsContentView() {
    val configManager = koinInject<DesktopConfigManager>()
    val copywriter = koinInject<GlobalCopywriter>()
    val networkInterfaceService = koinInject<NetworkInterfaceService>()
    val syncScopeFactory = koinInject<SyncScopeFactory>()

    val appSizeValue = LocalDesktopAppSizeValueState.current

    val jsonUtils = getJsonUtils()

    var port by remember { mutableStateOf<String?>(null) }

    val config by configManager.config.collectAsState()

    var networkInterfaces by remember { mutableStateOf(listOf<NetworkInterfaceInfo>()) }

    val useNetworkInterfaces: List<String> = jsonUtils.JSON.decodeFromString(config.useNetworkInterfaces)

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

    Column(
        modifier =
            Modifier
                .wrapContentSize()
                .background(AppUIColors.generalBackground),
    ) {
        SettingItemsTitleView("network_info")

        SettingItemView(
            painter = wifi(),
            text = "allow_discovery_by_new_devices",
        ) {
            CustomSwitch(
                modifier =
                    Modifier
                        .width(medium * 2)
                        .height(large2X),
                checked = useNetworkInterfaces.isNotEmpty(),
                onCheckedChange = { enableDiscovery ->
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
                },
            )
        }

        HorizontalDivider(modifier = Modifier.padding(start = xxxLarge))

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

        HorizontalDivider(modifier = Modifier.padding(start = xxxLarge))

        SettingItemView(
            painter = link(),
            text = "port",
        ) {
            port?.let {
                SettingsText(text = it)
            } ?: run {
                CircularProgressIndicator(modifier = Modifier.size(xLarge))
            }
        }
    }

    Column(
        modifier =
            Modifier
                .wrapContentSize()
                .background(AppUIColors.generalBackground),
    ) {
        SettingItemsTitleView("blacklist")

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val blacklist =
                remember(config) {
                    val list: List<SyncInfo> = jsonUtils.JSON.decodeFromString(config.blacklist)
                    mutableStateListOf(*list.toTypedArray())
                }

            if (blacklist.isEmpty()) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(appSizeValue.deviceHeight)
                            .padding(start = small2X),
                    verticalArrangement = Arrangement.Center,
                ) {
                    SettingsText(text = copywriter.getText("empty"))
                }
            } else {
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = appSizeValue.deviceHeight * 3),
                    verticalArrangement = Arrangement.Top,
                ) {
                    itemsIndexed(blacklist) { index, syncInfo ->
                        val currentIndex by rememberUpdatedState(index)
                        val currentSyncInfo by rememberUpdatedState(syncInfo)

                        val scope =
                            remember(currentSyncInfo) {
                                syncScopeFactory.createSyncScope(currentSyncInfo)
                            }

                        scope.BlackListDeviceView()

                        if (currentIndex != blacklist.size - 1) {
                            HorizontalDivider(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}
