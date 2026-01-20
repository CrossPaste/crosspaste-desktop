package com.crosspaste.sync

import com.crosspaste.db.sync.HostInfo
import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.net.VersionRelation
import com.crosspaste.platform.Platform
import kotlinx.coroutines.flow.StateFlow

interface SyncHandler {

    val syncRuntimeInfoFlow: StateFlow<SyncRuntimeInfo>

    val versionRelation: StateFlow<VersionRelation>

    val currentSyncRuntimeInfo: SyncRuntimeInfo
        get() = syncRuntimeInfoFlow.value

    val currentVersionRelation: VersionRelation
        get() = versionRelation.value

    fun getSyncPlatform(): Platform = syncRuntimeInfoFlow.value.platform

    fun updateSyncRuntimeInfo(syncRuntimeInfo: SyncRuntimeInfo)

    suspend fun getConnectHostAddress(): String?

    suspend fun getConnectHostInfo(): HostInfo? {
        val address = getConnectHostAddress() ?: return null
        return currentSyncRuntimeInfo.hostInfoList.firstOrNull { it.hostAddress == address }
    }

    suspend fun forceResolve()

    suspend fun updateAllowSend(allowSend: Boolean)

    suspend fun updateAllowReceive(allowReceive: Boolean)

    suspend fun updateNoteName(noteName: String)

    // use user input token to trust
    suspend fun trustByToken(
        token: Int,
        callback: (Boolean) -> Unit,
    )

    suspend fun showToken()

    suspend fun notifyExit()

    suspend fun markExit()

    suspend fun removeDevice()
}
