package com.crosspaste.sync

import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.db.sync.SyncRuntimeInfoDao
import com.crosspaste.db.sync.SyncState
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

    suspend fun exchangeKeysForPairing(syncRuntimeInfo: SyncRuntimeInfo) {
        if (syncRuntimeInfo.connectState == SyncState.UNVERIFIED) {
            syncRuntimeInfo.connectHostAddress?.let { host ->
                val hostAndPort = HostAndPort(host, syncRuntimeInfo.port)
                val result =
                    syncClientApi.exchangeKeys(syncRuntimeInfo.appInstanceId) {
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

    suspend fun showPairingCode(syncRuntimeInfo: SyncRuntimeInfo) {
        if (syncRuntimeInfo.connectState == SyncState.UNVERIFIED) {
            syncRuntimeInfo.connectHostAddress?.let { host ->
                val hostAndPort = HostAndPort(host, syncRuntimeInfo.port)
                val result =
                    syncClientApi.showPairingCode {
                        buildUrl(hostAndPort)
                    }
                if (result is SuccessResult) {
                    logger.info { "showPairingCode success $host ${syncRuntimeInfo.port}" }
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
