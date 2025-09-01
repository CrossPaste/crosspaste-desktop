package com.crosspaste.sync

import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.net.VersionRelation

sealed interface SyncEvent {

    interface SyncRunTimeInfoEvent : SyncEvent {
        val syncRuntimeInfo: SyncRuntimeInfo
    }

    data class ResolveDisconnected(
        override val syncRuntimeInfo: SyncRuntimeInfo,
        val updateVersionRelation: (VersionRelation) -> Unit,
    ) : SyncRunTimeInfoEvent {
        override fun toString(): String = "ResolveDisconnected ${syncRuntimeInfo.appInstanceId}"
    }

    data class ResolveConnecting(
        override val syncRuntimeInfo: SyncRuntimeInfo,
        val updateVersionRelation: (VersionRelation) -> Unit,
    ) : SyncRunTimeInfoEvent {
        override fun toString(): String = "ResolveConnecting ${syncRuntimeInfo.appInstanceId}"
    }

    data class ResolveConnection(
        override val syncRuntimeInfo: SyncRuntimeInfo,
        val updateVersionRelation: (VersionRelation) -> Unit,
    ) : SyncRunTimeInfoEvent {
        override fun toString(): String = "ResolveConnection ${syncRuntimeInfo.appInstanceId}"
    }

    data class TrustByToken(
        override val syncRuntimeInfo: SyncRuntimeInfo,
        val token: Int,
    ) : SyncRunTimeInfoEvent {
        override fun toString(): String = "TrustByToken ${syncRuntimeInfo.appInstanceId} $token"
    }

    data class UpdateAllowSend(
        override val syncRuntimeInfo: SyncRuntimeInfo,
        val allowSend: Boolean,
    ) : SyncRunTimeInfoEvent {
        override fun toString(): String = "UpdateAllowSend ${syncRuntimeInfo.appInstanceId} $allowSend"
    }

    data class UpdateAllowReceive(
        override val syncRuntimeInfo: SyncRuntimeInfo,
        val allowReceive: Boolean,
    ) : SyncRunTimeInfoEvent {
        override fun toString(): String = "UpdateAllowReceive ${syncRuntimeInfo.appInstanceId} $allowReceive"
    }

    data class UpdateNoteName(
        override val syncRuntimeInfo: SyncRuntimeInfo,
        val noteName: String,
    ) : SyncRunTimeInfoEvent {
        override fun toString(): String = "UpdateNoteName ${syncRuntimeInfo.appInstanceId} $noteName"
    }

    data class ShowToken(
        override val syncRuntimeInfo: SyncRuntimeInfo,
    ) : SyncRunTimeInfoEvent {
        override fun toString(): String = "ShowToken ${syncRuntimeInfo.appInstanceId}"
    }

    data class NotifyExit(
        override val syncRuntimeInfo: SyncRuntimeInfo,
    ) : SyncRunTimeInfoEvent {
        override fun toString(): String = "NotifyExit ${syncRuntimeInfo.appInstanceId}"
    }

    data class MarkExit(
        override val syncRuntimeInfo: SyncRuntimeInfo,
    ) : SyncRunTimeInfoEvent {
        override fun toString(): String = "MarkExit ${syncRuntimeInfo.appInstanceId}"
    }

    data class RemoveDevice(
        override val syncRuntimeInfo: SyncRuntimeInfo,
    ) : SyncRunTimeInfoEvent {
        override fun toString(): String = "RemoveDevice ${syncRuntimeInfo.appInstanceId}"
    }

    data class RefreshSyncInfo(
        val appInstanceId: String,
    ) : SyncEvent {
        override fun toString(): String = "RefreshSyncInfo $appInstanceId"
    }

    data class UpdateSyncInfo(
        val syncInfo: SyncInfo,
    ) : SyncEvent {
        override fun toString(): String = "UpdateSyncInfo ${syncInfo.appInfo.appInstanceId}"
    }
}
