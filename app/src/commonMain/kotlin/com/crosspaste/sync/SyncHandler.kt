package com.crosspaste.sync

import com.crosspaste.db.sync.HostInfo
import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.net.VersionRelation
import com.crosspaste.platform.Platform
import kotlinx.coroutines.flow.StateFlow

interface SyncHandler {

    val syncRuntimeInfoFlow: StateFlow<SyncRuntimeInfo>

    val versionRelation: StateFlow<VersionRelation>

    val currentSyncRuntimeInfo: SyncRuntimeInfo
        get() = syncRuntimeInfoFlow.value

    val currentVersionRelation: VersionRelation
        get() = versionRelation.value

    fun getSyncPlatform(): Platform = syncRuntimeInfoFlow.value.platform

    fun updateSyncRuntimeInfo(syncRuntimeInfo: SyncRuntimeInfo)

    suspend fun getConnectHostAddress(): String?

    suspend fun getConnectHostInfo(): HostInfo? {
        val address = getConnectHostAddress() ?: return null
        return currentSyncRuntimeInfo.hostInfoList.firstOrNull { it.hostAddress == address }
    }

    suspend fun forceResolve()

    /**
     * Discovery-driven fast reconnect: invoked when mDNS re-discovers a paired but
     * DISCONNECTED peer. The same-IP reappearance never writes the DB, so the normal
     * state-machine path can't observe it — discovery drives the reconnect directly.
     * Default behaviour is a plain [forceResolve]; [GeneralSyncHandler] additionally
     * resets the connection-failure backoff first.
     */
    suspend fun fastReconnect() {
        forceResolve()
    }

    suspend fun updateAllowSend(allowSend: Boolean)

    suspend fun updateAllowReceive(allowReceive: Boolean)

    suspend fun updateNoteName(noteName: String)

    // Trust using the random QR/screen bearer token (POST /sync/trust).
    suspend fun trustByBearerToken(
        token: QrBearerToken,
        callback: (Boolean) -> Unit,
    )

    // Trust using the key-derived SAS the user enters (POST /sync/trust/v2/*).
    suspend fun trustBySasCode(
        code: SasCode,
        callback: (Boolean) -> Unit,
    )

    suspend fun exchangeKeysForPairing()

    suspend fun showToken()

    suspend fun showPairingCode()

    suspend fun notifyExit()

    suspend fun markExit()

    suspend fun removeDevice()

    fun cancelScope() {}
}
