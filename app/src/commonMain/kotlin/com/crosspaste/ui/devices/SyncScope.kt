package com.crosspaste.ui.devices

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.crosspaste.app.AppControl
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.sync.NearbyDeviceManager
import com.crosspaste.sync.SyncManager
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUIFont.buttonTextStyle
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny3XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.tiny5X
import com.crosspaste.ui.theme.AppUISize.xxLarge
import com.crosspaste.ui.theme.AppUISize.zero
import com.crosspaste.ui.theme.AppUISize.zeroButtonElevation
import com.crosspaste.ui.theme.CrossPasteTheme.connectedColor
import com.crosspaste.ui.theme.CrossPasteTheme.disconnectedColor
import com.crosspaste.utils.getJsonUtils
import org.koin.compose.koinInject

interface SyncScope : PlatformScope {

    val syncInfo: SyncInfo

    @Composable
    fun Modifier.hoverModifier(): Modifier

    @Composable
    fun NearbyDeviceView() {
        val appControl = koinInject<AppControl>()
        val configManager = koinInject<CommonConfigManager>()
        val copywriter = koinInject<GlobalCopywriter>()
        val nearbyDeviceManager = koinInject<NearbyDeviceManager>()
        val syncManager = koinInject<SyncManager>()

        val jsonUtils = getJsonUtils()

        val config by configManager.config.collectAsState()

        HoverableDeviceBarView {
            Row(
                modifier =
                    Modifier
                        .wrapContentSize()
                        .padding(start = medium),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                Button(
                    modifier = Modifier.height(xxLarge),
                    onClick = {
                        if (appControl.isDeviceConnectionEnabled(syncManager.getSyncHandlers().size + 1)) {
                            syncManager.updateSyncInfo(syncInfo, refresh = true)
                        }
                    },
                    shape = tiny3XRoundedCornerShape,
                    border = BorderStroke(tiny5X, connectedColor(AppUIColors.generalBackground)),
                    contentPadding = PaddingValues(horizontal = tiny, vertical = zero),
                    colors = ButtonDefaults.buttonColors(containerColor = AppUIColors.topBackground),
                    elevation = zeroButtonElevation,
                ) {
                    Text(
                        text = copywriter.getText("add"),
                        color = connectedColor(AppUIColors.generalBackground),
                        style = buttonTextStyle,
                    )
                }

                Spacer(modifier = Modifier.width(tiny))

                Button(
                    modifier = Modifier.height(xxLarge),
                    onClick = {
                        val blackSyncInfos: MutableList<SyncInfo> =
                            jsonUtils.JSON.decodeFromString(
                                config.blacklist,
                            )
                        for (blackSyncInfo in blackSyncInfos) {
                            if (blackSyncInfo.appInfo.appInstanceId == syncInfo.appInfo.appInstanceId) {
                                return@Button
                            }
                        }
                        blackSyncInfos.add(syncInfo)
                        val newBlackList = jsonUtils.JSON.encodeToString(blackSyncInfos)
                        configManager.updateConfig("blacklist", newBlackList)
                        nearbyDeviceManager.refreshSyncManager()
                    },
                    shape = tiny3XRoundedCornerShape,
                    border = BorderStroke(tiny5X, disconnectedColor(AppUIColors.generalBackground)),
                    contentPadding = PaddingValues(horizontal = tiny, vertical = zero),
                    colors = ButtonDefaults.buttonColors(containerColor = AppUIColors.topBackground),
                    elevation = zeroButtonElevation,
                ) {
                    Text(
                        text = copywriter.getText("block"),
                        color = disconnectedColor(AppUIColors.generalBackground),
                        style = buttonTextStyle,
                    )
                }
                Spacer(modifier = Modifier.width(medium))
            }
        }
    }
}
