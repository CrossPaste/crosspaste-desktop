package com.crosspaste.ui.devices

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Arrow_back
import com.composables.icons.materialsymbols.rounded.Arrow_forward
import com.composables.icons.materialsymbols.rounded.Autorenew
import com.composables.icons.materialsymbols.rounded.Close
import com.composables.icons.materialsymbols.rounded.Link_off
import com.composables.icons.materialsymbols.rounded.Pause
import com.composables.icons.materialsymbols.rounded.Shield
import com.composables.icons.materialsymbols.rounded.Sync_alt
import com.composables.icons.materialsymbols.rounded.Warning
import com.crosspaste.db.sync.SyncState
import com.crosspaste.ui.LocalThemeExtState
import com.crosspaste.ui.base.StateTagStyle
import com.crosspaste.ui.base.StateTagView
import com.crosspaste.utils.DateUtils
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

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

val connectingStateStyle
    @Composable @ReadOnlyComposable
    get() =
        StateTagStyle(
            label = "sync_status_connecting",
            containerColor = LocalThemeExtState.current.neutral.container,
            contentColor = LocalThemeExtState.current.neutral.onContainer,
            icon = MaterialSymbols.Rounded.Autorenew,
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

/**
 * Everything the list row and the detail header derive from a device's connect
 * state: the platform icon tint, the status tag, and the action button colors.
 * Kept in one mapping so the three never disagree on a state. A manual refresh
 * is shown exactly like an automatic reconnect: both are "connecting".
 */
class SyncStateVisual(
    val iconColor: Color,
    val tag: StateTagStyle,
)

@Composable
@ReadOnlyComposable
fun DeviceScope.syncStateVisual(connecting: Boolean = false): SyncStateVisual {
    val themeExt = LocalThemeExtState.current
    if (connecting) {
        return SyncStateVisual(themeExt.neutral.color, connectingStateStyle)
    }
    return when (syncRuntimeInfo.connectState) {
        SyncState.CONNECTED ->
            when {
                syncRuntimeInfo.allowSend && syncRuntimeInfo.allowReceive ->
                    SyncStateVisual(themeExt.success.color, syncedStateStyle)
                syncRuntimeInfo.allowSend ->
                    SyncStateVisual(themeExt.info.color, outgoingOnlyStateStyle)
                syncRuntimeInfo.allowReceive ->
                    SyncStateVisual(themeExt.info.color, incomingOnlyStateStyle)
                else ->
                    SyncStateVisual(themeExt.neutral.color, pauseSyncStateStyle)
            }
        SyncState.DISCONNECTED -> SyncStateVisual(MaterialTheme.colorScheme.error, disconnectedStateStyle)
        SyncState.UNMATCHED -> SyncStateVisual(themeExt.warning.color, unmatchedStateStyle)
        SyncState.UNVERIFIED -> SyncStateVisual(themeExt.info.color, unverifiedStateStyle)
        SyncState.INCOMPATIBLE -> SyncStateVisual(MaterialTheme.colorScheme.error, incompatibleStateStyle)
        else -> SyncStateVisual(themeExt.neutral.color, connectingStateStyle)
    }
}

@Composable
@ReadOnlyComposable
fun PlatformScope.SyncStateColor(): Color =
    if (this is DeviceScope) {
        syncStateVisual().iconColor
    } else {
        LocalThemeExtState.current.info.color
    }

@Composable
fun DeviceScope.SyncStateTag(connecting: Boolean) {
    StateTagView(syncStateVisual(connecting).tag)
}

/**
 * The single "connecting" flag a device row shows: true while a manual refresh
 * runs or the sync layer is in CONNECTING, held for at least
 * [MIN_CONNECTING_VISIBLE] once entered. A reconnect against an unreachable
 * peer fails within milliseconds, and without the hold every automatic attempt
 * flickers the tag, the icon tint and the spinner.
 */
class ConnectingIndicator(
    val visible: Boolean,
    val onRefreshingChange: (Boolean) -> Unit,
)

@Composable
fun DeviceScope.rememberConnectingIndicator(): ConnectingIndicator {
    var refreshing by remember { mutableStateOf(false) }
    val connecting = refreshing || syncRuntimeInfo.connectState == SyncState.CONNECTING
    var visible by remember { mutableStateOf(connecting) }
    var since by remember { mutableStateOf(0L) }

    LaunchedEffect(connecting) {
        if (connecting) {
            since = DateUtils.nowEpochMilliseconds()
            visible = true
        } else if (visible) {
            val elapsed = (DateUtils.nowEpochMilliseconds() - since).milliseconds
            val remaining = MIN_CONNECTING_VISIBLE - elapsed
            if (remaining.isPositive()) {
                delay(remaining)
            }
            visible = false
        }
    }

    return ConnectingIndicator(visible) { refreshing = it }
}

private val MIN_CONNECTING_VISIBLE = 1.seconds
