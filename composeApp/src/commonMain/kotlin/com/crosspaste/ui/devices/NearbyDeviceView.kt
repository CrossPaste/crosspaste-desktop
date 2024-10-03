package com.crosspaste.ui.devices

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crosspaste.config.ConfigManager
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.realm.sync.SyncRuntimeInfoRealm
import com.crosspaste.realm.sync.createSyncRuntimeInfo
import com.crosspaste.sync.DeviceManager
import com.crosspaste.ui.connectedColor
import com.crosspaste.ui.disconnectedColor
import com.crosspaste.utils.getJsonUtils
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import org.koin.compose.koinInject

@Composable
fun NearbyDeviceView(syncInfo: SyncInfo) {
    val copywriter = koinInject<GlobalCopywriter>()
    val deviceManager = koinInject<DeviceManager>()
    val syncRuntimeInfoRealm = koinInject<SyncRuntimeInfoRealm>()
    val configManager = koinInject<ConfigManager>()
    val jsonUtils = getJsonUtils()
    val scope = rememberCoroutineScope()
    SyncDeviceView(syncInfo = syncInfo) {
        Button(
            modifier = Modifier.height(28.dp),
            onClick = {
                val newSyncRuntimeInfo = createSyncRuntimeInfo(syncInfo)
                syncRuntimeInfoRealm.insertOrUpdate(newSyncRuntimeInfo)
            },
            shape = RoundedCornerShape(4.dp),
            border = BorderStroke(1.dp, connectedColor()),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.background),
            elevation =
                ButtonDefaults.elevatedButtonElevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                    hoveredElevation = 0.dp,
                    focusedElevation = 0.dp,
                ),
        ) {
            Text(
                text = copywriter.getText("add"),
                color = connectedColor(),
                style =
                    TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Light,
                        fontSize = 14.sp,
                    ),
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Button(
            modifier = Modifier.height(28.dp),
            onClick = {
                val blackSyncInfos: MutableList<SyncInfo> = jsonUtils.JSON.decodeFromString(configManager.config.blacklist)
                for (blackSyncInfo in blackSyncInfos) {
                    if (blackSyncInfo.appInfo.appInstanceId == syncInfo.appInfo.appInstanceId) {
                        return@Button
                    }
                }
                blackSyncInfos.add(syncInfo)
                val newBlackList = jsonUtils.JSON.encodeToString(blackSyncInfos)
                configManager.updateConfig("blacklist", newBlackList)
                scope.launch {
                    deviceManager.refresh()
                }
            },
            shape = RoundedCornerShape(4.dp),
            border = BorderStroke(1.dp, disconnectedColor()),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.background),
            elevation =
                ButtonDefaults.elevatedButtonElevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                    hoveredElevation = 0.dp,
                    focusedElevation = 0.dp,
                ),
        ) {
            Text(
                text = copywriter.getText("block"),
                color = disconnectedColor(),
                style =
                    TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Light,
                        fontSize = 14.sp,
                    ),
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
    }
}
