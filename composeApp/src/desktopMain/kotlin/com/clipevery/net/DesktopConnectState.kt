package com.clipevery.net

import com.clipevery.Dependencies
import com.clipevery.dao.sync.HostInfo
import com.clipevery.dao.sync.SyncRuntimeInfo
import com.clipevery.dao.sync.SyncState
import com.clipevery.utils.TelnetUtils
import io.github.oshai.kotlinlogging.KotlinLogging

class ConnectedState: ConnectState {
    override suspend fun autoResolve(syncRuntimeInfo: SyncRuntimeInfo) {
        // do nothing
    }

    override suspend fun next(): Boolean {
        return false
    }

}

class ConnectingState(private val clientHandler: ClientHandler): ConnectState {

    val logger = KotlinLogging.logger {}

    override suspend fun autoResolve(syncRuntimeInfo: SyncRuntimeInfo) {
        clientHandler.getHostInfo()?.let { hostInfo ->
            if (clientHandler.isExistSession()) {
                useSession(hostInfo, syncRuntimeInfo.port)
            } else {
                createSession(hostInfo, syncRuntimeInfo.port)
            }
        } ?: run {
            clientHandler.updateSyncState(SyncState.DISCONNECTED)
        }
    }

    private suspend fun useSession(hostInfo: HostInfo, port: Int) {
        val syncClientApi = clientHandler.getSyncClientApi()
        val sessionCipher = clientHandler.getSessionCipher()
        try {
            if(syncClientApi.exchangePreKey(sessionCipher) { urlBuilder ->
                urlBuilder.port = port
                urlBuilder.host = hostInfo.hostAddress
            }) {
                return
            }
        } catch (e: Exception) {
            logger.warn(e) { "useSession exchangePreKey fail" }
        }
        logger.info { "connect state to unmatched ${hostInfo.hostAddress} $port"}
        clientHandler.updateSyncState(SyncState.UNMATCHED)
    }

    private suspend fun createSession(hostInfo: HostInfo, port: Int) {
        val syncClientApi = clientHandler.getSyncClientApi()
        val sessionCipher = clientHandler.getSessionCipher()
        try {
            syncClientApi.getPreKeyBundle { urlBuilder ->
                urlBuilder.port = port
                urlBuilder.host = hostInfo.hostAddress
            }?.let { preKeyBundle ->
                val sessionBuilder = clientHandler.createSessionBuilder()
                sessionBuilder.process(preKeyBundle)
                try {
                    if (syncClientApi.exchangePreKey(sessionCipher) { urlBuilder ->
                            urlBuilder.port = port
                            urlBuilder.host = hostInfo.hostAddress
                        }) {
                        return
                    }
                } catch (e: Exception) {
                    logger.warn(e) { "createSession exchangePreKey fail" }
                }
            }
        } catch (e: Exception) {
            logger.warn(e) { "createSession getPreKeyBundle fail" }
        }
        logger.info { "connect state to unmatched ${hostInfo.hostAddress} $port"}
        clientHandler.updateSyncState(SyncState.UNMATCHED)
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