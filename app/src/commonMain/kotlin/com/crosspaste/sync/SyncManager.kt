package com.crosspaste.sync

import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.net.routing.SyncRoutingApi
import kotlinx.coroutines.flow.StateFlow

interface SyncManager : SyncRoutingApi {

    val realTimeSyncRuntimeInfos: StateFlow<List<SyncRuntimeInfo>>

    fun start()

    fun stop()

    fun createSyncHandler(syncRuntimeInfo: SyncRuntimeInfo): SyncHandler

    fun ignoreVerify(appInstanceId: String)

    fun toVerify(appInstanceId: String)

    fun updateAllowSend(
        appInstanceId: String,
        allowSend: Boolean,
    )

    fun updateAllowReceive(
        appInstanceId: String,
        allowReceive: Boolean,
    )

    fun updateNoteName(
        appInstanceId: String,
        noteName: String,
    )

    fun trustByToken(
        appInstanceId: String,
        token: Int,
    )

    fun refresh(
        ids: List<String> = listOf(),
        callback: () -> Unit = {},
    )
}
