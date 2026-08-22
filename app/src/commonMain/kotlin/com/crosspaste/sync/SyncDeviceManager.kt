package com.crosspaste.sync

import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.db.sync.SyncRuntimeInfoDao
import com.crosspaste.db.sync.SyncState
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.clientapi.FailureResult
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

class SyncDeviceManager(
    private val pastePullCursorManager: PastePullCursorManager,
    private val pendingExchangeLedger: PendingExchangeLedger,
    private val secureStore: SecureStore,
    private val syncClientApi: SyncClientApi,
    private val syncRuntimeInfoDao: SyncRuntimeInfoDao,
    private val wsSessionManager: WsSessionManager,
) {

    private val logger = KotlinLogging.logger {}

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

    suspend fun exchangeKeysForPairing(
        syncRuntimeInfo: SyncRuntimeInfo,
        generation: Long,
    ) {
        if (syncRuntimeInfo.connectState == SyncState.UNVERIFIED) {
            syncRuntimeInfo.connectHostAddress?.let { host ->
                // The dialog may have been dismissed while this event waited in
                // the resolver queue. Its cancel consumes the ledger entry, so
                // a late warm-up must not create a new responder-side exchange.
                if (pendingExchangeLedger.current(syncRuntimeInfo.appInstanceId) != generation) {
                    logger.info {
                        "exchangeKeysForPairing skipped for ${syncRuntimeInfo.appInstanceId} " +
                            "(exchange cancelled or superseded)"
                    }
                    return
                }
                val hostAndPort = HostAndPort(host, syncRuntimeInfo.port)
                val result =
                    syncClientApi.exchangeKeys(syncRuntimeInfo.appInstanceId, generation) {
                        buildUrl(hostAndPort)
                    }
                if (result is SuccessResult) {
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
     * (#4684). Gated by ownership, not connect state: [generation] was
     * captured from the ledger at the abandon instant, and the cancel is only
     * sent while the ledger still holds that exact generation — a reopened
     * dialog's newer exchange replaces the record, so a stale cancel consumes
     * nothing. Failures are ignored: pairing is being abandoned anyway, and
     * the peer's entry self-heals when a later exchange from us replaces it.
     */
    suspend fun cancelPairing(
        syncRuntimeInfo: SyncRuntimeInfo,
        generation: Long,
    ) {
        val appInstanceId = syncRuntimeInfo.appInstanceId
        val host = syncRuntimeInfo.connectHostAddress ?: return
        if (!pendingExchangeLedger.consume(appInstanceId, generation)) {
            logger.info { "cancelPairing skipped for $appInstanceId (exchange superseded or consumed)" }
            return
        }
        val hostAndPort = HostAndPort(host, syncRuntimeInfo.port)
        val result =
            syncClientApi.trustV2Cancel(generation) {
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

    suspend fun showPairingCode(syncRuntimeInfo: SyncRuntimeInfo): ShowPairingCodeResult {
        if (syncRuntimeInfo.connectState != SyncState.UNVERIFIED) return ShowPairingCodeResult.UNAVAILABLE
        val host = syncRuntimeInfo.connectHostAddress ?: return ShowPairingCodeResult.UNAVAILABLE
        val hostAndPort = HostAndPort(host, syncRuntimeInfo.port)
        val result =
            syncClientApi.showPairingCode {
                buildUrl(hostAndPort)
            }
        return when {
            result is SuccessResult -> {
                logger.info { "showPairingCode success $host ${syncRuntimeInfo.port}" }
                ShowPairingCodeResult.SHOWN
            }
            result is FailureResult &&
                result.exception.getErrorCode().code in
                setOf(
                    StandardErrorCode.REMOTE_SHOW_PAIRING_CODE_DISABLED.getCode(),
                    StandardErrorCode.REMOTE_SHOW_PAIRING_CODE_LOCKED.getCode(),
                ) -> ShowPairingCodeResult.NOT_ACCEPTING
            else -> {
                syncRuntimeInfoDao.updateConnectInfo(
                    syncRuntimeInfo.copy(
                        connectState = SyncState.DISCONNECTED,
                        modifyTime = nowEpochMilliseconds(),
                    ),
                )
                ShowPairingCodeResult.UNAVAILABLE
            }
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
                syncClientApi.notifyExit(syncRuntimeInfo.appInstanceId) {
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

        // HTTP authentication needs the shared key, so notify before deleting local trust state.
        if (!notifiedViaWs) {
            syncRuntimeInfo.connectHostAddress?.let { host ->
                val hostAndPort = HostAndPort(host, syncRuntimeInfo.port)
                syncClientApi.notifyRemove(syncRuntimeInfo.appInstanceId) {
                    buildUrl(hostAndPort)
                }
            }
        }

        wsSessionManager.closeSession(syncRuntimeInfo.appInstanceId)
        secureStore.deleteCryptPublicKey(syncRuntimeInfo.appInstanceId)
        syncRuntimeInfoDao.deleteSyncRuntimeInfo(syncRuntimeInfo.appInstanceId)
        pastePullCursorManager.removeDevice(syncRuntimeInfo.appInstanceId)
    }
}
