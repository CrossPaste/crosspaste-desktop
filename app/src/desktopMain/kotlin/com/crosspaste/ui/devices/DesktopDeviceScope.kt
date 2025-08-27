package com.crosspaste.ui.devices

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import com.crosspaste.app.AppWindowManager
import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.db.sync.SyncState
import com.crosspaste.sync.SyncManager
import com.crosspaste.ui.DeviceDetail
import org.koin.compose.koinInject

class DesktopDeviceScope(
    override var syncRuntimeInfo: SyncRuntimeInfo,
) : DeviceScope {

    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    override fun Modifier.hoverModifier(
        onHover: () -> Unit,
        onExitHover: () -> Unit,
    ): Modifier {
        val appWindowManager = koinInject<AppWindowManager>()
        val syncManager = koinInject<SyncManager>()
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
                if (syncRuntimeInfo.connectState == SyncState.UNVERIFIED) {
                    syncManager.toVerify(syncRuntimeInfo.appInstanceId)
                } else {
                    appWindowManager.toScreen(DeviceDetail, syncRuntimeInfo)
                }
            }
    }
}
