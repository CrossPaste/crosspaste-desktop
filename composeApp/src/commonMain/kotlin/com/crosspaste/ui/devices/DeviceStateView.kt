package com.crosspaste.ui.devices

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import com.crosspaste.net.VersionRelation
import com.crosspaste.realm.sync.SyncRuntimeInfo
import com.crosspaste.realm.sync.SyncState
import com.crosspaste.ui.base.allowReceive
import com.crosspaste.ui.base.allowSend
import com.crosspaste.ui.base.block
import com.crosspaste.ui.base.sync
import com.crosspaste.ui.base.unverified
import com.crosspaste.ui.theme.CrossPasteTheme.connectedColor
import com.crosspaste.ui.theme.CrossPasteTheme.connectingColor
import com.crosspaste.ui.theme.CrossPasteTheme.disconnectedColor
import com.crosspaste.ui.theme.CrossPasteTheme.unmatchedColor
import com.crosspaste.ui.theme.CrossPasteTheme.unverifiedColor

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
    syncRuntimeInfo: SyncRuntimeInfo,
    versionRelation: VersionRelation?,
    refresh: Boolean,
): Pair<Color, String> {
    return if (refresh) {
        Pair(connectingColor(), "connecting")
    } else {
        if (versionRelation != VersionRelation.EQUAL_TO) {
            // versionRelation is relation current app,
            // so LOWER_THAN means the other app is higher
            // HIGHER_THAN means the other app is lower
            Pair(
                unmatchedColor(),
                if (versionRelation == VersionRelation.LOWER_THAN) {
                    "version_higher"
                } else {
                    "version_lower"
                },
            )
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
