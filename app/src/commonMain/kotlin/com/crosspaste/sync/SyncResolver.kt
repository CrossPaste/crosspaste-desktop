package com.crosspaste.sync

import com.crosspaste.app.RatingPromptManager
import com.crosspaste.db.sync.HostInfo
import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.db.sync.SyncRuntimeInfoDao
import com.crosspaste.db.sync.SyncState
import com.crosspaste.dto.secure.KeyExchangeResponse
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.NetworkInterfaceService
import com.crosspaste.net.PasteBonjourService
import com.crosspaste.net.SyncApi
import com.crosspaste.net.SyncInfoFactory
import com.crosspaste.net.TelnetHelper
import com.crosspaste.net.VersionRelation
import com.crosspaste.net.clientapi.DecryptFail
import com.crosspaste.net.clientapi.EncryptFail
import com.crosspaste.net.clientapi.FailureResult
import com.crosspaste.net.clientapi.SuccessResult
import com.crosspaste.net.clientapi.SyncClientApi
import com.crosspaste.net.filter
import com.crosspaste.secure.SecureKeyPairSerializer
import com.crosspaste.secure.SecureStore
import com.crosspaste.utils.CryptographyUtils
import com.crosspaste.utils.DateUtils.nowEpochMilliseconds
import com.crosspaste.utils.HostAndPort
import com.crosspaste.utils.StripedMutex
import com.crosspaste.utils.buildUrl
import io.github.oshai.kotlinlogging.KotlinLogging

class SyncResolver(
    private val lazyNearbyDeviceManager: Lazy<NearbyDeviceManager>,
    private val lazyPasteBonjourService: Lazy<PasteBonjourService>,
    private val networkInterfaceService: NetworkInterfaceService,
    private val ratingPromptManager: RatingPromptManager,
    private val secureKeyPairSerializer: SecureKeyPairSerializer,
    private val secureStore: SecureStore,
    private val syncClientApi: SyncClientApi,
    private val syncDeviceManager: SyncDeviceManager,
    private val syncInfoFactory: SyncInfoFactory,
    private val syncRuntimeInfoDao: SyncRuntimeInfoDao,
    private val telnetHelper: TelnetHelper,
    private val tokenCache: TokenCacheApi,
) : SyncResolverApi {

    private val logger = KotlinLogging.logger {}

    private val deviceMutex = StripedMutex()

    private fun getRemotePairingVersion(appInstanceId: String): Int? =
        lazyNearbyDeviceManager.value.nearbySyncInfos.value
            .firstOrNull { it.appInfo.appInstanceId == appInstanceId }
            ?.appInfo
            ?.pairingVersion

    private fun SyncEvent.appInstanceId(): String =
        when (this) {
            is SyncEvent.SyncRunTimeInfoEvent -> syncRuntimeInfo.appInstanceId
            is SyncEvent.RefreshSyncInfo -> appInstanceId
            else -> error("Unknown SyncEvent type: $this")
        }

    override suspend fun emitEvent(event: SyncEvent) {
        logger.debug { "Event: $event" }
        deviceMutex.withLock(event.appInstanceId()) {
            processEvent(event)
        }
    }

    private suspend fun processEvent(event: SyncEvent) {
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
                        syncDeviceManager.updateAllowSend(syncRuntimeInfo, event.allowSend)
                    }

                    is SyncEvent.UpdateAllowReceive -> {
                        syncDeviceManager.updateAllowReceive(syncRuntimeInfo, event.allowReceive)
                    }

                    is SyncEvent.UpdateNoteName -> {
                        syncDeviceManager.updateNoteName(syncRuntimeInfo, event.noteName)
                    }

                    is SyncEvent.ExchangeKeysForPairing -> {
                        syncDeviceManager.exchangeKeysForPairing(syncRuntimeInfo)
                    }

                    is SyncEvent.ShowToken -> {
                        syncDeviceManager.showToken(syncRuntimeInfo)
                    }

                    is SyncEvent.NotifyExit -> {
                        syncDeviceManager.notifyExit(syncRuntimeInfo)
                        event.completionSignal.complete(Unit)
                    }

                    is SyncEvent.MarkExit -> {
                        syncDeviceManager.markExit(syncRuntimeInfo)
                    }

                    is SyncEvent.RemoveDevice -> {
                        syncDeviceManager.removeDevice(syncRuntimeInfo)
                    }
                }
            } else {
                when (event) {
                    is SyncEvent.RefreshSyncInfo -> {
                        refreshSyncInfo(event.appInstanceId, event.hostInfoList)
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
        val remotePairingVersion = getRemotePairingVersion(appInstanceId)
        val skipTokenCache = SyncApi.supportsSASPairing(remotePairingVersion)
        if (!skipTokenCache && trustByTokenCache()) {
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
            val remotePairingVersion = getRemotePairingVersion(appInstanceId)
            if (SyncApi.supportsSASPairing(remotePairingVersion)) {
                trustByTokenV2(token, callback)
            } else {
                trustByTokenV1(token, callback)
            }
        } else {
            callback(false)
        }
    }

    private suspend fun SyncRuntimeInfo.trustByTokenV1(
        token: Int,
        callback: (Boolean) -> Unit,
    ) {
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
    }

    private suspend fun SyncRuntimeInfo.trustByTokenV2(
        token: Int,
        callback: (Boolean) -> Unit,
    ) {
        connectHostAddress?.let { host ->
            val hostAndPort = HostAndPort(host, port)

            // Step 1: Exchange keys
            val exchangeResult =
                syncClientApi.exchangeKeys(appInstanceId) {
                    buildUrl(hostAndPort)
                }

            if (exchangeResult !is SuccessResult) {
                logger.warn { "v2 key exchange failed for $appInstanceId" }
                callback(false)
                return
            }

            val response: KeyExchangeResponse? = exchangeResult.getResult()
            if (response == null) {
                logger.warn { "v2 key exchange response verification failed for $appInstanceId" }
                callback(false)
                return
            }

            // Step 2: Compute local SAS and compare with user-entered token
            val localCryptPublicKey =
                secureStore.secureKeyPair.getCryptPublicKeyBytes(secureKeyPairSerializer)
            val localSAS =
                CryptographyUtils.computeSAS(localCryptPublicKey, response.cryptPublicKey)

            if (token != localSAS) {
                logger.warn { "v2 SAS mismatch for $appInstanceId: expected $localSAS, got $token (possible MITM)" }
                callback(false)
                return
            }

            // Step 3: Confirm trust
            val confirmResult =
                syncClientApi.trustV2Confirm(appInstanceId, host) {
                    buildUrl(hostAndPort)
                }

            if (confirmResult !is SuccessResult) {
                logger.warn { "v2 trust confirm failed for $appInstanceId" }
                callback(false)
                return
            }

            // Step 4: Save remote key locally and send heartbeat
            secureStore.saveCryptPublicKey(appInstanceId, response.cryptPublicKey)

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
        } ?: callback(false)
    }

    private fun refreshSyncInfo(
        appInstanceId: String,
        hostInfoList: List<HostInfo>,
    ) {
        lazyPasteBonjourService.value.refreshTarget(appInstanceId, hostInfoList)
    }
}
