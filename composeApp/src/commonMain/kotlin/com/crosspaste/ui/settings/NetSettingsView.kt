package com.crosspaste.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crosspaste.LocalKoinApplication
import com.crosspaste.config.ConfigManager
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.sync.DeviceManager
import com.crosspaste.ui.base.CustomSwitch
import com.crosspaste.ui.base.PasteIconButton
import com.crosspaste.ui.base.link
import com.crosspaste.ui.base.network
import com.crosspaste.ui.base.remove
import com.crosspaste.ui.base.wifi
import com.crosspaste.ui.devices.SyncDeviceView
import com.crosspaste.utils.getJsonUtils
import com.crosspaste.utils.getNetUtils
import kotlinx.serialization.encodeToString

@Composable
fun NetSettingsView() {
    val current = LocalKoinApplication.current
    val configManager = current.koin.get<ConfigManager>()
    val deviceManager = current.koin.get<DeviceManager>()
    val copywriter = current.koin.get<GlobalCopywriter>()
    val netUtils = getNetUtils()
    val jsonUtils = getJsonUtils()

    var ip: String? by remember { mutableStateOf(null) }
    var port: String? by remember { mutableStateOf(null) }

    LaunchedEffect(Unit) {
        ip = netUtils.getEn0IPAddress() ?: "N/A"
        val currentPort = configManager.config.port
        port = if (currentPort == 0) "N/A" else currentPort.toString()
    }

    Text(
        modifier =
            Modifier.wrapContentSize()
                .padding(start = 32.dp, top = 5.dp, bottom = 5.dp),
        text = copywriter.getText("network_info"),
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
        SettingItemView(
            painter = network(),
            text = "ip_address",
            tint = MaterialTheme.colors.onBackground,
        ) {
            if (ip != null) {
                settingsText(ip!!)
            } else {
                CircularProgressIndicator(modifier = Modifier.size(25.dp))
            }
        }

        Divider(modifier = Modifier.padding(start = 35.dp))

        SettingItemView(
            painter = link(),
            text = "port",
            tint = MaterialTheme.colors.onBackground,
        ) {
            if (port != null) {
                settingsText(port!!)
            } else {
                CircularProgressIndicator(modifier = Modifier.size(25.dp))
            }
        }
    }

    Text(
        modifier =
            Modifier.wrapContentSize()
                .padding(start = 32.dp, top = 5.dp, bottom = 5.dp),
        text = copywriter.getText("service_discovery"),
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
        SettingItemView(
            painter = wifi(),
            text = "allow_discovery_by_new_devices",
            tint = MaterialTheme.colors.onBackground,
        ) {
            var isAllowDiscovery by remember { mutableStateOf(configManager.config.isAllowDiscovery) }

            CustomSwitch(
                modifier =
                    Modifier.width(32.dp)
                        .height(20.dp),
                checked = isAllowDiscovery,
                onCheckedChange = { newIsAllowDiscovery ->
                    configManager.updateConfig("isAllowDiscovery", newIsAllowDiscovery)
                    isAllowDiscovery = configManager.config.isAllowDiscovery
                },
            )
        }
    }

    Text(
        modifier =
            Modifier.wrapContentSize()
                .padding(start = 32.dp, top = 5.dp, bottom = 5.dp),
        text = copywriter.getText("blacklist"),
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
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .height(70.dp)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val blacklist =
                remember(configManager.config) {
                    val list: List<SyncInfo> = jsonUtils.JSON.decodeFromString(configManager.config.blacklist)
                    mutableStateListOf(*list.toTypedArray())
                }

            if (blacklist.isEmpty()) {
                settingsText(copywriter.getText("empty"))
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    for ((index, syncInfo) in blacklist.withIndex()) {
                        BlackListDeviceView(syncInfo) {
                            val blackSyncInfos: List<SyncInfo> =
                                jsonUtils.JSON.decodeFromString<List<SyncInfo>>(
                                    configManager.config.blacklist,
                                ).filter { it.appInfo.appInstanceId != syncInfo.appInfo.appInstanceId }

                            val newBlackList = jsonUtils.JSON.encodeToString(blackSyncInfos)
                            configManager.updateConfig("blacklist", newBlackList)
                            blacklist.remove(syncInfo)
                            deviceManager.refresh()
                        }
                        if (index != blacklist.size - 1) {
                            Divider(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BlackListDeviceView(
    syncInfo: SyncInfo,
    clickable: () -> Unit,
) {
    SyncDeviceView(syncInfo = syncInfo) {
        PasteIconButton(
            size = 20.dp,
            onClick = {
                clickable()
            },
            modifier =
                Modifier
                    .background(Color.Transparent, CircleShape),
        ) {
            Icon(
                painter = remove(),
                contentDescription = "remove blacklist",
                tint = Color.Red,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
    }
}
