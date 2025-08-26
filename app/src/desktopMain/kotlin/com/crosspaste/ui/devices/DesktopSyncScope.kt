package com.crosspaste.ui.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    override fun Modifier.hoverModifier(): Modifier {
        var hover by remember { mutableStateOf(false) }
        val background =
            if (hover) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            }
        return this
            .background(background)
            .onPointerEvent(
                eventType = PointerEventType.Enter,
                onEvent = {
                    hover = true
                },
            ).onPointerEvent(
                eventType = PointerEventType.Exit,
                onEvent = {
                    hover = false
                },
            )
    }

    override val platform: Platform
        get() = syncInfo.endpointInfo.platform

    override fun getDeviceDisplayName(): String = syncInfo.endpointInfo.deviceName

    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    override fun Modifier.hoverModifier(
        onEnter: () -> Unit,
        onExit: () -> Unit,
    ): Modifier {
        val appWindowManager = koinInject<AppWindowManager>()
        return this
            .onPointerEvent(
                eventType = PointerEventType.Enter,
                onEvent = {
                    onEnter()
                },
            ).onPointerEvent(
                eventType = PointerEventType.Exit,
                onEvent = {
                    onExit()
                },
            ).clickable {
                appWindowManager.toScreen(NearbyDeviceDetail, syncInfo)
            }
    }
}
