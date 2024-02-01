package com.clipevery.net

import com.clipevery.Dependencies
import com.clipevery.dao.sync.SyncRuntimeInfo
import com.clipevery.dao.sync.SyncState
import com.clipevery.utils.TelnetUtils

class ConnectedState: ConnectState {
    override suspend fun autoResolve(syncRuntimeInfo: SyncRuntimeInfo) {
        // do nothing
    }

    override suspend fun next(): Boolean {
        return false
    }

}

class ConnectingState(private val clientHandler: ClientHandler): ConnectState {
    override suspend fun autoResolve(syncRuntimeInfo: SyncRuntimeInfo) {
        clientHandler.getHostInfo()?.let {
            val syncClientApi = clientHandler.getSyncClientApi()
            try {
                syncClientApi.getPreKeyBundle { urlBuilder ->
                    urlBuilder.port = clientHandler.port()
                    urlBuilder.host = it.hostAddress
                }?.let {
                    val sessionBuilder = clientHandler.createSessionBuilder()
                    sessionBuilder.process(it)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            syncClientApi
        }
    }

    override suspend fun next(): Boolean {
        return true
    }

}

class DisconnectedState(private val clientHandler: ClientHandler): ConnectState {

    private val telnetUtils = Dependencies.koinApplication.koin.get<TelnetUtils>()

    override suspend fun autoResolve(syncRuntimeInfo: SyncRuntimeInfo) {
        telnetUtils.switchHost(syncRuntimeInfo.hostInfoList, syncRuntimeInfo.port)?.let { hostInfo ->
            clientHandler.updateSyncStateWithHostInfo(SyncState.Companion.CONNECTING, hostInfo, syncRuntimeInfo.port)
        } ?: run {
            clientHandler.updateSyncState(SyncState.Companion.UNMATCHED)
        }
    }

    override suspend fun next(): Boolean {
        return false
    }

}

class UnmatchedState: ConnectState {
    override suspend fun autoResolve(syncRuntimeInfo: SyncRuntimeInfo) {
        // do nothing
    }

    override suspend fun next(): Boolean {
        return false
    }

}

class UnverifiedState(private val clientHandler: ClientHandler): ConnectState {
    override suspend fun autoResolve(syncRuntimeInfo: SyncRuntimeInfo) {
        // do nothing
    }

    override suspend fun next(): Boolean {
        return false
    }

}