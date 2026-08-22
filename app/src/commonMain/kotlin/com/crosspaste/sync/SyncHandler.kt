package com.crosspaste.sync

import com.crosspaste.db.sync.HostInfo
import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.net.VersionRelation
import com.crosspaste.platform.Platform
import kotlinx.coroutines.flow.StateFlow

/** Outcome of asking an unverified peer to surface and open its v3 pairing window. */
enum class ShowPairingCodeResult {
    /** The peer accepted the request and opened its pairing UI/window. */
    SHOWN,

    /** The peer is reachable but policy currently forbids opening the window. */
    NOT_ACCEPTING,

    /** The request could not be delivered or the peer is not in a requestable state. */
    UNAVAILABLE,
}

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

    suspend fun exchangeKeysForPairing(generation: Long)

    // Release the pending v2 exchange we started on the peer when pairing is
    // abandoned before confirm (POST /sync/trust/v2/cancel, best-effort).
    // [generation] = the abandoned exchange's ledger generation, captured at
    // the UI call site so a later exchange from a reopened dialog is never
    // cancelled.
    suspend fun cancelPairing(generation: Long)

    suspend fun showToken()

    suspend fun showPairingCode(): ShowPairingCodeResult

    suspend fun notifyExit()

    suspend fun markExit()

    suspend fun removeDevice()

    fun cancelScope() {}
}
