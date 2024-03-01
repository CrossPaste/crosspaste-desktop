package com.clipevery.ui.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
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

    DeviceBarView(syncRuntimeInfo, currentPageViewContext, false) { }

    Column(
        modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colors.surface)
            .padding(16.dp)
    ) {
        // Header
        Text( modifier = Modifier.wrapContentSize()
            .padding(start = 15.dp, bottom = 5.dp),
            text = copywriter.getText("Sync_Control"),
            color = MaterialTheme.colors.onBackground,
            style = MaterialTheme.typography.h6,
            fontFamily = FontFamily.SansSerif,
            fontSize = 12.sp)
        Column(modifier = Modifier.wrapContentSize()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colors.background)
        ) {
            Row(
                modifier = Modifier.wrapContentSize().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = copywriter.getText("Allow_Send_to_this_device"),
                    color = MaterialTheme.colors.onBackground,
                    style = TextStyle(fontWeight = FontWeight.Light),
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 17.sp,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    modifier = Modifier.align(Alignment.CenterVertically).padding(0.dp),
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

            Divider(modifier = Modifier.padding(start = 15.dp), color = Color.Gray)

            Row(
                modifier = Modifier.wrapContentSize().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = copywriter.getText("Allow_Receive_from_this_device"),
                    color = MaterialTheme.colors.onBackground,
                    style = TextStyle(fontWeight = FontWeight.Light),
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 17.sp,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    modifier = Modifier.align(Alignment.CenterVertically).padding(0.dp),
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
        }


        Spacer(Modifier.height(16.dp))

        var maxWidth by remember { mutableStateOf(0.dp) }

        val properties = remember(syncRuntimeInfo) {
            arrayOf(
                Pair("App_Version", syncRuntimeInfo.appVersion),
                Pair("User_Name", syncRuntimeInfo.userName),
                Pair("Device_ID", syncRuntimeInfo.deviceId),
                Pair("Arch", syncRuntimeInfo.platformArch),
                Pair("Connect_Host", syncRuntimeInfo.connectHostAddress ?: ""),
                Pair("Port", syncRuntimeInfo.port.toString())
            )
        }

        val textStyle = TextStyle(fontSize = 16.sp,
            fontWeight = FontWeight.Normal)

        for (property in properties) {
            maxWidth = maxOf(maxWidth, measureTextWidth(copywriter.getText(property.first), textStyle))
        }

        Text( modifier = Modifier.wrapContentSize()
            .padding(start = 15.dp, bottom = 5.dp),
            text = copywriter.getText("Base_Info"),
            color = MaterialTheme.colors.onBackground,
            fontFamily = FontFamily.SansSerif,
            style = MaterialTheme.typography.h6,
            fontSize = 12.sp)

        Column(modifier = Modifier.wrapContentSize()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colors.background)
        ) {

            properties.forEachIndexed { index, pair ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        modifier = Modifier.width(maxWidth + 16.dp),
                        text = copywriter.getText(pair.first),
                        style = TextStyle(fontWeight = FontWeight.Light),
                        fontFamily = FontFamily.SansSerif,
                        color = MaterialTheme.colors.onBackground,
                        fontSize = 17.sp
                    )
                    Text(text = pair.second,
                        style = TextStyle(fontWeight = FontWeight.Light),
                        fontFamily = FontFamily.SansSerif,
                        color = MaterialTheme.colors.onBackground,
                        fontSize = 16.sp
                    )
                }
                if (index < properties.size - 1) {
                    Divider(modifier = Modifier.padding(start = 15.dp), color = Color.Gray)
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {  }
}

@Composable
fun measureTextWidth(text: String, style: TextStyle): Dp {
    val textMeasurer = rememberTextMeasurer()
    val widthInPixels = textMeasurer.measure(text, style).size.width
    return with(LocalDensity.current) { widthInPixels.toDp() }
}