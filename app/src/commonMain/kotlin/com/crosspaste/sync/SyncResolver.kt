package com.crosspaste.sync

import com.crosspaste.app.AppInfo
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
import com.crosspaste.net.ws.WsClientConnector
import com.crosspaste.net.ws.WsConnectionPolicy
import com.crosspaste.net.ws.WsSessionManager
import com.crosspaste.platform.Platform
import com.crosspaste.secure.SecureKeyPairSerializer
import com.crosspaste.secure.SecureStore
import com.crosspaste.utils.CryptographyUtils
import com.crosspaste.utils.DateUtils.nowEpochMilliseconds
import com.crosspaste.utils.HostAndPort
import com.crosspaste.utils.StripedMutex
import com.crosspaste.utils.buildUrl
import io.github.oshai.kotlinlogging.KotlinLogging

class SyncResolver(
    private val appInfo: AppInfo,
    private val localPlatform: Platform,
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
    private val wsClientConnector: WsClientConnector,
    private val wsSessionManager: WsSessionManager,
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
                    is SyncEvent.Resolve -> {
                        syncRuntimeInfo.resolve(event.callback)
                    }

                    is SyncEvent.ForceResolve -> {
                        syncRuntimeInfo.forceResolve(event.callback)
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

                    is SyncEvent.ShowPairingCode -> {
                        syncDeviceManager.showPairingCode(syncRuntimeInfo)
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

    // ──────────────────────────────────────────────────────────────
    // Unified resolution: dispatches by current state, completes the
    // full discover → authenticate flow in a single pass to avoid
    // unnecessary intermediate state persistence and event round-trips.
    // ──────────────────────────────────────────────────────────────

    private suspend fun SyncRuntimeInfo.resolve(callback: ResolveCallback) {
        logger.info { "Resolve $appInstanceId (state=$connectState)" }

        // Extensions (e.g. Chrome) are client-only: they connect TO us via WebSocket
        // and have no server to telnet/heartbeat back to. Use WebSocket liveness instead.
        if (platform.isExtension()) {
            resolveExtension(callback)
            return
        }

        when (connectState) {
            SyncState.DISCONNECTED,
            SyncState.INCOMPATIBLE,
            -> {
                discoverAndConnect(callback)
            }

            SyncState.CONNECTING,
            SyncState.UNMATCHED,
            -> {
                connectHostAddress?.let { host ->
                    authenticate(host, connectNetworkPrefixLength, callback)
                } ?: discoverAndConnect(callback)
            }

            SyncState.UNVERIFIED -> {
                resolveUnverified(callback)
            }

            SyncState.CONNECTED -> {
                verifyConnection(callback)
            }
        }
    }

    /**
     * Resolve extension devices (e.g. Chrome Extension).
     * Extensions are client-only — they have no server, no host, no port.
     * Connection liveness is determined solely by WebSocket session state.
     */
    private suspend fun SyncRuntimeInfo.resolveExtension(callback: ResolveCallback) {
        val wsConnected = wsSessionManager.isConnected(appInstanceId)
        if (wsConnected && connectState == SyncState.DISCONNECTED) {
            logger.info { "Extension $appInstanceId WebSocket active, marking CONNECTED" }
            callback.updateVersionRelation(VersionRelation.EQUAL_TO)
            updateConnectState(SyncState.CONNECTED)
        } else if (!wsConnected && connectState == SyncState.CONNECTED) {
            logger.info { "Extension $appInstanceId WebSocket gone, marking DISCONNECTED" }
            updateConnectState(SyncState.DISCONNECTED)
        }
    }

    /**
     * Phase 1: Discover a reachable host and attempt full connection
     * in one pass (telnet + heartbeat without an event round-trip).
     *
     * Uses a fast-then-slow strategy: if a previously connected address
     * exists, try it first with a short timeout. On failure, fall back
     * to probing the full address list with a longer timeout.
     */
    private suspend fun SyncRuntimeInfo.discoverAndConnect(callback: ResolveCallback) {
        val result =
            discoverReachableHost() ?: run {
                logger.info { "$appInstanceId no reachable host, hostInfoList: $hostInfoList" }
                updateConnectState(SyncState.DISCONNECTED)
                return
            }

        val (hostInfo, versionRelation) = result
        callback.updateVersionRelation(versionRelation)

        if (versionRelation != VersionRelation.EQUAL_TO) {
            logger.info { "$appInstanceId version incompatible: $versionRelation" }
            updateConnectState(SyncState.INCOMPATIBLE, hostInfo.hostAddress, hostInfo.networkPrefixLength)
            return
        }

        // Host found and version compatible — set CONNECTING for UI feedback
        logger.info { "$appInstanceId ${hostInfo.hostAddress} to connecting" }
        updateConnectState(SyncState.CONNECTING, hostInfo.hostAddress, hostInfo.networkPrefixLength)

        // Immediately attempt authentication (no event round-trip)
        authenticate(hostInfo.hostAddress, hostInfo.networkPrefixLength, callback)
    }

    /**
     * Phase 2: Attempt authentication via existing key or token cache.
     * Called directly after discovery or when resuming from CONNECTING state.
     */
    private suspend fun SyncRuntimeInfo.authenticate(
        host: String,
        networkPrefixLength: Short?,
        callback: ResolveCallback,
    ) {
        if (secureStore.existCryptPublicKey(appInstanceId)) {
            val state = heartbeat(host, port, appInstanceId, callback)
            when (state) {
                SyncState.CONNECTED -> {
                    logger.info { "heartbeat success $appInstanceId $host $port" }
                    updateConnectState(SyncState.CONNECTED, host, networkPrefixLength)
                    ratingPromptManager.trackSignificantAction()
                    attemptWebSocketUpgrade(host, port)
                }

                SyncState.UNMATCHED -> {
                    logger.info { "heartbeat key mismatch $appInstanceId, re-authenticating" }
                    secureStore.deleteCryptPublicKey(appInstanceId)
                    tryTokenCacheOrUnverified(host, networkPrefixLength)
                }

                SyncState.INCOMPATIBLE -> {
                    updateConnectState(SyncState.INCOMPATIBLE, host, networkPrefixLength)
                }

                else -> {
                    updateConnectState(SyncState.DISCONNECTED)
                }
            }
        } else {
            logger.info { "$appInstanceId no public key, checking token cache" }
            tryTokenCacheOrUnverified(host, networkPrefixLength)
        }
    }

    /**
     * Phase 3: Try QR-scan token cache first; fall back to UNVERIFIED
     * so the user can manually enter a pairing code.
     */
    private suspend fun SyncRuntimeInfo.tryTokenCacheOrUnverified(
        host: String,
        networkPrefixLength: Short?,
    ) {
        if (trustByTokenCache()) {
            logger.info { "trustByTokenCache success $host $port" }
            updateConnectState(SyncState.CONNECTED, host, networkPrefixLength)
            ratingPromptManager.trackSignificantAction()
        } else {
            // Verify host is still reachable before showing UNVERIFIED to user
            telnetHelper.telnet(host, port)?.let { versionRelation ->
                if (versionRelation == VersionRelation.EQUAL_TO) {
                    logger.info { "$appInstanceId to unverified $host $port" }
                    updateConnectState(SyncState.UNVERIFIED, host, networkPrefixLength)
                } else {
                    updateConnectState(SyncState.INCOMPATIBLE, host, networkPrefixLength)
                }
            } ?: updateConnectState(SyncState.DISCONNECTED)
        }
    }

    /**
     * Handle UNVERIFIED state during polling: check if a QR token arrived
     * or if the host is still reachable.
     */
    private suspend fun SyncRuntimeInfo.resolveUnverified(callback: ResolveCallback) {
        connectHostAddress?.let { host ->
            // Check token cache first — user may have scanned QR while waiting
            if (trustByTokenCache()) {
                logger.info { "trustByTokenCache success (from unverified) $host $port" }
                updateConnectState(SyncState.CONNECTED, host, connectNetworkPrefixLength)
                ratingPromptManager.trackSignificantAction()
                return
            }
            // Verify host still reachable; only update DB if state changes
            telnetHelper.telnet(host, port)?.let { versionRelation ->
                callback.updateVersionRelation(versionRelation)
                if (versionRelation != VersionRelation.EQUAL_TO) {
                    updateConnectState(SyncState.INCOMPATIBLE, host, connectNetworkPrefixLength)
                }
                // If still EQUAL_TO, stay UNVERIFIED — no DB write needed
            } ?: updateConnectState(SyncState.DISCONNECTED)
        } ?: updateConnectState(SyncState.DISCONNECTED)
    }

    /**
     * Verify an existing CONNECTED device is still healthy.
     * If a WebSocket session is active, skip the HTTP heartbeat —
     * the WebSocket ping/pong handles liveness.
     */
    private suspend fun SyncRuntimeInfo.verifyConnection(callback: ResolveCallback) {
        if (wsSessionManager.isConnected(appInstanceId)) {
            logger.debug { "WebSocket active for $appInstanceId, skipping HTTP heartbeat" }
            return
        }
        connectHostAddress?.let { host ->
            val state = heartbeat(host, port, appInstanceId, callback)
            when (state) {
                SyncState.CONNECTED -> {
                    // Still connected — no state change needed
                }

                SyncState.UNMATCHED -> {
                    logger.info { "connection key mismatch $appInstanceId, re-authenticating" }
                    secureStore.deleteCryptPublicKey(appInstanceId)
                    tryTokenCacheOrUnverified(host, connectNetworkPrefixLength)
                }

                SyncState.INCOMPATIBLE -> {
                    updateConnectState(SyncState.INCOMPATIBLE, host, connectNetworkPrefixLength)
                }

                else -> {
                    updateConnectState(SyncState.DISCONNECTED)
                }
            }
        } ?: updateConnectState(SyncState.DISCONNECTED)
    }

    private suspend fun SyncRuntimeInfo.forceResolve(callback: ResolveCallback) {
        logger.info { "Force resolve $appInstanceId" }
        refreshSyncInfo(appInstanceId, hostInfoList)
        resolve(callback)
    }

    /**
     * Fast-then-slow host discovery: try the last known address first
     * with a short timeout, then fall back to probing all addresses
     * with a longer timeout.
     */
    private suspend fun SyncRuntimeInfo.discoverReachableHost(): Pair<HostInfo, VersionRelation>? {
        // Fast path: try the previously connected address first
        connectHostAddress?.let { lastHost ->
            val lastHostInfo = hostInfoList.firstOrNull { it.hostAddress == lastHost }
            if (lastHostInfo != null) {
                logger.info { "$appInstanceId fast probe $lastHost:$port" }
                telnetHelper.telnet(lastHost, port, TelnetHelper.FAST_TIMEOUT)?.let { version ->
                    return@discoverReachableHost Pair(lastHostInfo, version)
                }
                logger.info { "$appInstanceId fast probe failed, falling back to full list" }
            }
        }

        // Slow path: try all addresses in parallel with a longer timeout
        return telnetHelper.switchHost(hostInfoList, port, TelnetHelper.SLOW_TIMEOUT)
    }

    // ──────────────────────────────────────────────────────────────
    // WebSocket upgrade (opportunistic, non-blocking)
    // ──────────────────────────────────────────────────────────────

    /**
     * Attempt to upgrade the connection to WebSocket after HTTP CONNECTED.
     * This is a best-effort, non-blocking operation:
     * - If the remote device supports WebSocket, a persistent bidirectional
     *   channel is established, replacing HTTP heartbeat polling.
     * - If the remote is an older version without /ws/sync, the attempt
     *   silently fails and HTTP polling continues as before.
     */
    private fun SyncRuntimeInfo.attemptWebSocketUpgrade(
        host: String,
        port: Int,
    ) {
        if (wsSessionManager.isConnected(appInstanceId)) return

        if (!WsConnectionPolicy.shouldInitiate(
                localAppInstanceId = appInfo.appInstanceId,
                localPlatform = localPlatform,
                remoteAppInstanceId = appInstanceId,
                remotePlatform = platform,
            )
        ) {
            logger.debug { "WebSocket: not initiator for $appInstanceId, waiting for inbound" }
            return
        }

        logger.info { "Attempting WebSocket upgrade to $appInstanceId at $host:$port" }
        wsClientConnector.connectAsync(host, port, appInstanceId)
    }

    // ──────────────────────────────────────────────────────────────
    // Low-level operations (heartbeat, trust, token cache)
    // ──────────────────────────────────────────────────────────────

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

    // ──────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────

    private suspend fun SyncRuntimeInfo.updateConnectState(
        state: Int,
        hostAddress: String? = connectHostAddress,
        networkPrefixLength: Short? = connectNetworkPrefixLength,
    ) {
        syncRuntimeInfoDao.updateConnectInfo(
            this.copy(
                connectHostAddress = hostAddress,
                connectNetworkPrefixLength = networkPrefixLength,
                connectState = state,
                modifyTime = nowEpochMilliseconds(),
            ),
        )
    }

    private fun refreshSyncInfo(
        appInstanceId: String,
        hostInfoList: List<HostInfo>,
    ) {
        lazyPasteBonjourService.value.refreshTarget(appInstanceId, hostInfoList)
    }
}
