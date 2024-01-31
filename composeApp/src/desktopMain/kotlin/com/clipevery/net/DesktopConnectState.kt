package com.clipevery.net

import com.clipevery.dao.sync.SyncRuntimeInfo

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
        TODO("Not yet implemented")
    }

}

class DisconnectedState(private val clientHandler: ClientHandler): ConnectState {
    override suspend fun autoResolve(syncRuntimeInfo: SyncRuntimeInfo) {
        TODO("Not yet implemented")
    }

    override suspend fun next(): Boolean {
        return false
    }

}

class UnmatchedState: ConnectState {
    override suspend fun autoResolve(syncRuntimeInfo: SyncRuntimeInfo) {
        TODO("Not yet implemented")
    }

    override suspend fun next(): Boolean {
        TODO("Not yet implemented")
    }

}

class UnverifiedState(private val clientHandler: ClientHandler): ConnectState {
    override suspend fun autoResolve(syncRuntimeInfo: SyncRuntimeInfo) {
        TODO("Not yet implemented")
    }

    override suspend fun next(): Boolean {
        TODO("Not yet implemented")
    }

}