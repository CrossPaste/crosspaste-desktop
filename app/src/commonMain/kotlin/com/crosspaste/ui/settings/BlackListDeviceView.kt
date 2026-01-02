package com.crosspaste.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.ui.base.GeneralIconButton
import com.crosspaste.ui.devices.SyncDeviceView
import com.crosspaste.ui.devices.SyncScope
import com.crosspaste.utils.getJsonUtils
import org.koin.compose.koinInject

@Composable
fun SyncScope.BlackListDeviceView() {
    val configManager = koinInject<CommonConfigManager>()
    val jsonUtils = getJsonUtils()

    val config by configManager.config.collectAsState()

    SyncDeviceView {
        GeneralIconButton(
            imageVector = Icons.Default.DoNotDisturbOn,
            desc = "remove_blacklist",
            colors =
                IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
        ) {
            val blackSyncInfos: List<SyncInfo> =
                jsonUtils.JSON
                    .decodeFromString<List<SyncInfo>>(
                        config.blacklist,
                    ).filter { it.appInfo.appInstanceId != syncInfo.appInfo.appInstanceId }

            val newBlackList = jsonUtils.JSON.encodeToString(blackSyncInfos)
            configManager.updateConfig("blacklist", newBlackList)
        }
    }
}
