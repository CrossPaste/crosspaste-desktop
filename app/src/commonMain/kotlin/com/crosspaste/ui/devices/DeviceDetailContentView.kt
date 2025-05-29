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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import com.crosspaste.app.AppControl
import com.crosspaste.app.AppInfo
import com.crosspaste.app.AppWindowManager
import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.net.VersionRelation
import com.crosspaste.sync.SyncManager
import com.crosspaste.ui.base.CustomSwitch
import com.crosspaste.ui.base.alertCircle
import com.crosspaste.ui.base.measureTextWidth
import com.crosspaste.ui.theme.AppUISize.large2X
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.small
import com.crosspaste.ui.theme.AppUISize.small2X
import com.crosspaste.ui.theme.AppUISize.tiny3X
import com.crosspaste.ui.theme.AppUISize.tinyRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.zero
import kotlinx.coroutines.runBlocking
import org.koin.compose.koinInject

@Composable
fun DeviceDetailContentView() {
    val appControl = koinInject<AppControl>()
    val appInfo = koinInject<AppInfo>()
    val appWindowManager = koinInject<AppWindowManager>()
    val copywriter = koinInject<GlobalCopywriter>()
    val deviceViewProvider = koinInject<DeviceViewProvider>()
    val syncManager = koinInject<SyncManager>()

    val screen by appWindowManager.screenContext.collectAsState()

    var syncRuntimeInfo by remember { mutableStateOf(screen.context as SyncRuntimeInfo) }

    var syncHandler by remember {
        mutableStateOf(syncManager.getSyncHandler(syncRuntimeInfo.appInstanceId))
    }

    var versionRelation by remember {
        mutableStateOf(syncHandler?.versionRelation)
    }

    LaunchedEffect(screen) {
        syncRuntimeInfo = screen.context as SyncRuntimeInfo
        syncHandler = syncManager.getSyncHandler(syncRuntimeInfo.appInstanceId)
        versionRelation = syncHandler?.versionRelation
    }

    Column(
        modifier =
            Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        deviceViewProvider.DeviceConnectView(syncRuntimeInfo, false) { }

        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(horizontal = medium)
                    .padding(bottom = medium).clip(tinyRoundedCornerShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .verticalScroll(rememberScrollState())
                    .padding(medium),
        ) {
            if (versionRelation != null && versionRelation != VersionRelation.EQUAL_TO) {
                Column(
                    modifier =
                        Modifier.wrapContentSize()
                            .clip(tinyRoundedCornerShape)
                            .background(MaterialTheme.colorScheme.errorContainer),
                ) {
                    Row(
                        modifier =
                            Modifier.wrapContentSize()
                                .padding(small2X),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = alertCircle(),
                            contentDescription = "Warning",
                            tint =
                                MaterialTheme.colorScheme.contentColorFor(
                                    MaterialTheme.colorScheme.errorContainer,
                                ),
                            modifier = Modifier.size(large2X),
                        )
                        Spacer(modifier = Modifier.width(medium))
                        Text(
                            text =
                                "${copywriter.getText("current_software_version")}: ${appInfo.appVersion}\n" +
                                    "${copywriter.getText("connected_software_version")}: ${syncRuntimeInfo.appVersion}\n" +
                                    copywriter.getText("incompatible_info"),
                            color =
                                MaterialTheme.colorScheme.contentColorFor(
                                    MaterialTheme.colorScheme.errorContainer,
                                ),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(medium))
                }
            }

            // Header
            Text(
                modifier =
                    Modifier.wrapContentSize()
                        .padding(start = medium, bottom = tiny3X),
                text = copywriter.getText("sync_control"),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
            )
            Column(
                modifier =
                    Modifier.wrapContentSize()
                        .clip(tinyRoundedCornerShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                Row(
                    modifier =
                        Modifier.wrapContentSize()
                            .padding(small2X),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${copywriter.getText("allow_send_to")} ${syncRuntimeInfo.getDeviceDisplayName()}",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Light),
                        modifier = Modifier.weight(1f),
                    )
                    CustomSwitch(
                        modifier =
                            Modifier.align(Alignment.CenterVertically)
                                .width(medium * 2)
                                .height(large2X),
                        checked = !appControl.isSyncControlEnabled(false) || syncRuntimeInfo.allowSend,
                        onCheckedChange = { allowSend ->
                            runBlocking {
                                if (appControl.isSyncControlEnabled()) {
                                    syncManager.getSyncHandlers()[syncRuntimeInfo.appInstanceId]
                                        ?.updateSyncRuntimeInfo { syncRuntimeInfo ->
                                            syncRuntimeInfo.copy(allowSend = allowSend)
                                        }?.let {
                                            syncRuntimeInfo = it
                                        }
                                }
                            }
                        },
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(start = small))

                Row(
                    modifier =
                        Modifier.wrapContentSize()
                            .padding(small2X),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${copywriter.getText("allow_receive_from")} ${syncRuntimeInfo.getDeviceDisplayName()}",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Light),
                        modifier = Modifier.weight(1f),
                    )
                    CustomSwitch(
                        modifier =
                            Modifier.align(Alignment.CenterVertically)
                                .width(medium * 2)
                                .height(large2X),
                        checked = !appControl.isSyncControlEnabled(false) || syncRuntimeInfo.allowReceive,
                        onCheckedChange = { allowReceive ->
                            runBlocking {
                                if (appControl.isSyncControlEnabled()) {
                                    syncManager.getSyncHandlers()[syncRuntimeInfo.appInstanceId]
                                        ?.updateSyncRuntimeInfo { syncRuntimeInfo ->
                                            syncRuntimeInfo.copy(allowReceive = allowReceive)
                                        }?.let {
                                            syncRuntimeInfo = it
                                        }
                                }
                            }
                        },
                    )
                }
            }

            Spacer(Modifier.height(medium))

            var maxWidth by remember { mutableStateOf(zero) }

            val properties =
                remember(syncRuntimeInfo) {
                    arrayOf(
                        Pair("app_version", syncRuntimeInfo.appVersion),
                        Pair("user_name", syncRuntimeInfo.userName),
                        Pair("device_id", syncRuntimeInfo.deviceId),
                        Pair("arch", syncRuntimeInfo.platform.arch),
                        Pair("connect_host", syncRuntimeInfo.connectHostAddress ?: ""),
                        Pair("port", syncRuntimeInfo.port.toString()),
                    )
                }

            val textStyle =
                MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                )

            for (property in properties) {
                maxWidth =
                    maxOf(maxWidth, measureTextWidth(copywriter.getText(property.first), textStyle))
            }

            Text(
                modifier =
                    Modifier.wrapContentSize()
                        .padding(start = medium, bottom = tiny3X),
                text = copywriter.getText("base_info"),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
            )

            Column(
                modifier =
                    Modifier.wrapContentSize()
                        .clip(tinyRoundedCornerShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                properties.forEachIndexed { index, pair ->
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .padding(small2X),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            modifier = Modifier.width(maxWidth + medium),
                            text = copywriter.getText(pair.first),
                            style = textStyle,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = pair.second,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    if (index < properties.size - 1) {
                        HorizontalDivider(modifier = Modifier.padding(start = small))
                    }
                }
            }
        }
    }
}
