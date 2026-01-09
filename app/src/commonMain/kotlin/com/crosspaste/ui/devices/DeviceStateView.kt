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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import com.crosspaste.db.sync.SyncState
import com.crosspaste.ui.LocalThemeExtState
import com.crosspaste.ui.base.StateTagStyle
import com.crosspaste.ui.base.StateTagView

val syncedStateStyle
    @Composable @ReadOnlyComposable
    get() =
        StateTagStyle(
            label = "sync_status_synced",
            containerColor = LocalThemeExtState.current.success.container,
            contentColor = LocalThemeExtState.current.success.onContainer,
            icon = Icons.Default.SyncAlt,
        )

val outgoingOnlyStateStyle
    @Composable @ReadOnlyComposable
    get() =
        StateTagStyle(
            label = "sync_status_outgoing_only",
            containerColor = LocalThemeExtState.current.info.container,
            contentColor = LocalThemeExtState.current.info.onContainer,
            icon = Icons.AutoMirrored.Filled.ArrowForward,
        )

val incomingOnlyStateStyle
    @Composable @ReadOnlyComposable
    get() =
        StateTagStyle(
            label = "sync_status_incoming_only",
            containerColor = LocalThemeExtState.current.info.container,
            contentColor = LocalThemeExtState.current.info.onContainer,
            icon = Icons.AutoMirrored.Filled.ArrowBack,
        )

val pauseSyncStateStyle
    @Composable @ReadOnlyComposable
    get() =
        StateTagStyle(
            label = "sync_status_paused",
            containerColor = LocalThemeExtState.current.neutral.container,
            contentColor = LocalThemeExtState.current.neutral.onContainer,
            icon = Icons.Default.Pause,
        )

val disconnectedStateStyle
    @Composable @ReadOnlyComposable
    get() =
        StateTagStyle(
            label = "sync_status_disconnected",
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            icon = Icons.Default.LinkOff,
        )

val unmatchedStateStyle
    @Composable @ReadOnlyComposable
    get() =
        StateTagStyle(
            label = "sync_status_unmatched",
            containerColor = LocalThemeExtState.current.special.container,
            contentColor = LocalThemeExtState.current.special.onContainer,
            icon = Icons.Default.Warning,
        )

val unverifiedStateStyle
    @Composable @ReadOnlyComposable
    get() =
        StateTagStyle(
            label = "sync_status_unverified",
            containerColor = LocalThemeExtState.current.special.container,
            contentColor = LocalThemeExtState.current.special.onContainer,
            icon = Icons.Default.Shield,
        )

val incompatibleStateStyle
    @Composable @ReadOnlyComposable
    get() =
        StateTagStyle(
            label = "sync_status_incompatible",
            containerColor = LocalThemeExtState.current.warning.container,
            contentColor = LocalThemeExtState.current.warning.onContainer,
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
