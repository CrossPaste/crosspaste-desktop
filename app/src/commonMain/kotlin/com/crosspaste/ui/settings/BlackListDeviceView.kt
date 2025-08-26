package com.crosspaste.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.sync.NearbyDeviceManager
import com.crosspaste.ui.base.PasteIconButton
import com.crosspaste.ui.base.remove
import com.crosspaste.ui.devices.SyncDeviceView
import com.crosspaste.ui.devices.SyncScope
import com.crosspaste.ui.theme.AppUISize.large2X
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.utils.getJsonUtils
import org.koin.compose.koinInject

@Composable
fun SyncScope.BlackListDeviceView(remove: (SyncInfo) -> Unit) {
    val configManager = koinInject<CommonConfigManager>()
    val nearbyDeviceManager = koinInject<NearbyDeviceManager>()
    val jsonUtils = getJsonUtils()

    val config by configManager.config.collectAsState()

    SyncDeviceView {
        PasteIconButton(
            size = large2X,
            onClick = {
                val blackSyncInfos: List<SyncInfo> =
                    jsonUtils.JSON
                        .decodeFromString<List<SyncInfo>>(
                            config.blacklist,
                        ).filter { it.appInfo.appInstanceId != syncInfo.appInfo.appInstanceId }

                val newBlackList = jsonUtils.JSON.encodeToString(blackSyncInfos)
                configManager.updateConfig("blacklist", newBlackList)
                remove(syncInfo)
                nearbyDeviceManager.refreshSyncManager()
            },
            modifier =
                Modifier
                    .background(Color.Transparent, CircleShape),
        ) {
            Icon(
                painter = remove(),
                contentDescription = "remove blacklist",
                tint = Color.Red,
                modifier = Modifier.size(large2X),
            )
        }
        Spacer(modifier = Modifier.width(medium))
    }
}
