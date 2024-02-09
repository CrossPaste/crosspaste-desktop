package com.clipevery.net

import com.clipevery.Dependencies
import com.clipevery.dao.sync.HostInfo
import com.clipevery.dao.sync.SyncRuntimeInfo
import com.clipevery.dao.sync.SyncState
import com.clipevery.utils.TelnetUtils
import com.clipevery.utils.buildUrl
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
                if (useSession(hostInfo, syncRuntimeInfo.port)) {
                    return
                }
            }
            createSession(hostInfo, syncRuntimeInfo.port)
        } ?: run {
            logger.info { "${syncRuntimeInfo.platformName} to disconnected" }
            clientHandler.updateSyncState(SyncState.DISCONNECTED)
        }
    }

    private suspend fun useSession(hostInfo: HostInfo, port: Int): Boolean {
        try {
            return exchangePreKey(hostInfo, port)
        } catch (e: Exception) {
            logger.warn(e) { "useSession exchangePreKey fail" }
        }
        return false
    }

    private suspend fun createSession(hostInfo: HostInfo, port: Int) {
        val syncClientApi = clientHandler.getSyncClientApi()
        try {
            syncClientApi.getPreKeyBundle { urlBuilder ->
                buildUrl(urlBuilder, hostInfo, port, "sync", "preKeyBundle")
            }?.let { preKeyBundle ->
                val sessionBuilder = clientHandler.createSessionBuilder()
                sessionBuilder.process(preKeyBundle)
                try {
                    if (exchangePreKey(hostInfo, port)) {
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

    private suspend fun exchangePreKey(hostInfo: HostInfo, port: Int): Boolean {
        val syncClientApi = clientHandler.getSyncClientApi()
        val sessionCipher = clientHandler.getSessionCipher()
        return if (syncClientApi.exchangePreKey(sessionCipher) { urlBuilder ->
                buildUrl(urlBuilder, hostInfo, port, "sync", "exchangePreKey")
            }) {
            clientHandler.updateSyncState(SyncState.CONNECTED)
            true
        } else {
            false
        }
    }

    override suspend fun next(): Boolean {
        return true
    }

}

class DisconnectedState(private val clientHandler: ClientHandler): ConnectState {

    private val logger = KotlinLogging.logger {}

    private val telnetUtils = Dependencies.koinApplication.koin.get<TelnetUtils>()

    override suspend fun autoResolve(syncRuntimeInfo: SyncRuntimeInfo) {
        telnetUtils.switchHost(syncRuntimeInfo.hostInfoList, syncRuntimeInfo.port)?.let { hostInfo ->
            logger.info { "${hostInfo.hostAddress} to connecting" }
            clientHandler.updateSyncStateWithHostInfo(SyncState.Companion.CONNECTING, hostInfo, syncRuntimeInfo.port)
        } ?: run {
            logger.info { "${syncRuntimeInfo.platformName} to disconnected" }
            clientHandler.updateSyncState(SyncState.Companion.DISCONNECTED)
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

class UnverifiedState: ConnectState {
    override suspend fun autoResolve(syncRuntimeInfo: SyncRuntimeInfo) {
        // do nothing
    }

    override suspend fun next(): Boolean {
        return false
    }

}