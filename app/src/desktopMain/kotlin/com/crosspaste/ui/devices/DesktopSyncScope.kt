package com.crosspaste.ui.devices

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.platform.Platform
import com.crosspaste.ui.LocalNavHostController
import com.crosspaste.ui.NearbyDeviceDetail

class DesktopSyncScope(
    override val syncInfo: SyncInfo,
) : SyncScope {

    override val platform: Platform
        get() = syncInfo.endpointInfo.platform

    override fun getDeviceDisplayName(): String = syncInfo.endpointInfo.deviceName

    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    override fun hoverModifier(
        modifier: Modifier,
        onHover: () -> Unit,
        onExitHover: () -> Unit,
    ): Modifier {
        val navController = LocalNavHostController.current
        return modifier
            .onPointerEvent(
                eventType = PointerEventType.Enter,
                onEvent = {
                    onHover()
                },
            ).onPointerEvent(
                eventType = PointerEventType.Exit,
                onEvent = {
                    onExitHover()
                },
            ).clickable {
                navController.navigate(NearbyDeviceDetail(syncInfo))
            }
    }
}
