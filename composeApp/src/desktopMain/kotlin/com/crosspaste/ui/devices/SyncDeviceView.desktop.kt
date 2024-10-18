package com.crosspaste.ui.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.dp
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.realm.sync.createSyncRuntimeInfo
import com.crosspaste.ui.CrossPasteTheme.selectColor

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun SyncDeviceView(
    syncInfo: SyncInfo,
    action: @Composable () -> Unit,
) {
    val syncRuntimeInfo = createSyncRuntimeInfo(syncInfo)

    var hover by remember { mutableStateOf(false) }
    val backgroundColor =
        if (hover) {
            MaterialTheme.colorScheme.selectColor()
        } else {
            MaterialTheme.colorScheme.background
        }

    DeviceBarView(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(60.dp)
                .background(backgroundColor)
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
                ),
        syncRuntimeInfo,
    ) {
        Row(
            modifier =
                Modifier.wrapContentSize()
                    .padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            action()
        }
    }
}
