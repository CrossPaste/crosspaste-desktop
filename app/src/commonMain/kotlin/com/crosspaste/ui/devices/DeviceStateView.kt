package com.crosspaste.ui.devices

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Arrow_back
import com.composables.icons.materialsymbols.rounded.Arrow_forward
import com.composables.icons.materialsymbols.rounded.Close
import com.composables.icons.materialsymbols.rounded.Link_off
import com.composables.icons.materialsymbols.rounded.Pause
import com.composables.icons.materialsymbols.rounded.Refresh
import com.composables.icons.materialsymbols.rounded.Shield
import com.composables.icons.materialsymbols.rounded.Sync_alt
import com.composables.icons.materialsymbols.rounded.Warning
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
            icon = MaterialSymbols.Rounded.Sync_alt,
        )

val outgoingOnlyStateStyle
    @Composable @ReadOnlyComposable
    get() =
        StateTagStyle(
            label = "sync_status_outgoing_only",
            containerColor = LocalThemeExtState.current.info.container,
            contentColor = LocalThemeExtState.current.info.onContainer,
            icon = MaterialSymbols.Rounded.Arrow_forward,
        )

val incomingOnlyStateStyle
    @Composable @ReadOnlyComposable
    get() =
        StateTagStyle(
            label = "sync_status_incoming_only",
            containerColor = LocalThemeExtState.current.info.container,
            contentColor = LocalThemeExtState.current.info.onContainer,
            icon = MaterialSymbols.Rounded.Arrow_back,
        )

val pauseSyncStateStyle
    @Composable @ReadOnlyComposable
    get() =
        StateTagStyle(
            label = "sync_status_paused",
            containerColor = LocalThemeExtState.current.neutral.container,
            contentColor = LocalThemeExtState.current.neutral.onContainer,
            icon = MaterialSymbols.Rounded.Pause,
        )

val disconnectedStateStyle
    @Composable @ReadOnlyComposable
    get() =
        StateTagStyle(
            label = "sync_status_disconnected",
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            icon = MaterialSymbols.Rounded.Link_off,
        )

val unmatchedStateStyle
    @Composable @ReadOnlyComposable
    get() =
        StateTagStyle(
            label = "sync_status_unmatched",
            containerColor = LocalThemeExtState.current.warning.container,
            contentColor = LocalThemeExtState.current.warning.onContainer,
            icon = MaterialSymbols.Rounded.Warning,
        )

val unverifiedStateStyle
    @Composable @ReadOnlyComposable
    get() =
        StateTagStyle(
            label = "sync_status_unverified",
            containerColor = LocalThemeExtState.current.warning.container,
            contentColor = LocalThemeExtState.current.warning.onContainer,
            icon = MaterialSymbols.Rounded.Shield,
        )

val incompatibleStateStyle
    @Composable @ReadOnlyComposable
    get() =
        StateTagStyle(
            label = "sync_status_incompatible",
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
            icon = MaterialSymbols.Rounded.Close,
        )

val refreshingStateStyle
    @Composable @ReadOnlyComposable
    get() =
        StateTagStyle(
            label = "refresh",
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            icon = MaterialSymbols.Rounded.Refresh,
        )

@Composable
fun PlatformScope.SyncStateColor(): Color =
    if (this is DeviceScope) {
        val state = syncRuntimeInfo.connectState
        if (state == SyncState.CONNECTED) {
            if (syncRuntimeInfo.allowSend && syncRuntimeInfo.allowReceive) {
                LocalThemeExtState.current.success.color
            } else if (syncRuntimeInfo.allowSend) {
                LocalThemeExtState.current.info.color
            } else if (syncRuntimeInfo.allowReceive) {
                LocalThemeExtState.current.info.color
            } else {
                LocalThemeExtState.current.neutral.color
            }
        } else if (state == SyncState.DISCONNECTED) {
            MaterialTheme.colorScheme.error
        } else if (state == SyncState.UNMATCHED) {
            LocalThemeExtState.current.warning.color
        } else if (state == SyncState.UNVERIFIED) {
            LocalThemeExtState.current.info.color
        } else if (state == SyncState.INCOMPATIBLE) {
            MaterialTheme.colorScheme.error
        } else {
            LocalThemeExtState.current.warning.color
        }
    } else {
        LocalThemeExtState.current.info.color
    }

@Composable
fun DeviceScope.SyncStateTag(refreshing: Boolean) {
    val state = syncRuntimeInfo.connectState
    if (refreshing) {
        StateTagView(refreshingStateStyle)
    } else if (state == SyncState.CONNECTED) {
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
