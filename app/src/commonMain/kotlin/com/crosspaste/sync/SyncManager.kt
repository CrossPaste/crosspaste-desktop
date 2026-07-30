package com.crosspaste.sync

import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.net.routing.SyncRoutingApi
import kotlinx.coroutines.flow.StateFlow

interface SyncManager : SyncRoutingApi {

    val realTimeSyncRuntimeInfos: StateFlow<List<SyncRuntimeInfo>>

    val unverifiedSyncRuntimeInfo: StateFlow<SyncRuntimeInfo?>

    val pairingCredentialTypes: StateFlow<Map<String, PairingCredentialType>>

    suspend fun start()

    suspend fun stop()

    fun createSyncHandler(syncRuntimeInfo: SyncRuntimeInfo): SyncHandler

    fun ignoreVerify(appInstanceId: String)

    fun toVerify(appInstanceId: String)

    fun rememberPairingCredentialType(syncInfo: SyncInfo)

    suspend fun refreshPairingCredentialType(appInstanceId: String): PairingCredentialRefreshResult

    fun updateAllowSend(
        appInstanceId: String,
        allowSend: Boolean,
    )

    fun updateAllowReceive(
        appInstanceId: String,
        allowReceive: Boolean,
    )

    fun updateNoteName(
        appInstanceId: String,
        noteName: String,
    )

    // Trust a device using the random bearer token carried by a scanned QR / typed on a
    // pairingVersion<2 peer's screen. Routes to POST /sync/trust.
    fun trustByBearerToken(
        appInstanceId: String,
        token: QrBearerToken,
        callback: (Boolean) -> Unit,
    )

    // Trust a device using the key-derived SAS the user compares/enters on a
    // pairingVersion>=2 peer. Routes to POST /sync/trust/v2/*.
    fun trustBySasCode(
        appInstanceId: String,
        code: SasCode,
        callback: (Boolean) -> Unit,
    )

    // Starts the v2 warm-up exchange for the pairing dialog. The implementation
    // records its generation before crossing an asynchronous event boundary.
    fun exchangeKeysForPairing(appInstanceId: String)

    // Best-effort release of the pending v2 exchange on the peer when the user
    // abandons pairing before confirm. Routes to POST /sync/trust/v2/cancel.
    fun cancelPairing(appInstanceId: String)

    fun refresh(
        ids: List<String> = listOf(),
        callback: () -> Unit = {},
    )
}
