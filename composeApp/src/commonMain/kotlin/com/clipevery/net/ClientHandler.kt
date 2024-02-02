package com.clipevery.net

import com.clipevery.dao.sync.HostInfo
import com.clipevery.net.clientapi.SyncClientApi
import org.signal.libsignal.protocol.SessionBuilder
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.state.SignalProtocolStore

interface ClientHandler {

    fun getId(): String

    fun getSyncClientApi(): SyncClientApi

    fun isExistSession(): Boolean

    fun createSessionBuilder(): SessionBuilder

    fun getSessionCipher(): SessionCipher

    fun getHostInfo(): HostInfo?

    fun port(): Int

    fun isConnected(): Boolean

    suspend fun updateSyncStateWithHostInfo(syncState: Int, hostInfo: HostInfo, port: Int)

    suspend fun updateSyncState(syncState: Int)

    suspend fun checkHandler(checkAction: CheckAction): Boolean
}


enum class CheckAction {
    CheckAll,
    CheckNonConnected
}