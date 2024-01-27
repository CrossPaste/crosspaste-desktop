package com.clipevery.ui.devices

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipevery.LocalKoinApplication
import com.clipevery.dao.sync.SyncRuntimeInfo
import com.clipevery.dao.sync.SyncRuntimeInfoDao
import com.clipevery.i18n.GlobalCopywriter
import com.clipevery.ui.PageViewContext
import com.clipevery.ui.WindowDecoration

@Composable
fun DeviceDetailView(currentPageViewContext: MutableState<PageViewContext>) {
    WindowDecoration(currentPageViewContext, "Device_Detail")
    DeviceDetailContentView(currentPageViewContext)
}

@Composable
fun DeviceDetailContentView(currentPageViewContext: MutableState<PageViewContext>) {
    val current = LocalKoinApplication.current
    val syncRuntimeInfoDao = current.koin.get<SyncRuntimeInfoDao>()
    val copywriter = current.koin.get<GlobalCopywriter>()

    var syncRuntimeInfo by remember { mutableStateOf(currentPageViewContext.value.context as SyncRuntimeInfo) }

    DeviceBarView(syncRuntimeInfo, currentPageViewContext, false)

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        // Header
        Text(copywriter.getText("Sync_Control"),
            color = MaterialTheme.colors.onBackground,
            style = MaterialTheme.typography.h6,
            fontSize = 12.sp)

        Row(
            modifier = Modifier.wrapContentWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = copywriter.getText("Allow_Send_to_this_device"),
                style = TextStyle(
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colors.onBackground,
                    fontSize = 17.sp
                ),
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = syncRuntimeInfo.allowSend,
                onCheckedChange = { it ->
                    syncRuntimeInfoDao.updateAllowSend(syncRuntimeInfo, it)?.let {
                        syncRuntimeInfo = it
                    }
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Green,
                    uncheckedThumbColor = Color.Gray
                )
            )
        }

        Row(
            modifier = Modifier.wrapContentWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = copywriter.getText("Allow_Receive_from_this_device"),
                style = TextStyle(
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colors.onBackground,
                    fontSize = 17.sp
                ),
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = syncRuntimeInfo.allowReceive,
                onCheckedChange = { it ->
                    syncRuntimeInfoDao.updateAllowReceive(syncRuntimeInfo, it)?.let {
                        syncRuntimeInfo = it
                    }
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Green,
                    uncheckedThumbColor = Color.Gray
                )
            )
        }

        Spacer(Modifier.height(16.dp))

        Divider(color = Color.Gray)

        Spacer(Modifier.height(16.dp))

        SettingItem(title = copywriter.getText("App_Version"), value = syncRuntimeInfo.appVersion)
        SettingItem(title = copywriter.getText("User_Name"), value = syncRuntimeInfo.userName)
        SettingItem(title = copywriter.getText("Device_ID"), value = syncRuntimeInfo.deviceId)
        SettingItem(title = copywriter.getText("Arch"), value = syncRuntimeInfo.platformArch)
        SettingItem(title = copywriter.getText("Host_Name"), value = syncRuntimeInfo.connectHostAddress ?: "")
        SettingItem(title = copywriter.getText("Port"), value = syncRuntimeInfo.port.toString())
    }

    Column(modifier = Modifier.fillMaxSize()) {  }
}

@Composable
fun SettingItem(title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = MaterialTheme.colors.onBackground, modifier = Modifier.weight(1f))
        Text(value, color = MaterialTheme.colors.onBackground)
    }
}