package com.crosspaste.sync

import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.db.sync.SyncRuntimeInfoDao
import com.crosspaste.db.sync.SyncState
import com.crosspaste.dto.secure.KeyExchangeResponse
import com.crosspaste.net.clientapi.SuccessResult
import com.crosspaste.net.clientapi.SyncClientApi
import com.crosspaste.net.ws.WsEnvelope
import com.crosspaste.net.ws.WsMessageType
import com.crosspaste.net.ws.WsSessionManager
import com.crosspaste.secure.SecureStore
import com.crosspaste.utils.DateUtils.nowEpochMilliseconds
import com.crosspaste.utils.HostAndPort
import com.crosspaste.utils.buildUrl
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.util.collections.ConcurrentMap

class SyncDeviceManager(
    private val secureStore: SecureStore,
    private val syncClientApi: SyncClientApi,
    private val syncRuntimeInfoDao: SyncRuntimeInfoDao,
    private val wsSessionManager: WsSessionManager,
) {

    private val logger = KotlinLogging.logger {}

    /**
     * The v2 exchange we initiated on a peer that has not yet been confirmed
     * or cancelled: the responder's exchange timestamp (its generation marker)
     * plus when we recorded it locally. This is what makes cancelPairing both
     * targeted (the cancel names the exact exchange it releases) and
     * self-gating (no record → nothing we own → no request), independent of
     * the peer's connect state (#4684 / #4690 review).
     */
    private val pendingExchanges: MutableMap<String, PendingExchangeRecord> = ConcurrentMap()

    data class PendingExchangeRecord(
        val exchangeTimestamp: Long,
        val recordedAt: Long,
    )

    fun rememberPendingExchange(
        appInstanceId: String,
        exchangeTimestamp: Long,
    ) {
        pendingExchanges[appInstanceId] =
            PendingExchangeRecord(
                exchangeTimestamp = exchangeTimestamp,
                recordedAt = nowEpochMilliseconds(),
            )
    }

    fun clearPendingExchange(appInstanceId: String) {
        pendingExchanges.remove(appInstanceId)
    }

    suspend fun updateAllowSend(
        syncRuntimeInfo: SyncRuntimeInfo,
        allowSend: Boolean,
    ) {
        syncRuntimeInfoDao.updateAllowSend(syncRuntimeInfo.copy(allowSend = allowSend))
    }

    suspend fun updateAllowReceive(
        syncRuntimeInfo: SyncRuntimeInfo,
        allowReceive: Boolean,
    ) {
        syncRuntimeInfoDao.updateAllowReceive(syncRuntimeInfo.copy(allowReceive = allowReceive))
    }

    suspend fun updateNoteName(
        syncRuntimeInfo: SyncRuntimeInfo,
        noteName: String,
    ) {
        syncRuntimeInfoDao.updateNoteName(syncRuntimeInfo.copy(noteName = noteName))
    }

    suspend fun exchangeKeysForPairing(syncRuntimeInfo: SyncRuntimeInfo) {
        if (syncRuntimeInfo.connectState == SyncState.UNVERIFIED) {
            syncRuntimeInfo.connectHostAddress?.let { host ->
                val hostAndPort = HostAndPort(host, syncRuntimeInfo.port)
                val result =
                    syncClientApi.exchangeKeys(syncRuntimeInfo.appInstanceId) {
                        buildUrl(hostAndPort)
                    }
                if (result is SuccessResult) {
                    result.getResult<KeyExchangeResponse>()?.let { response ->
                        rememberPendingExchange(syncRuntimeInfo.appInstanceId, response.timestamp)
                    }
                    logger.info { "exchangeKeysForPairing success $host ${syncRuntimeInfo.port}" }
                } else {
                    logger.warn { "exchangeKeysForPairing failed $host ${syncRuntimeInfo.port}" }
                    syncRuntimeInfoDao.updateConnectInfo(
                        syncRuntimeInfo.copy(
                            connectState = SyncState.DISCONNECTED,
                            modifyTime = nowEpochMilliseconds(),
                        ),
                    )
                }
            }
        }
    }

    /**
     * Best-effort release of the pending v2 exchange we opened on the peer
     * (#4684). Gated by ownership, not connect state: we send exactly when we
     * hold an unconsumed exchange record for the peer (a confirm clears it, a
     * successful exchange creates it), so the request is still sent after the
     * peer dropped to DISCONNECTED, and skipped after a successful pairing.
     * [requestedAt] is when the user abandoned the dialog — a record written
     * after that belongs to a newer dialog, so this stale cancel must not
     * release it. Failures are ignored: pairing is being abandoned anyway, and
     * the peer's entry self-heals when a later exchange from us replaces it.
     */
    suspend fun cancelPairing(
        syncRuntimeInfo: SyncRuntimeInfo,
        requestedAt: Long,
    ) {
        val appInstanceId = syncRuntimeInfo.appInstanceId
        val record = pendingExchanges[appInstanceId] ?: return
        if (record.recordedAt > requestedAt) {
            logger.info { "cancelPairing skipped for $appInstanceId (a newer exchange superseded it)" }
            return
        }
        val host = syncRuntimeInfo.connectHostAddress ?: return
        clearPendingExchange(appInstanceId)
        val hostAndPort = HostAndPort(host, syncRuntimeInfo.port)
        val result =
            syncClientApi.trustV2Cancel(record.exchangeTimestamp) {
                buildUrl(hostAndPort)
            }
        if (result is SuccessResult) {
            logger.info { "cancelPairing released pending exchange on $host ${syncRuntimeInfo.port}" }
        } else {
            logger.info { "cancelPairing best-effort release failed $host ${syncRuntimeInfo.port}" }
        }
    }

    suspend fun showToken(syncRuntimeInfo: SyncRuntimeInfo) {
        if (syncRuntimeInfo.connectState == SyncState.UNVERIFIED) {
            syncRuntimeInfo.connectHostAddress?.let { host ->
                val hostAndPort = HostAndPort(host, syncRuntimeInfo.port)
                val result =
                    syncClientApi.showToken {
                        buildUrl(hostAndPort)
                    }
                if (result is SuccessResult) {
                    logger.info { "showToken success $host ${syncRuntimeInfo.port}" }
                } else {
                    syncRuntimeInfoDao.updateConnectInfo(
                        syncRuntimeInfo.copy(
                            connectState = SyncState.DISCONNECTED,
                            modifyTime = nowEpochMilliseconds(),
                        ),
                    )
                }
            }
        }
    }

    suspend fun showPairingCode(syncRuntimeInfo: SyncRuntimeInfo): Boolean {
        if (syncRuntimeInfo.connectState != SyncState.UNVERIFIED) return false
        val host = syncRuntimeInfo.connectHostAddress ?: return false
        val hostAndPort = HostAndPort(host, syncRuntimeInfo.port)
        val result =
            syncClientApi.showPairingCode {
                buildUrl(hostAndPort)
            }
        return if (result is SuccessResult) {
            logger.info { "showPairingCode success $host ${syncRuntimeInfo.port}" }
            true
        } else {
            syncRuntimeInfoDao.updateConnectInfo(
                syncRuntimeInfo.copy(
                    connectState = SyncState.DISCONNECTED,
                    modifyTime = nowEpochMilliseconds(),
                ),
            )
            false
        }
    }

    suspend fun notifyExit(syncRuntimeInfo: SyncRuntimeInfo) {
        if (syncRuntimeInfo.connectState == SyncState.CONNECTED) {
            // Prefer WebSocket when available (required for extensions that have no HTTP server)
            if (wsSessionManager.send(syncRuntimeInfo.appInstanceId, WsEnvelope(type = WsMessageType.NOTIFY_EXIT))) {
                logger.info { "notifyExit via WebSocket ${syncRuntimeInfo.appInstanceId}" }
                return
            }
            syncRuntimeInfo.connectHostAddress?.let { host ->
                val hostAndPort = HostAndPort(host, syncRuntimeInfo.port)
                syncClientApi.notifyExit {
                    buildUrl(hostAndPort)
                }
            }
        }
    }

    suspend fun markExit(syncRuntimeInfo: SyncRuntimeInfo) {
        logger.info { "markExit ${syncRuntimeInfo.appInstanceId}" }
        syncRuntimeInfoDao.updateConnectInfo(
            syncRuntimeInfo.copy(
                connectState = SyncState.DISCONNECTED,
                modifyTime = nowEpochMilliseconds(),
            ),
        )
    }

    suspend fun removeDevice(syncRuntimeInfo: SyncRuntimeInfo) {
        // Try WebSocket notification first (fast, buffer write only)
        val notifiedViaWs =
            wsSessionManager.send(
                syncRuntimeInfo.appInstanceId,
                WsEnvelope(type = WsMessageType.NOTIFY_REMOVE),
            )
        if (notifiedViaWs) {
            logger.info { "notifyRemove via WebSocket ${syncRuntimeInfo.appInstanceId}" }
        }

        // Delete local state immediately so UI updates without waiting for remote notification
        wsSessionManager.closeSession(syncRuntimeInfo.appInstanceId)
        secureStore.deleteCryptPublicKey(syncRuntimeInfo.appInstanceId)
        syncRuntimeInfoDao.deleteSyncRuntimeInfo(syncRuntimeInfo.appInstanceId)

        // HTTP fallback notification (best-effort, after local cleanup)
        if (!notifiedViaWs) {
            syncRuntimeInfo.connectHostAddress?.let { host ->
                val hostAndPort = HostAndPort(host, syncRuntimeInfo.port)
                syncClientApi.notifyRemove {
                    buildUrl(hostAndPort)
                }
            }
        }
    }
}
