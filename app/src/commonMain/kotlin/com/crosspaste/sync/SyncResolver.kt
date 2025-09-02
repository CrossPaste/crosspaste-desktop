package com.crosspaste.sync

import com.crosspaste.app.RatingPromptManager
import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.db.sync.SyncRuntimeInfoDao
import com.crosspaste.db.sync.SyncState
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.PasteBonjourService
import com.crosspaste.net.TelnetHelper
import com.crosspaste.net.VersionRelation
import com.crosspaste.net.clientapi.DecryptFail
import com.crosspaste.net.clientapi.EncryptFail
import com.crosspaste.net.clientapi.FailureResult
import com.crosspaste.net.clientapi.SuccessResult
import com.crosspaste.net.clientapi.SyncClientApi
import com.crosspaste.secure.SecureStore
import com.crosspaste.utils.DateUtils.nowEpochMilliseconds
import com.crosspaste.utils.HostAndPort
import com.crosspaste.utils.buildUrl
import io.github.oshai.kotlinlogging.KotlinLogging

class SyncResolver(
    private val lazyPasteBonjourService: Lazy<PasteBonjourService>,
    private val ratingPromptManager: RatingPromptManager,
    private val secureStore: SecureStore,
    private val syncClientApi: SyncClientApi,
    private val syncRuntimeInfoDao: SyncRuntimeInfoDao,
    private val telnetHelper: TelnetHelper,
    private val tokenCache: TokenCache,
) {

    private val logger = KotlinLogging.logger {}

    suspend fun emitEvent(event: SyncEvent) {
        logger.debug { "Event: $event" }
        runCatching {
            when (event) {
                is SyncEvent.ResolveDisconnected -> {
                    event.syncRuntimeInfo.resolveDisconnected(event.updateVersionRelation)
                }

                is SyncEvent.ResolveConnecting -> {
                    event.syncRuntimeInfo.resolveConnecting(event.updateVersionRelation)
                }

                is SyncEvent.ResolveConnection -> {
                    event.syncRuntimeInfo.resolveConnection(event.updateVersionRelation)
                }

                is SyncEvent.ForceResolveConnection -> {
                    event.syncRuntimeInfo.forceResolveConnection(event.updateVersionRelation)
                }

                is SyncEvent.TrustByToken -> {
                    event.syncRuntimeInfo.trustByToken(event.token)
                }

                is SyncEvent.RefreshSyncInfo -> {
                    refreshSyncInfo(event.appInstanceId)
                }

                is SyncEvent.UpdateSyncInfo -> {
                    updateSyncInfo(event.syncInfo)
                }

                is SyncEvent.UpdateAllowSend -> {
                    event.syncRuntimeInfo.updateAllowSend(event.allowSend)
                }

                is SyncEvent.UpdateAllowReceive -> {
                    event.syncRuntimeInfo.updateAllowReceive(event.allowReceive)
                }

                is SyncEvent.UpdateNoteName -> {
                    event.syncRuntimeInfo.updateNoteName(event.noteName)
                }

                is SyncEvent.ShowToken -> {
                    event.syncRuntimeInfo.showToken()
                }

                is SyncEvent.NotifyExit -> {
                    event.syncRuntimeInfo.notifyExit()
                }

                is SyncEvent.MarkExit -> {
                    event.syncRuntimeInfo.markExit()
                }

                is SyncEvent.RemoveDevice -> {
                    event.syncRuntimeInfo.removeDevice()
                }

                else -> {
                    logger.warn { "unknown event $event" }
                }
            }
        }
    }

    private suspend fun SyncRuntimeInfo.resolveDisconnected(updateVersionRelation: (VersionRelation) -> Unit) {
        logger.info { "Resolve disconnected ${this.appInstanceId}" }
        telnetHelper.switchHost(hostInfoList, port)?.let { pair ->
            val (hostInfo, versionRelation) = pair
            logger.info { "$hostInfo to connecting, versionRelation: $versionRelation" }

            updateVersionRelation(versionRelation)
            val isEqualVersion = versionRelation == VersionRelation.EQUAL_TO

            if (isEqualVersion) {
                syncRuntimeInfoDao.updateConnectInfo(
                    this.copy(
                        connectHostAddress = hostInfo.hostAddress,
                        connectNetworkPrefixLength = hostInfo.networkPrefixLength,
                        connectState = SyncState.CONNECTING,
                        modifyTime = nowEpochMilliseconds(),
                    ),
                )
            } else {
                syncRuntimeInfoDao.updateConnectInfo(
                    this.copy(
                        connectHostAddress = hostInfo.hostAddress,
                        connectNetworkPrefixLength = hostInfo.networkPrefixLength,
                        connectState = SyncState.INCOMPATIBLE,
                        modifyTime = nowEpochMilliseconds(),
                    ),
                )
            }
        } ?: run {
            logger.info { "${this.appInstanceId} to disconnected" }
            syncRuntimeInfoDao.updateConnectInfo(
                this.copy(
                    connectHostAddress = null,
                    connectNetworkPrefixLength = null,
                    connectState = SyncState.DISCONNECTED,
                    modifyTime = nowEpochMilliseconds(),
                ),
            )
        }
    }

    private suspend fun SyncRuntimeInfo.resolveConnecting(updateVersionRelation: (VersionRelation) -> Unit) {
        logger.info { "Resolve connecting ${this.appInstanceId}" }
        this.connectHostAddress?.let { host ->
            if (secureStore.existCryptPublicKey(appInstanceId)) {
                val state = heartbeat(host, port, appInstanceId, updateVersionRelation)

                when (state) {
                    SyncState.CONNECTED -> {
                        logger.info { "heartbeat success $host $port" }
                        syncRuntimeInfoDao.updateConnectInfo(
                            this.copy(
                                connectState = SyncState.CONNECTED,
                                modifyTime = nowEpochMilliseconds(),
                            ),
                        )
                        // track significant action
                        ratingPromptManager.trackSignificantAction()
                        return@resolveConnecting
                    }
                    SyncState.UNMATCHED -> {
                        logger.info {
                            "heartbeat fail and connectState is unmatched, need to re verify $host $port"
                        }
                        secureStore.deleteCryptPublicKey(appInstanceId)
                        tryUseTokenCache(host, port)
                    }
                    SyncState.INCOMPATIBLE -> {
                        logger.info {
                            "heartbeat success and connectState is incompatible $host $port"
                        }
                        syncRuntimeInfoDao.updateConnectInfo(
                            this.copy(
                                connectState = SyncState.INCOMPATIBLE,
                                modifyTime = nowEpochMilliseconds(),
                            ),
                        )
                    }

                    else -> {
                        syncRuntimeInfoDao.updateConnectInfo(
                            this.copy(
                                connectState = SyncState.DISCONNECTED,
                                modifyTime = nowEpochMilliseconds(),
                            ),
                        )
                    }
                }
            } else {
                logger.info { "not exist identity, need to verify $host $port" }
                tryUseTokenCache(host, port)
            }
        } ?: run {
            logger.info { "${platform.name} to disconnected" }
            syncRuntimeInfoDao.updateConnectInfo(
                this.copy(
                    connectState = SyncState.DISCONNECTED,
                    modifyTime = nowEpochMilliseconds(),
                ),
            )
        }
    }

    private suspend fun SyncRuntimeInfo.resolveConnection(updateVersionRelation: (VersionRelation) -> Unit) {
        logger.info { "Resolve connection ${this.appInstanceId}" }
        if (connectState == SyncState.DISCONNECTED ||
            connectState == SyncState.INCOMPATIBLE ||
            connectHostAddress == null
        ) {
            resolveDisconnected(updateVersionRelation)
        } else {
            resolveConnecting(updateVersionRelation)
        }
    }

    private suspend fun SyncRuntimeInfo.forceResolveConnection(updateVersionRelation: (VersionRelation) -> Unit) {
        logger.info { "Force resolve connection ${this.appInstanceId}" }
        refreshSyncInfo(appInstanceId)
        resolveConnection(updateVersionRelation)
    }

    private suspend fun heartbeat(
        host: String,
        port: Int,
        targetAppInstanceId: String,
        updateVersionRelation: (VersionRelation) -> Unit,
    ): Int {
        val hostAndPort = HostAndPort(host, port)
        val result =
            syncClientApi.heartbeat(
                targetAppInstanceId = targetAppInstanceId,
            ) {
                buildUrl(hostAndPort)
            }

        return when (result) {
            is SuccessResult -> {
                val versionRelation: VersionRelation = result.getResult()
                updateVersionRelation(versionRelation)
                if (versionRelation == VersionRelation.EQUAL_TO) {
                    SyncState.CONNECTED
                } else {
                    SyncState.INCOMPATIBLE
                }
            }

            is FailureResult -> {
                when (val failErrorCode = result.exception.getErrorCode().code) {
                    StandardErrorCode.NOT_MATCH_APP_INSTANCE_ID.getCode() -> {
                        logger.info { "heartbeat return fail state to disconnect $host $port" }
                        SyncState.DISCONNECTED
                    }
                    StandardErrorCode.DECRYPT_FAIL.getCode() -> {
                        logger.info { "exchangeSyncInfo return fail state to unmatched $host $port $failErrorCode" }
                        SyncState.UNMATCHED
                    }
                    else -> {
                        logger.info { "failErrorCode $failErrorCode $host $port" }
                        SyncState.DISCONNECTED
                    }
                }
            }

            is EncryptFail -> {
                logger.info { "exchangeSyncInfo encrypt fail $host $port" }
                SyncState.UNMATCHED
            }
            is DecryptFail -> {
                logger.info { "exchangeSyncInfo decrypt fail $host $port" }
                SyncState.UNMATCHED
            }

            else -> {
                logger.info { "exchangeSyncInfo connect fail state to disconnected $host $port" }
                SyncState.DISCONNECTED
            }
        }
    }

    private suspend fun SyncRuntimeInfo.tryUseTokenCache(
        host: String,
        port: Int,
    ) {
        if (trustByTokenCache()) {
            logger.info { "trustByTokenCache success $host $port" }
            syncRuntimeInfoDao.updateConnectInfo(
                this.copy(
                    connectState = SyncState.CONNECTED,
                    modifyTime = nowEpochMilliseconds(),
                ),
            )
            ratingPromptManager.trackSignificantAction()
        } else {
            connectHostAddress?.let {
                telnetHelper.telnet(it, port)?.let { versionRelation ->
                    val versionRelation = versionRelation
                    if (versionRelation == VersionRelation.EQUAL_TO) {
                        logger.info { "telnet success $host $port" }
                        syncRuntimeInfoDao.updateConnectInfo(
                            this.copy(
                                connectState = SyncState.UNVERIFIED,
                                modifyTime = nowEpochMilliseconds(),
                            ),
                        )
                    } else {
                        logger.info { "telnet fail $host $port" }
                        syncRuntimeInfoDao.updateConnectInfo(
                            this.copy(
                                connectState = SyncState.INCOMPATIBLE,
                                modifyTime = nowEpochMilliseconds(),
                            ),
                        )
                    }
                    return@tryUseTokenCache
                }
            }
            syncRuntimeInfoDao.updateConnectInfo(
                this.copy(
                    connectState = SyncState.DISCONNECTED,
                    modifyTime = nowEpochMilliseconds(),
                ),
            )
        }
    }

    // try to use camera to scan token to trust
    private suspend fun SyncRuntimeInfo.trustByTokenCache(): Boolean {
        tokenCache.getToken(appInstanceId)?.let { token ->
            connectHostAddress?.let { host ->
                val hostAndPort = HostAndPort(host, port)
                val result =
                    syncClientApi.trust(appInstanceId, token) {
                        buildUrl(hostAndPort)
                    }

                if (result is SuccessResult) {
                    return@trustByTokenCache true
                }
            }
        }
        return false
    }

    private suspend fun SyncRuntimeInfo.trustByToken(token: Int) {
        if (connectState == SyncState.UNVERIFIED) {
            connectHostAddress?.let { host ->
                val hostAndPort = HostAndPort(host, port)
                val result =
                    syncClientApi.trust(appInstanceId, token) {
                        buildUrl(hostAndPort)
                    }

                if (result is SuccessResult) {
                    syncRuntimeInfoDao.updateConnectInfo(
                        this.copy(
                            connectState = SyncState.CONNECTED,
                            modifyTime = nowEpochMilliseconds(),
                        ),
                    )
                    ratingPromptManager.trackSignificantAction()
                }
            }
        }
    }

    private fun refreshSyncInfo(appInstanceId: String) {
        lazyPasteBonjourService.value.request(appInstanceId)
    }

    private fun updateSyncInfo(syncInfo: SyncInfo) {
        syncRuntimeInfoDao.insertOrUpdateSyncInfo(syncInfo)
    }

    private fun SyncRuntimeInfo.updateAllowSend(allowSend: Boolean) {
        syncRuntimeInfoDao.updateAllowSend(this.copy(allowSend = allowSend))
    }

    private fun SyncRuntimeInfo.updateAllowReceive(allowReceive: Boolean) {
        syncRuntimeInfoDao.updateAllowReceive(this.copy(allowReceive = allowReceive))
    }

    private fun SyncRuntimeInfo.updateNoteName(noteName: String) {
        syncRuntimeInfoDao.updateNoteName(this.copy(noteName = noteName))
    }

    private suspend fun SyncRuntimeInfo.showToken() {
        if (connectState == SyncState.UNVERIFIED) {
            connectHostAddress?.let { host ->
                val hostAndPort = HostAndPort(host, port)
                val result =
                    syncClientApi.showToken {
                        buildUrl(hostAndPort)
                    }
                if (result is SuccessResult) {
                    logger.info { "showToken success $host $port" }
                } else {
                    syncRuntimeInfoDao.updateConnectInfo(
                        this.copy(
                            connectState = SyncState.DISCONNECTED,
                            modifyTime = nowEpochMilliseconds(),
                        ),
                    )
                }
            }
        }
    }

    private suspend fun SyncRuntimeInfo.notifyExit() {
        if (connectState == SyncState.CONNECTED) {
            connectHostAddress?.let { host ->
                val hostAndPort = HostAndPort(host, port)
                syncClientApi.notifyExit {
                    buildUrl(hostAndPort)
                }
            }
        }
    }

    private fun SyncRuntimeInfo.markExit() {
        logger.info { "markExit $appInstanceId" }
        syncRuntimeInfoDao.updateConnectInfo(
            this.copy(
                connectState = SyncState.DISCONNECTED,
                modifyTime = nowEpochMilliseconds(),
            ),
        )
    }

    private suspend fun SyncRuntimeInfo.removeDevice() {
        secureStore.deleteCryptPublicKey(appInstanceId)
        syncRuntimeInfoDao.deleteSyncRuntimeInfo(appInstanceId)
        connectHostAddress?.let { host ->
            val hostAndPort = HostAndPort(host, port)
            syncClientApi.notifyRemove {
                buildUrl(hostAndPort)
            }
        }
    }
}
