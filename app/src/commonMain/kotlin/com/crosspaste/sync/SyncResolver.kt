package com.crosspaste.sync

import com.crosspaste.app.RatingPromptManager
import com.crosspaste.db.sync.HostInfo
import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.db.sync.SyncRuntimeInfoDao
import com.crosspaste.db.sync.SyncState
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.NetworkInterfaceService
import com.crosspaste.net.PasteBonjourService
import com.crosspaste.net.SyncInfoFactory
import com.crosspaste.net.TelnetHelper
import com.crosspaste.net.VersionRelation
import com.crosspaste.net.clientapi.DecryptFail
import com.crosspaste.net.clientapi.EncryptFail
import com.crosspaste.net.clientapi.FailureResult
import com.crosspaste.net.clientapi.SuccessResult
import com.crosspaste.net.clientapi.SyncClientApi
import com.crosspaste.net.filter
import com.crosspaste.secure.SecureStore
import com.crosspaste.utils.DateUtils.nowEpochMilliseconds
import com.crosspaste.utils.HostAndPort
import com.crosspaste.utils.buildUrl
import io.github.oshai.kotlinlogging.KotlinLogging

class SyncResolver(
    private val lazyPasteBonjourService: Lazy<PasteBonjourService>,
    private val networkInterfaceService: NetworkInterfaceService,
    private val ratingPromptManager: RatingPromptManager,
    private val secureStore: SecureStore,
    private val syncClientApi: SyncClientApi,
    private val syncInfoFactory: SyncInfoFactory,
    private val syncRuntimeInfoDao: SyncRuntimeInfoDao,
    private val telnetHelper: TelnetHelper,
    private val tokenCache: TokenCacheApi,
) {

    private val logger = KotlinLogging.logger {}

    suspend fun emitEvent(event: SyncEvent) {
        logger.debug { "Event: $event" }
        runCatching {
            if (event is SyncEvent.SyncRunTimeInfoEvent) {
                // Re-read from DB to avoid stale snapshots.
                // Events are queued in a SharedFlow and may be processed after the DB has changed.
                val syncRuntimeInfo =
                    syncRuntimeInfoDao.getSyncRuntimeInfo(event.syncRuntimeInfo.appInstanceId)
                        ?: return@runCatching

                when (event) {
                    is SyncEvent.ResolveDisconnected -> {
                        syncRuntimeInfo.resolveDisconnected(event.callback)
                    }

                    is SyncEvent.ResolveConnecting -> {
                        syncRuntimeInfo.resolveConnecting(event.callback)
                    }

                    is SyncEvent.ResolveConnection -> {
                        syncRuntimeInfo.resolveConnection(event.callback)
                    }

                    is SyncEvent.ForceResolveConnection -> {
                        syncRuntimeInfo.forceResolveConnection(event.callback)
                    }

                    is SyncEvent.TrustByToken -> {
                        syncRuntimeInfo.trustByToken(event.token, event.callback)
                    }

                    is SyncEvent.UpdateAllowSend -> {
                        syncRuntimeInfo.updateAllowSend(event.allowSend)
                    }

                    is SyncEvent.UpdateAllowReceive -> {
                        syncRuntimeInfo.updateAllowReceive(event.allowReceive)
                    }

                    is SyncEvent.UpdateNoteName -> {
                        syncRuntimeInfo.updateNoteName(event.noteName)
                    }

                    is SyncEvent.ShowToken -> {
                        syncRuntimeInfo.showToken()
                    }

                    is SyncEvent.NotifyExit -> {
                        syncRuntimeInfo.notifyExit()
                    }

                    is SyncEvent.MarkExit -> {
                        syncRuntimeInfo.markExit()
                    }

                    is SyncEvent.RemoveDevice -> {
                        syncRuntimeInfo.removeDevice()
                    }
                }
            } else {
                when (event) {
                    is SyncEvent.RefreshSyncInfo -> {
                        refreshSyncInfo(event.appInstanceId, event.hostInfoList)
                    }

                    is SyncEvent.UpdateSyncInfo -> {
                        updateSyncInfo(event.syncInfo)
                    }

                    is SyncEvent.TrustSyncInfo -> {
                        trustSyncInfo(event.syncInfo, event.host)
                    }

                    else -> {
                        logger.warn { "unknown event $event" }
                    }
                }
            }
        }.onFailure { e ->
            logger.error(e) { "Failed to process event: $event" }
        }.apply {
            if (event is SyncEvent.CallbackEvent) {
                event.callback.onComplete()
            }
        }
    }

    private suspend fun SyncRuntimeInfo.resolveDisconnected(callback: ResolveCallback) {
        logger.info { "Resolve disconnected $appInstanceId" }
        telnetHelper.switchHost(hostInfoList, port)?.let { pair ->
            val (hostInfo, versionRelation) = pair
            logger.info { "$appInstanceId $hostInfo to connecting, versionRelation: $versionRelation" }

            callback.updateVersionRelation(versionRelation)
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
            logger.info { "$appInstanceId to disconnected, hostInfoList: $hostInfoList" }
            syncRuntimeInfoDao.updateConnectInfo(
                this.copy(
                    connectState = SyncState.DISCONNECTED,
                    modifyTime = nowEpochMilliseconds(),
                ),
            )
        }
    }

    private suspend fun SyncRuntimeInfo.resolveConnecting(callback: ResolveCallback) {
        logger.info { "Resolve connecting $appInstanceId" }
        this.connectHostAddress?.let { host ->
            if (secureStore.existCryptPublicKey(appInstanceId)) {
                val state = heartbeat(host, port, appInstanceId, callback)

                when (state) {
                    SyncState.CONNECTED -> {
                        logger.info { "heartbeat success $appInstanceId $host $port" }
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
                            "heartbeat fail and connectState is unmatched, need to re verify $appInstanceId $host $port"
                        }
                        secureStore.deleteCryptPublicKey(appInstanceId)
                        tryUseTokenCache(host, port)
                    }
                    SyncState.INCOMPATIBLE -> {
                        logger.info {
                            "heartbeat success and connectState is incompatible $appInstanceId $host $port"
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
                logger.info { "not exist $appInstanceId public key, need to verify $host $port" }
                tryUseTokenCache(host, port)
            }
        } ?: run {
            logger.info { "$appInstanceId ${platform.name} to disconnected" }
            syncRuntimeInfoDao.updateConnectInfo(
                this.copy(
                    connectState = SyncState.DISCONNECTED,
                    modifyTime = nowEpochMilliseconds(),
                ),
            )
        }
    }

    private suspend fun SyncRuntimeInfo.resolveConnection(callback: ResolveCallback) {
        logger.info { "Resolve connection $appInstanceId" }
        if (connectState == SyncState.DISCONNECTED ||
            connectState == SyncState.INCOMPATIBLE
        ) {
            resolveDisconnected(callback)
        } else {
            resolveConnecting(callback)
        }
    }

    private suspend fun SyncRuntimeInfo.forceResolveConnection(callback: ResolveCallback) {
        logger.info { "Force resolve connection $appInstanceId" }
        refreshSyncInfo(appInstanceId, hostInfoList)
        resolveConnection(callback)
    }

    private suspend fun heartbeat(
        host: String,
        port: Int,
        targetAppInstanceId: String,
        callback: ResolveCallback,
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
                val versionRelation: VersionRelation? = result.getResult()
                versionRelation?.let {
                    callback.updateVersionRelation(versionRelation)
                    if (versionRelation == VersionRelation.EQUAL_TO) {
                        SyncState.CONNECTED
                    } else {
                        SyncState.INCOMPATIBLE
                    }
                } ?: run {
                    logger.info { "heartbeat success but versionRelation is null $host $port" }
                    SyncState.DISCONNECTED
                }
            }

            is FailureResult -> {
                when (val failErrorCode = result.exception.getErrorCode().code) {
                    StandardErrorCode.NOT_MATCH_APP_INSTANCE_ID.getCode() -> {
                        logger.info { "heartbeat return fail state to disconnect $host $port" }
                        SyncState.DISCONNECTED
                    }
                    StandardErrorCode.DECRYPT_FAIL.getCode() -> {
                        logger.info { "heartbeat decrypt fail, state to unmatched $host $port $failErrorCode" }
                        SyncState.UNMATCHED
                    }
                    else -> {
                        logger.info { "heartbeat fail, errorCode: $failErrorCode $host $port" }
                        SyncState.DISCONNECTED
                    }
                }
            }

            is EncryptFail -> {
                logger.info { "heartbeat encrypt fail $host $port" }
                SyncState.UNMATCHED
            }
            is DecryptFail -> {
                logger.info { "heartbeat decrypt fail $host $port" }
                SyncState.UNMATCHED
            }

            else -> {
                logger.info { "heartbeat connect fail, state to disconnected $host $port" }
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
                    syncClientApi.trust(appInstanceId, host, token) {
                        buildUrl(hostAndPort)
                    }

                if (result is SuccessResult) {
                    val hostInfoList =
                        networkInterfaceService
                            .getCurrentUseNetworkInterfaces()
                            .map { it.toHostInfo() }
                            .filter { it.filter(host) }
                    val syncInfo = syncInfoFactory.createSyncInfo(hostInfoList)
                    syncClientApi.heartbeat(
                        syncInfo = syncInfo,
                        targetAppInstanceId = appInstanceId,
                    ) {
                        buildUrl(HostAndPort(host, port))
                    }
                    return@trustByTokenCache true
                }
            }
        }
        return false
    }

    private suspend fun SyncRuntimeInfo.trustByToken(
        token: Int,
        callback: (Boolean) -> Unit,
    ) {
        if (connectState == SyncState.UNVERIFIED) {
            connectHostAddress?.let { host ->
                val hostAndPort = HostAndPort(host, port)
                val result =
                    syncClientApi.trust(appInstanceId, host, token) {
                        buildUrl(hostAndPort)
                    }

                if (result is SuccessResult) {
                    val hostInfoList =
                        networkInterfaceService
                            .getCurrentUseNetworkInterfaces()
                            .map { it.toHostInfo() }
                            .filter { it.filter(host) }
                    val syncInfo = syncInfoFactory.createSyncInfo(hostInfoList)
                    syncClientApi.heartbeat(
                        syncInfo = syncInfo,
                        targetAppInstanceId = appInstanceId,
                    ) {
                        buildUrl(HostAndPort(host, port))
                    }
                    syncRuntimeInfoDao.updateConnectInfo(
                        this.copy(
                            connectState = SyncState.CONNECTED,
                            modifyTime = nowEpochMilliseconds(),
                        ),
                    )
                    callback(true)
                    ratingPromptManager.trackSignificantAction()
                } else {
                    callback(false)
                }
            } ?: callback(false)
        } else {
            callback(false)
        }
    }

    private fun refreshSyncInfo(
        appInstanceId: String,
        hostInfoList: List<HostInfo>,
    ) {
        lazyPasteBonjourService.value.refreshTarget(appInstanceId, hostInfoList)
    }

    private suspend fun updateSyncInfo(syncInfo: SyncInfo) {
        syncRuntimeInfoDao.insertOrUpdateSyncInfo(syncInfo)
    }

    private suspend fun trustSyncInfo(
        syncInfo: SyncInfo,
        host: String,
    ) {
        syncRuntimeInfoDao.insertOrUpdateSyncInfo(syncInfo, host)
    }

    private suspend fun SyncRuntimeInfo.updateAllowSend(allowSend: Boolean) {
        syncRuntimeInfoDao.updateAllowSend(this.copy(allowSend = allowSend))
    }

    private suspend fun SyncRuntimeInfo.updateAllowReceive(allowReceive: Boolean) {
        syncRuntimeInfoDao.updateAllowReceive(this.copy(allowReceive = allowReceive))
    }

    private suspend fun SyncRuntimeInfo.updateNoteName(noteName: String) {
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

    private suspend fun SyncRuntimeInfo.markExit() {
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
