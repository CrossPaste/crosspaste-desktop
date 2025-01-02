package com.crosspaste.ui.devices

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crosspaste.app.AppInfo
import com.crosspaste.app.AppWindowManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.net.VersionRelation
import com.crosspaste.realm.sync.SyncRuntimeInfo
import com.crosspaste.sync.SyncManager
import com.crosspaste.ui.base.CustomSwitch
import com.crosspaste.ui.base.alertCircle
import com.crosspaste.ui.base.measureTextWidth
import com.crosspaste.ui.theme.CrossPasteTheme.unmatchedColor
import kotlinx.coroutines.runBlocking
import org.koin.compose.koinInject

@Composable
fun DeviceDetailContentView() {
    val appInfo = koinInject<AppInfo>()
    val appWindowManager = koinInject<AppWindowManager>()
    val copywriter = koinInject<GlobalCopywriter>()
    val syncManager = koinInject<SyncManager>()

    val screen by appWindowManager.screenContext.collectAsState()

    var syncRuntimeInfo by remember(screen) {
        mutableStateOf(screen.context as SyncRuntimeInfo)
    }

    var syncHandler by remember(screen) {
        mutableStateOf(syncManager.getSyncHandler(syncRuntimeInfo.appInstanceId))
    }

    var versionRelation by remember(screen) {
        mutableStateOf(syncHandler?.versionRelation)
    }

    Column {
        DeviceConnectView(syncRuntimeInfo, false) { }

        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .padding(16.dp),
        ) {
            if (versionRelation != null && versionRelation != VersionRelation.EQUAL_TO) {
                Column(
                    modifier =
                        Modifier.wrapContentSize()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.errorContainer),
                ) {
                    Row(
                        modifier =
                            Modifier.wrapContentSize()
                                .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = alertCircle(),
                            contentDescription = "Warning",
                            tint = unmatchedColor(MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text =
                                "${copywriter.getText("current_software_version")}: ${appInfo.appVersion}\n" +
                                    "${copywriter.getText("connected_software_version")}: ${syncRuntimeInfo.appVersion}\n" +
                                    copywriter.getText("incompatible_info"),
                            color = unmatchedColor(MaterialTheme.colorScheme.errorContainer),
                            style =
                                TextStyle(
                                    fontWeight = FontWeight.Light,
                                    lineHeight = 20.sp,
                                ),
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // Header
            Text(
                modifier =
                    Modifier.wrapContentSize()
                        .padding(start = 15.dp, bottom = 5.dp),
                text = copywriter.getText("sync_control"),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.SansSerif,
                fontSize = 12.sp,
            )
            Column(
                modifier =
                    Modifier.wrapContentSize()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                Row(
                    modifier =
                        Modifier.wrapContentSize()
                            .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${copywriter.getText("allow_send_to")} ${syncRuntimeInfo.getDeviceDisplayName()}",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = TextStyle(fontWeight = FontWeight.Light),
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f),
                    )
                    CustomSwitch(
                        modifier =
                            Modifier.align(Alignment.CenterVertically)
                                .width(32.dp)
                                .height(20.dp),
                        checked = syncRuntimeInfo.allowSend,
                        onCheckedChange = {
                            runBlocking {
                                syncManager.getSyncHandlers()[syncRuntimeInfo.appInstanceId]
                                    ?.update {
                                        this.allowSend = it
                                    }?.let {
                                        syncRuntimeInfo = it
                                    }
                            }
                        },
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(start = 15.dp))

                Row(
                    modifier =
                        Modifier.wrapContentSize()
                            .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${copywriter.getText("allow_receive_from")} ${syncRuntimeInfo.getDeviceDisplayName()}",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = TextStyle(fontWeight = FontWeight.Light),
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f),
                    )
                    CustomSwitch(
                        modifier =
                            Modifier.align(Alignment.CenterVertically)
                                .width(32.dp)
                                .height(20.dp),
                        checked = syncRuntimeInfo.allowReceive,
                        onCheckedChange = {
                            runBlocking {
                                syncManager.getSyncHandlers()[syncRuntimeInfo.appInstanceId]
                                    ?.update {
                                        this.allowReceive = it
                                    }?.let {
                                        syncRuntimeInfo = it
                                    }
                            }
                        },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            var maxWidth by remember { mutableStateOf(0.dp) }

            val properties =
                remember(syncRuntimeInfo) {
                    arrayOf(
                        Pair("app_version", syncRuntimeInfo.appVersion),
                        Pair("user_name", syncRuntimeInfo.userName),
                        Pair("device_id", syncRuntimeInfo.deviceId),
                        Pair("arch", syncRuntimeInfo.platformArch),
                        Pair("connect_host", syncRuntimeInfo.connectHostAddress ?: ""),
                        Pair("port", syncRuntimeInfo.port.toString()),
                    )
                }

            val textStyle =
                TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                )

            for (property in properties) {
                maxWidth =
                    maxOf(maxWidth, measureTextWidth(copywriter.getText(property.first), textStyle))
            }

            Text(
                modifier =
                    Modifier.wrapContentSize()
                        .padding(start = 15.dp, bottom = 5.dp),
                text = copywriter.getText("base_info"),
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = FontFamily.SansSerif,
                style = MaterialTheme.typography.headlineSmall,
                fontSize = 12.sp,
            )

            Column(
                modifier =
                    Modifier.wrapContentSize()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                properties.forEachIndexed { index, pair ->
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            modifier = Modifier.width(maxWidth + 16.dp),
                            text = copywriter.getText(pair.first),
                            style = TextStyle(fontWeight = FontWeight.Light),
                            fontFamily = FontFamily.SansSerif,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                        )
                        Text(
                            text = pair.second,
                            style = TextStyle(fontWeight = FontWeight.Light),
                            fontFamily = FontFamily.SansSerif,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp,
                        )
                    }
                    if (index < properties.size - 1) {
                        HorizontalDivider(modifier = Modifier.padding(start = 15.dp))
                    }
                }
            }
        }
    }
}
