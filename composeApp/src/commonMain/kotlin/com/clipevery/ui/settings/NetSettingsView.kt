package com.clipevery.ui.settings

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
import com.clipevery.LocalKoinApplication
import com.clipevery.config.ConfigManager
import com.clipevery.dto.sync.SyncInfo
import com.clipevery.i18n.GlobalCopywriter
import com.clipevery.sync.DeviceManager
import com.clipevery.ui.base.ClipIconButton
import com.clipevery.ui.base.CustomSwitch
import com.clipevery.ui.base.link
import com.clipevery.ui.base.network
import com.clipevery.ui.base.remove
import com.clipevery.ui.base.towerBroadcast
import com.clipevery.ui.devices.SyncDeviceView
import com.clipevery.utils.getJsonUtils
import com.clipevery.utils.getNetUtils
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
        text = copywriter.getText("Network_Info"),
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
                    .height(40.dp)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                modifier = Modifier.size(15.dp),
                painter = network(),
                contentDescription = "IP",
                tint = MaterialTheme.colors.onBackground,
            )

            Spacer(modifier = Modifier.width(8.dp))

            settingsText(copywriter.getText("IP_Address"))

            Spacer(modifier = Modifier.weight(1f))

            if (ip != null) {
                settingsText(ip!!)
            } else {
                CircularProgressIndicator(modifier = Modifier.size(25.dp))
            }
        }

        Divider(modifier = Modifier.padding(start = 35.dp))

        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .height(40.dp)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                modifier = Modifier.size(15.dp),
                painter = link(),
                contentDescription = "Port",
                tint = MaterialTheme.colors.onBackground,
            )

            Spacer(modifier = Modifier.width(8.dp))

            settingsText(copywriter.getText("Port"))

            Spacer(modifier = Modifier.weight(1f))

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
        text = copywriter.getText("Service_Discovery"),
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
                    .height(40.dp)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                modifier = Modifier.size(15.dp),
                painter = towerBroadcast(),
                contentDescription = "Allow discovery by new devices",
                tint = MaterialTheme.colors.onBackground,
            )

            Spacer(modifier = Modifier.width(8.dp))

            settingsText(copywriter.getText("Allow_discovery_by_new_devices"))

            Spacer(modifier = Modifier.weight(1f))

            var isAllowDiscovery by remember { mutableStateOf(configManager.config.isAllowDiscovery) }

            CustomSwitch(
                modifier =
                    Modifier.width(32.dp)
                        .height(20.dp),
                checked = isAllowDiscovery,
                onCheckedChange = { newIsAllowDiscovery ->
                    configManager.updateConfig { it.copy(isAllowDiscovery = newIsAllowDiscovery) }
                    isAllowDiscovery = configManager.config.isAllowDiscovery
                },
            )
        }
    }

    Text(
        modifier =
            Modifier.wrapContentSize()
                .padding(start = 32.dp, top = 5.dp, bottom = 5.dp),
        text = copywriter.getText("Blacklist"),
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
                settingsText(copywriter.getText("Empty"))
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    for ((index, syncInfo) in blacklist.withIndex()) {
                        BlackListDeviceView(syncInfo) {
                            val blackSyncInfos: List<SyncInfo> =
                                jsonUtils.JSON.decodeFromString<List<SyncInfo>>(
                                    configManager.config.blacklist,
                                ).filter { it.appInfo.appInstanceId != syncInfo.appInfo.appInstanceId }

                            val newBlackList = jsonUtils.JSON.encodeToString(blackSyncInfos)
                            configManager.updateConfig { it.copy(blacklist = newBlackList) }
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
        ClipIconButton(
            radius = 18.dp,
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
                modifier = Modifier.size(30.dp),
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
    }
}
