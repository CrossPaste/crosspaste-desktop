package com.crosspaste.sync

import com.crosspaste.dao.sync.SyncRuntimeInfo
import com.crosspaste.signal.SignalMessageProcessor

interface SyncHandler {

    var compatibility: Boolean

    var recommendedRefreshTime: Long

    var syncRuntimeInfo: SyncRuntimeInfo

    val signalProcessor: SignalMessageProcessor

    suspend fun getConnectHostAddress(): String?

    suspend fun forceResolve()

    suspend fun update(block: SyncRuntimeInfo.() -> Unit): SyncRuntimeInfo?

    suspend fun tryDirectUpdateConnected()

    suspend fun clearContext()

    suspend fun trustByToken(token: Int)

    suspend fun showToken()

    suspend fun notifyExit()

    suspend fun markExit()
}
