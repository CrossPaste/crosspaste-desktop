package com.clipevery.sync

import com.clipevery.dao.sync.SyncRuntimeInfo
import com.clipevery.dto.sync.SyncInfo
import org.signal.libsignal.protocol.SessionCipher

interface SyncHandler {

    var syncRuntimeInfo: SyncRuntimeInfo

    val sessionCipher: SessionCipher

    suspend fun getConnectHostAddress(): String?

    suspend fun resolveSync(
        currentDeviceSyncInfo: SyncInfo,
        resolveWay: ResolveWay,
    )

    suspend fun update(block: SyncRuntimeInfo.() -> Unit): SyncRuntimeInfo?

    fun updateSyncRuntimeInfo(syncRuntimeInfo: SyncRuntimeInfo)

    fun clearContext()

    suspend fun trustByToken(token: Int)

    suspend fun showToken()

    suspend fun notifyExit()
}
