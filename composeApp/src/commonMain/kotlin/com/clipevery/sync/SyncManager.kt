package com.clipevery.sync

import androidx.compose.runtime.MutableState
import com.clipevery.dao.sync.SyncRuntimeInfo

interface SyncManager {

    var realTimeSyncRuntimeInfos: MutableList<SyncRuntimeInfo>

    fun resolveSyncs(force: Boolean)

    fun resolveSync(
        id: String,
        force: Boolean,
    )

    fun getSyncHandlers(): Map<String, SyncHandler>

    var waitToVerifySyncRuntimeInfo: MutableState<SyncRuntimeInfo?>

    fun refreshWaitToVerifySyncRuntimeInfo()

    fun ignoreVerify(appInstanceId: String)

    fun toVerify(appInstanceId: String)

    suspend fun trustByToken(
        appInstanceId: String,
        token: Int,
    )
}
