package com.clipevery.net

import com.clipevery.dao.sync.HostInfo
import com.clipevery.dao.sync.SyncRuntimeInfo
import com.clipevery.dao.sync.SyncRuntimeInfoDao
import com.clipevery.dao.sync.SyncState
import com.clipevery.net.clientapi.SyncClientApi
import io.github.oshai.kotlinlogging.KotlinLogging
import org.signal.libsignal.protocol.SessionBuilder
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.state.SignalProtocolStore

class DesktopClientHandler(private var syncRuntimeInfo: SyncRuntimeInfo,
                           private val syncClientApi: SyncClientApi,
                           private val signalProtocolStore: SignalProtocolStore,
                           private val syncRuntimeInfoDao: SyncRuntimeInfoDao): ClientHandler {

    private val logger = KotlinLogging.logger {}

    private val signalProtocolAddress = SignalProtocolAddress( syncRuntimeInfo.appInstanceId, 1)

    private val sessionCipher: SessionCipher = SessionCipher(signalProtocolStore, signalProtocolAddress)

    private var currentHostInfo: HostInfo? = null

    private var port: Int = 0

    private var currentConnectState: ConnectState? = null

    private val connected: ConnectedState = ConnectedState()

    private val connecting: ConnectingState = ConnectingState(this)

    private val disconnected: DisconnectedState = DisconnectedState(this)

    private val unmatched: UnmatchedState = UnmatchedState()

    private val unverified: UnverifiedState = UnverifiedState()

    override fun getId(): String {
        return syncRuntimeInfo.appInstanceId
    }

    override fun getSyncClientApi(): SyncClientApi {
        return syncClientApi
    }

    override fun isExistSession(): Boolean {
        signalProtocolStore.loadSession(signalProtocolAddress)?.let {
            return true
        }
        return false
    }

    override fun createSessionBuilder(): SessionBuilder {
        return SessionBuilder(signalProtocolStore,
            SignalProtocolAddress( syncRuntimeInfo.appInstanceId, 1))
    }

    override fun getSessionCipher(): SessionCipher {
        return sessionCipher
    }

    override fun getHostInfo(): HostInfo? {
        return currentHostInfo
    }

    override fun port(): Int {
        return port
    }

    override fun isConnected(): Boolean {
        return false
    }

    override suspend fun updateSyncStateWithHostInfo(
        syncState: Int,
        hostInfo: HostInfo,
        port: Int
    ) {
        this.currentHostInfo = hostInfo
        this.port = port
        syncRuntimeInfoDao.updateConnectInfo(syncRuntimeInfo, syncState, hostInfo.hostAddress)
        updateConnectState(syncState)
    }

    override suspend fun updateSyncState(syncState: Int) {
        syncRuntimeInfoDao.updateConnectState(syncRuntimeInfo, syncState)
        updateConnectState(syncState)
    }

    private fun updateConnectState(syncState: Int) {
        when(syncState) {
            SyncState.CONNECTED -> this.currentConnectState = this.connected
            SyncState.CONNECTING -> this.currentConnectState = this.connecting
            SyncState.DISCONNECTED -> this.currentConnectState = this.disconnected
            SyncState.UNMATCHED -> this.currentConnectState = this.unmatched
            SyncState.UNVERIFIED -> this.currentConnectState = this.unverified
        }
    }

    override suspend fun checkHandler(checkAction: CheckAction): Boolean {
        return todoResolveSuccess {
            resolveConnect(checkAction)
        }
    }

    private suspend fun todoResolveSuccess(todo: suspend () -> Unit): Boolean {
        todo()
        return this.isConnected()
    }

    private suspend fun resolveConnect(checkAction: CheckAction) {
        if (this.currentConnectState == null || checkAction == CheckAction.CheckAll || currentConnectState !is ConnectedState) {
            this.currentConnectState = this.disconnected
        }

        syncRuntimeInfoDao.getSyncRuntimeInfo(syncRuntimeInfo.appInstanceId)?.let {
            syncRuntimeInfo = it
            do {
                try {
                    this.currentConnectState!!.autoResolve(it)
                } catch (e: Exception) {
                    logger.error(e) { "resolve fail" }
                }
            } while (this.currentConnectState!!.next())
        }
    }
}
