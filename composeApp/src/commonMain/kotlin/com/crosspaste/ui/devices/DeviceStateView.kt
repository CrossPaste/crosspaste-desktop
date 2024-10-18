package com.crosspaste.ui.devices

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import com.crosspaste.app.AppInfo
import com.crosspaste.app.VersionCompatibilityChecker
import com.crosspaste.realm.sync.SyncRuntimeInfo
import com.crosspaste.realm.sync.SyncState
import com.crosspaste.ui.CrossPasteTheme.connectedColor
import com.crosspaste.ui.CrossPasteTheme.connectingColor
import com.crosspaste.ui.CrossPasteTheme.disconnectedColor
import com.crosspaste.ui.CrossPasteTheme.unmatchedColor
import com.crosspaste.ui.CrossPasteTheme.unverifiedColor
import com.crosspaste.ui.base.allowReceive
import com.crosspaste.ui.base.allowSend
import com.crosspaste.ui.base.block
import com.crosspaste.ui.base.sync
import com.crosspaste.ui.base.unverified

@Composable
fun AllowSendAndReceiveImage(syncRuntimeInfo: SyncRuntimeInfo): Painter {
    return if (syncRuntimeInfo.connectState == SyncState.UNVERIFIED) {
        unverified()
    } else if (syncRuntimeInfo.allowSend && syncRuntimeInfo.allowReceive) {
        sync()
    } else if (syncRuntimeInfo.allowSend) {
        allowSend()
    } else if (syncRuntimeInfo.allowReceive) {
        allowReceive()
    } else {
        block()
    }
}

fun getConnectStateColorAndText(
    appInfo: AppInfo,
    syncRuntimeInfo: SyncRuntimeInfo,
    checker: VersionCompatibilityChecker,
    refresh: Boolean,
): Pair<Color, String> {
    return if (refresh) {
        Pair(connectingColor(), "connecting")
    } else {
        val hasApiCompatibilityChangesBetween =
            checker.hasApiCompatibilityChangesBetween(
                appInfo.appVersion,
                syncRuntimeInfo.appVersion,
            )

        if (hasApiCompatibilityChangesBetween) {
            Pair(unmatchedColor(), "no_compatible")
        } else if (syncRuntimeInfo.allowSend || syncRuntimeInfo.allowReceive) {
            when (syncRuntimeInfo.connectState) {
                SyncState.CONNECTED -> Pair(connectedColor(), "connected")
                SyncState.CONNECTING -> Pair(connectingColor(), "connecting")
                SyncState.DISCONNECTED -> Pair(disconnectedColor(), "disconnected")
                SyncState.UNMATCHED -> Pair(unmatchedColor(), "unmatched")
                SyncState.UNVERIFIED -> Pair(unverifiedColor(), "unverified")
                else -> throw IllegalArgumentException("Unknown connectState: ${syncRuntimeInfo.connectState}")
            }
        } else {
            Pair(disconnectedColor(), "off_connected")
        }
    }
}
