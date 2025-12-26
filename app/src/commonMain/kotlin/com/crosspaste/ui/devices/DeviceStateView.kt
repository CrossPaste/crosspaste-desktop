package com.crosspaste.ui.devices

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.crosspaste.db.sync.SyncState
import com.crosspaste.ui.base.StateTagStyle
import com.crosspaste.ui.base.StateTagView

val syncedStateStyle =
    StateTagStyle(
        label = "sync_status_synced",
        containerColor = Color(0xFFCCFBF1),
        contentColor = Color(0xFF115E59),
        icon = Icons.Default.SyncAlt,
    )

val outgoingOnlyStateStyle =
    StateTagStyle(
        label = "sync_status_outgoing_only",
        containerColor = Color(0xFFEDE9FE),
        contentColor = Color(0xFF5B21B6),
        icon = Icons.AutoMirrored.Filled.ArrowForward,
    )

val incomingOnlyStateStyle =
    StateTagStyle(
        label = "sync_status_incoming_only",
        containerColor = Color(0xFFE0E7FF),
        contentColor = Color(0xFF3730A3),
        icon = Icons.AutoMirrored.Filled.ArrowBack,
    )

val pauseSyncStateStyle =
    StateTagStyle(
        label = "sync_status_paused",
        containerColor = Color(0xFFF3F4F6),
        contentColor = Color(0xFF374151),
        icon = Icons.Default.Pause,
    )

val disconnectedStateStyle =
    StateTagStyle(
        label = "sync_status_disconnected",
        containerColor = Color(0xFFE4E4E7),
        contentColor = Color(0xFF3F3F46),
        icon = Icons.Default.LinkOff,
    )

val unmatchedStateStyle =
    StateTagStyle(
        label = "sync_status_unmatched",
        containerColor = Color(0xFFFEF3C7),
        contentColor = Color(0xFF92400E),
        icon = Icons.Default.Warning,
    )

val unverifiedStateStyle =
    StateTagStyle(
        label = "sync_status_unverified",
        containerColor = Color(0xFFFFEDD5),
        contentColor = Color(0xFF9A3412),
        icon = Icons.Default.Shield,
    )

val incompatibleStateStyle =
    StateTagStyle(
        label = "sync_status_incompatible",
        containerColor = Color(0xFFFFE4E6),
        contentColor = Color(0xFF9F1239),
        icon = Icons.Default.Close,
    )

@Composable
fun DeviceScope.SyncStateTag() {
    val state = syncRuntimeInfo.connectState
    if (state == SyncState.CONNECTED) {
        if (syncRuntimeInfo.allowSend && syncRuntimeInfo.allowReceive) {
            StateTagView(syncedStateStyle)
        } else if (syncRuntimeInfo.allowSend) {
            StateTagView(outgoingOnlyStateStyle)
        } else if (syncRuntimeInfo.allowReceive) {
            StateTagView(incomingOnlyStateStyle)
        } else {
            StateTagView(pauseSyncStateStyle)
        }
    } else if (state == SyncState.DISCONNECTED) {
        StateTagView(disconnectedStateStyle)
    } else if (state == SyncState.UNMATCHED) {
        StateTagView(unmatchedStateStyle)
    } else if (state == SyncState.UNVERIFIED) {
        StateTagView(unverifiedStateStyle)
    } else if (state == SyncState.INCOMPATIBLE) {
        StateTagView(incompatibleStateStyle)
    }
}
