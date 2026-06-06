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
import io.ktor.util.collections.ConcurrentMap
import kotlin.coroutines.cancellation.CancellationException

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

    // Last subnet-matched address set we advertised to each peer over the liveness heartbeat,
    // keyed by appInstanceId. We piggyback our address onto the heartbeat so a peer learns our
    // (possibly changed) return address without waiting for the next mDNS round (#4509), but
    // only when it actually changed — re-posting every cycle would re-run the peer's
    // trustSyncInfo (a DB write) on every heartbeat. A real IP change recomputes a different
    // list, so the next heartbeat re-advertises; any non-success drops the entry so a
    // reconnect re-advertises too. Cleared on MarkExit/RemoveDevice so a gracefully-departed
    // device leaves nothing behind (no heartbeat would ever come to evict it otherwise).
    private val lastHeartbeatAdvertised: MutableMap<String, List<HostInfo>> = ConcurrentMap()

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
        try {
            if (event is SyncEvent.SyncRunTimeInfoEvent) {
                // Re-read from DB to avoid stale snapshots.
                // Events are queued in a SharedFlow and may be processed after the DB has changed.
                val syncRuntimeInfo =
                    syncRuntimeInfoDao.getSyncRuntimeInfo(event.syncRuntimeInfo.appInstanceId)
                        ?: return

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
                        // No more heartbeats will come to clear this on failure; drop it now
                        // so the entry can't linger after a graceful exit (a reconnect starts
                        // with an empty marker and re-advertises, which is what we want).
                        lastHeartbeatAdvertised.remove(syncRuntimeInfo.appInstanceId)
                    }

                    is SyncEvent.RemoveDevice -> {
                        syncDeviceManager.removeDevice(syncRuntimeInfo)
                        lastHeartbeatAdvertised.remove(syncRuntimeInfo.appInstanceId)
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
        } catch (e: CancellationException) {
            // Never swallow cancellation — rethrow so structured concurrency
            // can tear down the resolve pipeline instead of re-processing
            // buffered events in a cancelled scope (which previously produced
            // an unbounded "Failed to process event" loop on shutdown/teardown).
            throw e
        } catch (e: Throwable) {
            logger.error(e) { "Failed to process event: $event" }
        } finally {
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
        // Per-poll entry trace: debug only. At INFO we log just the state transitions
        // (connected / disconnected / incompatible / …), so steady-state polling stays quiet.
        logger.debug { "Resolve $appInstanceId (state=$connectState)" }

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
     * Liveness is verified by sending an in-band Frame.Ping; send failure
     * means the underlying socket is dead.
     */
    private suspend fun SyncRuntimeInfo.resolveExtension(callback: ResolveCallback) {
        when (connectState) {
            SyncState.CONNECTED -> {
                if (!wsSessionManager.probe(appInstanceId)) {
                    logger.info { "Extension $appInstanceId probe failed, marking DISCONNECTED" }
                    updateConnectState(SyncState.DISCONNECTED)
                }
                // probe success → stay CONNECTED
            }
            SyncState.DISCONNECTED -> {
                if (wsSessionManager.probe(appInstanceId)) {
                    logger.info { "Extension $appInstanceId WebSocket back, marking CONNECTED" }
                    callback.updateVersionRelation(VersionRelation.EQUAL_TO)
                    updateConnectState(SyncState.CONNECTED)
                } else {
                    // WS still gone (or half-open); only scheduled polling will actually
                    // escalate backoff (force-resolve / first-value paths pass a no-op
                    // markPollFailure).
                    callback.markPollFailure()
                }
            }
            else -> {
                logger.warn { "Extension $appInstanceId in unexpected state=$connectState" }
            }
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
        // Gate discovery when the machine has no usable local interface (cold-start
        // before the network is up, sleep/wake, cable pulled). Every telnet would fail
        // with UnresolvedAddressException, and the per-poll "no reachable host" line
        // turns into a log storm (#4509 / #4499: 700+ lines in one offline session).
        // Skip probing entirely and stay DISCONNECTED; the NetworkStateMonitor re-emits
        // interfaces the moment connectivity returns, and the next poll resolves cleanly.
        if (networkInterfaceService.getCurrentUseNetworkInterfaces().isEmpty()) {
            logger.debug { "$appInstanceId offline (no local interface), skipping discovery" }
            if (connectState != SyncState.DISCONNECTED) {
                updateConnectState(SyncState.DISCONNECTED)
            }
            return
        }

        val result =
            discoverReachableHost() ?: run {
                // Log + persist only on the transition into DISCONNECTED. Re-writing
                // DISCONNECTED every poll bumps modifyTime, re-emits the DB flow, and
                // churns the handler; re-logging it floods the log. Kept at INFO (not
                // debug) so a user's default-level logs still capture when and why a peer
                // went unreachable — connection issues get reported far more often than
                // anyone will re-run with debug enabled, and the connection layer is new
                // enough that losing this signal would hurt triage.
                if (connectState != SyncState.DISCONNECTED) {
                    logger.info { "$appInstanceId no reachable host, hostInfoList: $hostInfoList" }
                    updateConnectState(SyncState.DISCONNECTED)
                }
                return
            }

        val (hostInfo, versionRelation) = result
        callback.updateVersionRelation(versionRelation)

        if (versionRelation != VersionRelation.EQUAL_TO) {
            // INCOMPATIBLE re-enters discoverAndConnect on every poll; log only on the
            // transition into it so a version mismatch doesn't repeat the same line forever.
            if (connectState != SyncState.INCOMPATIBLE) {
                logger.info { "$appInstanceId version incompatible: $versionRelation" }
            }
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
                    // Persist DISCONNECTED at the address we actually tried, not the
                    // stale snapshot's connectHostAddress. Reverting to the old address
                    // here makes connectHostAddress oscillate (CONNECTING@new ->
                    // DISCONNECTED@stale) within a single resolve pass, which the DB flow
                    // reads as an address change and re-emits Resolve, bypassing backoff
                    // (#4499 / #4500 tight loop).
                    updateConnectState(SyncState.DISCONNECTED, host, networkPrefixLength)
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
            telnetHelper.telnet(host, port)?.versionRelation?.let { versionRelation ->
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
            telnetHelper.telnet(host, port)?.versionRelation?.let { versionRelation ->
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
            // Heartbeat the current address DIRECTLY — it need not be a member of
            // hostInfoList. With Phase B's capacity cap, a still-alive connectHostAddress
            // can be LRU-evicted from hostInfoList once the peer advertises newer ones;
            // keeping liveness checks bound to the address (not list membership) is what
            // makes that boundary safe. Do not change this to "only probe hostInfoList".
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
                    // Active switch (#4499 weakness ①): the current address is unreachable.
                    // Instead of dropping to DISCONNECTED (a visible disconnect, then a
                    // separate reconnect pass), re-discover among the peer's advertised
                    // addresses in the same pass. An IP move resolves as
                    // CONNECTED -> CONNECTING -> CONNECTED with no gap; if nothing is
                    // reachable, discoverAndConnect persists DISCONNECTED itself.
                    logger.info { "$appInstanceId heartbeat failed on $host, switching via discovery" }
                    discoverAndConnect(callback)
                }
            }
        } ?: discoverAndConnect(callback)
    }

    private suspend fun SyncRuntimeInfo.forceResolve(callback: ResolveCallback) {
        logger.info { "Force resolve $appInstanceId" }
        refreshSyncInfo(appInstanceId, hostInfoList)
        resolve(callback)
    }

    /**
     * Recency-first, fast-then-slow host discovery: probe the most-recently-advertised
     * address first with a short timeout (the currently-connected address wins ties),
     * then fall back to racing the full recency-ordered list with a longer timeout.
     *
     * Ordering by lastSeen means a peer that moved to a new IP is reached on the fresh
     * address immediately, instead of wasting the fast timeout on a now-dead old one
     * (#4499 weakness ②). switchHost admits only identity-matching (or identity-unknown)
     * hosts so a ghost on a historical IP is never selected.
     */
    private suspend fun SyncRuntimeInfo.discoverReachableHost(): Pair<HostInfo, VersionRelation>? {
        val ordered =
            hostInfoList.sortedWith(
                compareByDescending<HostInfo> { it.lastSeen }
                    .thenByDescending { it.hostAddress == connectHostAddress },
            )

        // Fast path: probe the freshest address first (skipped when there are none).
        ordered.firstOrNull()?.let { freshest ->
            // Per-poll probe trace stays at debug; the conclusion (connecting / no reachable
            // host) is logged at info by the caller, so dropping this loses nothing for triage.
            logger.debug { "$appInstanceId fast probe ${freshest.hostAddress}:$port (lastSeen=${freshest.lastSeen})" }
            telnetHelper.telnet(freshest.hostAddress, port, TelnetHelper.FAST_TIMEOUT)?.let { telnetResult ->
                if (telnetResult.identityAccepted(appInstanceId)) {
                    return@discoverReachableHost Pair(freshest, telnetResult.versionRelation)
                }
                // INFO: a reachable address answered with the wrong identity (stale/reused IP,
                // ghost peer). This is the only signal that separates "unreachable" from
                // "reached the wrong device", so it must survive at the user's default level.
                logger.info {
                    "$appInstanceId fast probe identity mismatch " +
                        "(${telnetResult.peerAppInstanceId}), skipping ${freshest.hostAddress}"
                }
            }
            logger.debug { "$appInstanceId fast probe failed, falling back to full list" }
        }

        // Slow path: race the recency-ordered list in parallel with a longer timeout.
        return telnetHelper
            .switchHost(ordered, port, appInstanceId, TelnetHelper.SLOW_TIMEOUT)
            ?.let { (hostInfo, telnetResult) -> Pair(hostInfo, telnetResult.versionRelation) }
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

        // Piggyback our current return address onto the heartbeat (#4509). Advertise only the
        // local interface(s) on the peer's subnet — the address it can actually route to — and
        // only when it differs from what we last delivered, so a steady connection tells each
        // peer exactly once and then sends plain GET heartbeats.
        val advertiseHostInfo = subnetMatchedHostInfo(host)
        val advertiseSyncInfo =
            if (advertiseHostInfo.isNotEmpty() && lastHeartbeatAdvertised[targetAppInstanceId] != advertiseHostInfo) {
                syncInfoFactory.createSyncInfo(advertiseHostInfo)
            } else {
                null
            }

        val result =
            syncClientApi.heartbeat(
                syncInfo = advertiseSyncInfo,
                targetAppInstanceId = targetAppInstanceId,
            ) {
                buildUrl(hostAndPort)
            }

        if (result is SuccessResult) {
            // Mark delivered only on success, so a failed heartbeat re-advertises next time.
            advertiseSyncInfo?.let { lastHeartbeatAdvertised[targetAppInstanceId] = advertiseHostInfo }
        } else {
            // Forget on any failure so the next successful heartbeat (e.g. after a reconnect)
            // re-advertises our address to the peer.
            lastHeartbeatAdvertised.remove(targetAppInstanceId)
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
                    advertiseAddressViaHeartbeat(host, port, appInstanceId)
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
                advertiseAddressViaHeartbeat(host, port, appInstanceId)
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

            advertiseAddressViaHeartbeat(host, port, appInstanceId)
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

    /**
     * Our local interface address(es) on [host]'s subnet — the address the peer at [host] can
     * actually route back to. Reuses [HostInfo.filter], the same subnet match the discovery and
     * trust flows use. Empty when no local interface shares the peer's subnet (cross-subnet /
     * offline), in which case we advertise nothing and leave reachability to the next mDNS round.
     */
    private fun subnetMatchedHostInfo(host: String): List<HostInfo> =
        networkInterfaceService
            .getCurrentUseNetworkInterfaces()
            .map { it.toHostInfo() }
            .filter { it.filter(host) }

    /**
     * Heartbeat that always carries our subnet-matched address — used by the trust handshakes,
     * where the peer must receive our [SyncInfo] to finish pairing. Records what we advertised
     * (on success) into [lastHeartbeatAdvertised] so the subsequent steady-state liveness
     * heartbeat doesn't redundantly re-send the same address right after a fresh pairing.
     */
    private suspend fun advertiseAddressViaHeartbeat(
        host: String,
        port: Int,
        appInstanceId: String,
    ) {
        val advertiseHostInfo = subnetMatchedHostInfo(host)
        val result =
            syncClientApi.heartbeat(
                syncInfo = syncInfoFactory.createSyncInfo(advertiseHostInfo),
                targetAppInstanceId = appInstanceId,
            ) {
                buildUrl(HostAndPort(host, port))
            }
        if (result is SuccessResult) {
            lastHeartbeatAdvertised[appInstanceId] = advertiseHostInfo
        }
    }
}
