package com.clipevery.sync

import com.clipevery.dao.sync.SyncRuntimeInfo
import org.signal.libsignal.protocol.SessionCipher

interface SyncHandler {

    var recommendedRefreshTime: Long

    var syncRuntimeInfo: SyncRuntimeInfo

    val sessionCipher: SessionCipher

    suspend fun getConnectHostAddress(): String?

    suspend fun forceResolve()

    suspend fun update(block: SyncRuntimeInfo.() -> Unit): SyncRuntimeInfo?

    fun updateSyncRuntimeInfo(syncRuntimeInfo: SyncRuntimeInfo)

    suspend fun clearContext()

    suspend fun trustByToken(token: Int)

    suspend fun showToken()

    suspend fun notifyExit()

    suspend fun markExit()
}
