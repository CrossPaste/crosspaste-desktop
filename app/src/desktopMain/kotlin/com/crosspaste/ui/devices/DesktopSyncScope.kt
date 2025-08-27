package com.crosspaste.ui.devices

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import com.crosspaste.app.AppWindowManager
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.platform.Platform
import com.crosspaste.ui.NearbyDeviceDetail
import org.koin.compose.koinInject

class DesktopSyncScope(
    override val syncInfo: SyncInfo,
) : SyncScope {

    override val platform: Platform
        get() = syncInfo.endpointInfo.platform

    override fun getDeviceDisplayName(): String = syncInfo.endpointInfo.deviceName

    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    override fun Modifier.hoverModifier(
        onHover: () -> Unit,
        onExitHover: () -> Unit,
    ): Modifier {
        val appWindowManager = koinInject<AppWindowManager>()
        return this
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
                appWindowManager.toScreen(NearbyDeviceDetail, syncInfo)
            }
    }
}
