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
import com.crosspaste.app.AppSize
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.net.NetworkInterfaceInfo
import com.crosspaste.net.NetworkInterfaceService
import com.crosspaste.sync.NearbyDeviceManager
import com.crosspaste.ui.base.CustomSwitch
import com.crosspaste.ui.base.link
import com.crosspaste.ui.base.wifi
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
    val appSize = koinInject<AppSize>()
    val configManager = koinInject<DesktopConfigManager>()
    val nearbyDeviceManager = koinInject<NearbyDeviceManager>()
    val networkInterfaceService = koinInject<NetworkInterfaceService>()
    val copywriter = koinInject<GlobalCopywriter>()
    val jsonUtils = getJsonUtils()

    var port: String? by remember { mutableStateOf(null) }

    val config by configManager.config.collectAsState()

    var networkInterfaces by remember { mutableStateOf(listOf<NetworkInterfaceInfo>()) }

    var useNetworkInterfaces by remember { mutableStateOf(listOf<String>()) }

    LaunchedEffect(config.useNetworkInterfaces) {
        useNetworkInterfaces = jsonUtils.JSON.decodeFromString(config.useNetworkInterfaces)
    }

    LaunchedEffect(Unit) {
        networkInterfaces = networkInterfaceService.getAllNetworkInterfaceInfo()
        val currentPort = config.port
        port = if (currentPort == 0) "N/A" else currentPort.toString()
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
                            useNetworkInterfaces = listOf(it.name)
                            val newUseNetworkInterfaces =
                                jsonUtils.JSON.encodeToString(useNetworkInterfaces)
                            configManager.updateConfig("useNetworkInterfaces", newUseNetworkInterfaces)
                        }
                    } else {
                        configManager.updateConfig("useNetworkInterfaces", "[]")
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
                useNetworkInterfaces =
                    if (isChecked) {
                        useNetworkInterfaces + currentInterface
                    } else {
                        useNetworkInterfaces - currentInterface
                    }
                val newUseNetworkInterfaces = jsonUtils.JSON.encodeToString(useNetworkInterfaces)
                configManager.updateConfig("useNetworkInterfaces", newUseNetworkInterfaces)
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
                            .height(appSize.deviceHeight)
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
                            .heightIn(max = appSize.deviceHeight * 3),
                    verticalArrangement = Arrangement.Top,
                ) {
                    itemsIndexed(blacklist) { index, syncInfo ->
                        val currentIndex by rememberUpdatedState(index)
                        val currentSyncInfo by rememberUpdatedState(syncInfo)

                        BlackListDeviceView(currentSyncInfo) {
                            val blackSyncInfos: List<SyncInfo> =
                                jsonUtils.JSON
                                    .decodeFromString<List<SyncInfo>>(
                                        config.blacklist,
                                    ).filter { it.appInfo.appInstanceId != currentSyncInfo.appInfo.appInstanceId }

                            val newBlackList = jsonUtils.JSON.encodeToString(blackSyncInfos)
                            configManager.updateConfig("blacklist", newBlackList)
                            blacklist.remove(currentSyncInfo)
                            nearbyDeviceManager.refreshSyncManager()
                        }

                        if (currentIndex != blacklist.size - 1) {
                            HorizontalDivider(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}
