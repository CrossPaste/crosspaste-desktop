package com.clipevery.sync

import com.clipevery.dao.sync.SyncRuntimeInfo
import org.signal.libsignal.protocol.SessionCipher

interface SyncHandler {

    var syncRuntimeInfo: SyncRuntimeInfo

    val sessionCipher: SessionCipher

    suspend fun getConnectHostAddress(): String?

    suspend fun resolveSync(force: Boolean)

    fun updateSyncRuntimeInfo(syncRuntimeInfo: SyncRuntimeInfo)

    fun clearContext()

    suspend fun trustByToken(token: Int)
}
