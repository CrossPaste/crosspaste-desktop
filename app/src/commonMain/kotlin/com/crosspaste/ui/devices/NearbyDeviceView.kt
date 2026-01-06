package com.crosspaste.ui.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import com.crosspaste.app.AppControl
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.sync.SyncManager
import com.crosspaste.ui.NavigationManager
import com.crosspaste.ui.NearbyDeviceDetail
import com.crosspaste.ui.base.GeneralIconButton
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.utils.getJsonUtils
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScope.NearbyDeviceView() {
    val appControl = koinInject<AppControl>()
    val configManager = koinInject<CommonConfigManager>()
    val navigationManager = koinInject<NavigationManager>()
    val syncManager = koinInject<SyncManager>()

    val jsonUtils = getJsonUtils()
    val config by configManager.config.collectAsState()

    DeviceRowContent(
        style = nearbyDeviceStyle,
        onClick = {
            navigationManager.navigate(NearbyDeviceDetail(syncInfo.appInfo.appInstanceId))
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(tiny),
            ) {
                GeneralIconButton(
                    imageVector = Icons.Default.Block,
                    desc = "block",
                    colors =
                        IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                ) {
                    val blackSyncInfos: MutableList<SyncInfo> =
                        jsonUtils.JSON.decodeFromString(
                            config.blacklist,
                        )
                    for (blackSyncInfo in blackSyncInfos) {
                        if (blackSyncInfo.appInfo.appInstanceId == syncInfo.appInfo.appInstanceId) {
                            return@GeneralIconButton
                        }
                    }
                    blackSyncInfos.add(syncInfo)
                    val newBlackList = jsonUtils.JSON.encodeToString(blackSyncInfos)
                    configManager.updateConfig("blacklist", newBlackList)
                }

                GeneralIconButton(
                    imageVector = Icons.Default.Link,
                    desc = "pair",
                    colors =
                        IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                ) {
                    if (appControl.isDeviceConnectionEnabled(syncManager.getSyncHandlers().size + 1)) {
                        syncManager.updateSyncInfo(syncInfo)
                    }
                }
            }
        },
    )
}
