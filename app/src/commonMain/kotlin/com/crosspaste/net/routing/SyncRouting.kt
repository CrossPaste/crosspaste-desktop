package com.crosspaste.net.routing

import com.crosspaste.app.AppInfo
import com.crosspaste.app.AppTokenApi
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.dto.secure.KeyExchangeRequest
import com.crosspaste.dto.secure.KeyExchangeResponse
import com.crosspaste.dto.secure.PairingResponse
import com.crosspaste.dto.secure.TrustConfirmRequest
import com.crosspaste.dto.secure.TrustConfirmResponse
import com.crosspaste.dto.secure.TrustRequest
import com.crosspaste.dto.secure.TrustResponse
import com.crosspaste.dto.sync.AuthenticatedControlRequest
import com.crosspaste.dto.sync.ControlOperation
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.NetworkInterfaceService
import com.crosspaste.net.SyncApi
import com.crosspaste.net.SyncInfoFactory
import com.crosspaste.net.SyncInfoHeaderCodec
import com.crosspaste.net.exception.ExceptionHandler
import com.crosspaste.pairing.v3.PairingVersionCoordinator
import com.crosspaste.secure.SecureKeyPairSerializer
import com.crosspaste.secure.SecureStore
import com.crosspaste.sync.NearbyDeviceManager
import com.crosspaste.sync.PendingKeyExchange
import com.crosspaste.sync.PendingKeyExchangeLookup
import com.crosspaste.sync.PendingKeyExchangeStore
import com.crosspaste.utils.CryptographyUtils
import com.crosspaste.utils.DateUtils.nowEpochMilliseconds
import com.crosspaste.utils.HEADER_APP_INSTANCE_ID
import com.crosspaste.utils.HEADER_EXCHANGE_TIMESTAMP
import com.crosspaste.utils.failResponse
import com.crosspaste.utils.getAppInstanceId
import com.crosspaste.utils.successResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*

fun Routing.syncRouting(
    appInfo: AppInfo,
    appTokenApi: AppTokenApi,
    configManager: CommonConfigManager,
    exceptionHandler: ExceptionHandler,
    nearbyDeviceManager: NearbyDeviceManager,
    networkInterfaceService: NetworkInterfaceService,
    pendingKeyExchangeStore: PendingKeyExchangeStore,
    secureKeyPairSerializer: SecureKeyPairSerializer,
    secureStore: SecureStore,
    syncApi: SyncApi,
    syncInfoFactory: SyncInfoFactory,
    syncRoutingApi: SyncRoutingApi,
    trustSyncInfo: (String, String?, SyncInfo?) -> Unit,
    releasePendingKeyExchange: (String) -> Unit,
    pairingVersionCoordinator: PairingVersionCoordinator,
    // No-downgrade rule (pairing v3 design §17.2): while a v3 pairing session is
    // active for a peer, that peer must not be able to fall back to v2 trust.
    hasActivePairingV3Session: (String) -> Boolean = { false },
    openPairingV3AcceptanceWindow: () -> Unit = {},
    controlChallengeStore: ControlChallengeStore = ControlChallengeStore(),
) {
    val logger = KotlinLogging.logger {}

    fun ApplicationCall.clientSyncInfo(): SyncInfo? =
        request.headers[SyncInfoHeaderCodec.HEADER]?.let { encoded ->
            runCatching { SyncInfoHeaderCodec.decodeOrThrow(encoded) }
                .onFailure { e -> logger.warn(e) { "Failed to parse ${SyncInfoHeaderCodec.HEADER} header" } }
                .getOrNull()
        }

    suspend fun validateHeartbeat(
        appInstanceId: String,
        call: ApplicationCall,
    ): Boolean {
        val targetAppInstanceId = call.request.headers["targetAppInstanceId"]
        if (targetAppInstanceId != appInfo.appInstanceId) {
            logger.error { "heartbeat targetAppInstanceId $targetAppInstanceId not match ${appInfo.appInstanceId}" }
            failResponse(call, StandardErrorCode.NOT_MATCH_APP_INSTANCE_ID.toErrorCode())
            return false
        }
        if (!secureStore.existCryptPublicKey(appInstanceId)) {
            logger.error { "heartbeat appInstanceId $appInstanceId not exist crypt public key" }
            failResponse(call, StandardErrorCode.DECRYPT_FAIL.toErrorCode())
            return false
        }
        return true
    }

    get("/sync/heartbeat") {
        getAppInstanceId(call)?.let { appInstanceId ->
            if (validateHeartbeat(appInstanceId, call)) {
                successResponse(call, syncApi.VERSION)
            }
        }
    }

    post("/sync/heartbeat/syncInfo") {
        getAppInstanceId(call)?.let { appInstanceId ->
            if (!validateHeartbeat(appInstanceId, call)) {
                return@let
            }
            if (call.request.headers["secure"] != "1") {
                logger.warn { "Ignoring unauthenticated SyncInfo heartbeat from $appInstanceId" }
                successResponse(call, syncApi.VERSION)
                return@let
            }
            runCatching {
                val receivedSyncInfo = call.receive(SyncInfo::class)
                if (receivedSyncInfo.appInfo.appInstanceId != appInstanceId) {
                    logger.warn { "Binding mismatched SyncInfo identity to authenticated peer $appInstanceId" }
                }
                val syncInfo =
                    receivedSyncInfo.copy(
                        appInfo = receivedSyncInfo.appInfo.copy(appInstanceId = appInstanceId),
                    )
                val host = call.request.host()
                syncRoutingApi.trustSyncInfo(syncInfo, host)
                logger.info { "$appInstanceId heartbeat to ${appInfo.appInstanceId} success" }
            }.onSuccess {
                successResponse(call, syncApi.VERSION)
            }.onFailure { e ->
                if (exceptionHandler.isDecryptFail(e)) {
                    // Key mismatch is an expected protocol state: the peer receives
                    // DECRYPT_FAIL and falls back to re-pairing.
                    logger.warn { "$appInstanceId heartbeat to ${appInfo.appInstanceId} fail: decrypt fail" }
                    failResponse(call, StandardErrorCode.DECRYPT_FAIL.toErrorCode())
                } else {
                    logger.error(e) { "$appInstanceId heartbeat to ${appInfo.appInstanceId} fail" }
                    failResponse(call, StandardErrorCode.UNKNOWN_ERROR.toErrorCode())
                }
            }
        }
    }

    get("/sync/notifyExit") {
        getAppInstanceId(call)?.let {
            logger.warn { "Ignoring unauthenticated legacy notifyExit" }
            successResponse(call)
        }
    }

    get("/sync/notifyRemove") {
        getAppInstanceId(call)?.let {
            logger.warn { "Ignoring unauthenticated legacy notifyRemove" }
            successResponse(call)
        }
    }

    get("/sync/control/challenge") {
        getAppInstanceId(call)?.let { appInstanceId ->
            if (!validateHeartbeat(appInstanceId, call)) {
                return@let
            }
            successResponse(call, controlChallengeStore.issue(appInstanceId))
        }
    }

    suspend fun handleAuthenticatedControl(
        call: ApplicationCall,
        expectedOperation: ControlOperation,
        action: (String) -> Unit,
    ) {
        val appInstanceId = getAppInstanceId(call) ?: return
        if (call.request.headers["secure"] != "1") {
            failResponse(call, StandardErrorCode.DECRYPT_FAIL.toErrorCode())
            return
        }
        if (!validateHeartbeat(appInstanceId, call)) return
        runCatching {
            val request = call.receive<AuthenticatedControlRequest>()
            val valid =
                request.targetAppInstanceId == appInfo.appInstanceId &&
                    request.operation == expectedOperation &&
                    controlChallengeStore.consume(
                        appInstanceId,
                        request.challengeId,
                        request.challengeNonce,
                    )
            if (!valid) {
                failResponse(call, StandardErrorCode.DECRYPT_FAIL.toErrorCode())
                return
            }
            action(appInstanceId)
            successResponse(call)
        }.onFailure { e ->
            logger.warn(e) { "Authenticated control request failed for $appInstanceId" }
            failResponse(call, StandardErrorCode.DECRYPT_FAIL.toErrorCode())
        }
    }

    post("/sync/control/notifyExit") {
        handleAuthenticatedControl(call, ControlOperation.NOTIFY_EXIT, syncRoutingApi::markExit)
    }

    post("/sync/control/notifyRemove") {
        handleAuthenticatedControl(call, ControlOperation.NOTIFY_REMOVE, syncRoutingApi::removeSyncHandler)
    }

    get("/sync/showToken") {
        val appInstanceId = call.request.headers["appInstanceId"]
        val host = call.request.host()
        if (appInstanceId != null) {
            appTokenApi.acquireVerifier(appInstanceId)
        } else {
            appTokenApi.startRefresh(showToken = true)
        }
        logger.info { "show token requested from $host" }
        successResponse(call)
    }

    get("/sync/showPairingCode") {
        val host = call.request.host()
        if (!configManager.getCurrentConfig().enableRemoteShowPairingCode) {
            logger.info { "show pairing code rejected (disabled) from $host" }
            failResponse(call, StandardErrorCode.REMOTE_SHOW_PAIRING_CODE_DISABLED.toErrorCode())
            return@get
        }
        appTokenApi.showPairingCode()
        openPairingV3AcceptanceWindow()
        logger.info { "show pairing code requested from $host" }
        successResponse(call)
    }

    get("/sync/syncInfo") {
        val host = call.request.host()
        val hostInfoList =
            networkInterfaceService
                .getCurrentUseNetworkInterfaces()
                .map { it.toHostInfo() }
                .filter { it.hostAddress == host }
        if (hostInfoList.isEmpty()) {
            logger.debug { "syncInfo request from $host matched no local network interfaces" }
        }
        val syncInfo = syncInfoFactory.createSyncInfo(hostInfoList)
        successResponse(call, syncInfo)
    }

    get("/sync/telnet") {
        // Address push (#4509 phase 3): the probe may carry the caller's subnet-matched
        // SyncInfo so we learn its current address without waiting for the next mDNS
        // round. This is an UNAUTHENTICATED routing hint, so we only honor it for a peer
        // we have already paired with (ECDH crypt key on file) — its purpose is to let a
        // known peer reconnect fast after an IP change. Unknown-peer discovery stays
        // mDNS's job; gating here keeps the in-memory nearby map bounded by the number of
        // real paired devices instead of by attacker-controlled appInstanceId headers.
        // Trust is still granted solely by the ECDH heartbeat.
        call.clientSyncInfo()?.let { syncInfo ->
            if (secureStore.existCryptPublicKey(syncInfo.appInfo.appInstanceId)) {
                nearbyDeviceManager.addDevice(syncInfo)
            }
        }

        // Advertise our identity alongside the version so discovery can vet the peer
        // atomically. Unauthenticated, selection-only (trust is via ECDH); body is
        // unchanged so older clients ignore the extra header. See #4499 / #4500.
        call.response.headers.append(HEADER_APP_INSTANCE_ID, appInfo.appInstanceId)
        successResponse(call, syncApi.VERSION)
    }

    post("/sync/trust") {
        getAppInstanceId(call)?.let { appInstanceId ->
            pairingVersionCoordinator.withPeerLock(appInstanceId) {
                if (hasActivePairingV3Session(appInstanceId)) {
                    logger.warn { "refusing v1 trust during active pairing v3 session for $appInstanceId" }
                    failResponse(call, StandardErrorCode.PAIRING_VERSION_UNSUPPORTED.toErrorCode())
                    return@withPeerLock
                }
                runCatching {
                    val trustRequest = call.receive(TrustRequest::class)
                    val currentTimestamp = nowEpochMilliseconds()

                    val receiveSignPublicKey =
                        secureKeyPairSerializer.decodeSignPublicKey(
                            trustRequest.pairingRequest.signPublicKey,
                        )

                    val verifyResult =
                        CryptographyUtils.verifyPairingRequest(
                            receiveSignPublicKey,
                            trustRequest.pairingRequest,
                            trustRequest.signature,
                        )

                    if (!verifyResult) {
                        logger.warn { "trustRequest verify fail for $appInstanceId" }
                        failResponse(call, StandardErrorCode.SIGN_INVALID.toErrorCode())
                        return@withPeerLock
                    }

                    val sameToken = appTokenApi.sameToken(trustRequest.pairingRequest.token)
                    if (!sameToken) {
                        failResponse(call, StandardErrorCode.TOKEN_INVALID.toErrorCode())
                        return@withPeerLock
                    }

                    secureStore.saveCryptPublicKey(appInstanceId, trustRequest.pairingRequest.cryptPublicKey)

                    val signPublicKey = secureStore.secureKeyPair.getSignPublicKeyBytes(secureKeyPairSerializer)
                    val cryptPublicKey = secureStore.secureKeyPair.getCryptPublicKeyBytes(secureKeyPairSerializer)

                    val pairingResponse =
                        PairingResponse(
                            signPublicKey,
                            cryptPublicKey,
                            currentTimestamp,
                        )

                    TrustResponse(
                        pairingResponse = pairingResponse,
                        signature =
                            CryptographyUtils.signPairingResponse(
                                secureStore.secureKeyPair.signKeyPair.privateKey,
                                pairingResponse,
                            ),
                    )
                }.onSuccess { trustResponse ->
                    val host = call.request.headers["crosspaste-host"]
                    val clientSyncInfo = call.clientSyncInfo()
                    // Atomic release: decrements the refresh count only if this
                    // verifier was still pending (a QR token-cache trust never
                    // requested /sync/showToken, so it has nothing to release).
                    appTokenApi.releaseVerifier(appInstanceId)
                    trustSyncInfo(appInstanceId, host, clientSyncInfo)
                    successResponse(call, trustResponse)
                }.onFailure { e ->
                    logger.error(e) { "Trust request failed for $appInstanceId" }
                    failResponse(call, StandardErrorCode.TRUST_FAIL.toErrorCode())
                }
            }
        }
    }

    post("/sync/trust/v2/exchange") {
        getAppInstanceId(call)?.let { appInstanceId ->
            pairingVersionCoordinator.withPeerLock(appInstanceId) {
                if (hasActivePairingV3Session(appInstanceId)) {
                    logger.warn { "refusing v2 exchange during active pairing v3 session for $appInstanceId" }
                    failResponse(call, StandardErrorCode.PAIRING_VERSION_UNSUPPORTED.toErrorCode())
                    return@withPeerLock
                }
                runCatching {
                    val request = call.receive(KeyExchangeRequest::class)

                    val receiveSignPublicKey =
                        secureKeyPairSerializer.decodeSignPublicKey(request.signPublicKey)

                    val verifyResult =
                        CryptographyUtils.verifyKeyExchangeRequest(
                            receiveSignPublicKey,
                            request,
                        )

                    if (!verifyResult) {
                        logger.warn { "v2 exchange verify fail for $appInstanceId" }
                        failResponse(call, StandardErrorCode.SIGN_INVALID.toErrorCode())
                        return@withPeerLock
                    }

                    val localCryptPublicKey =
                        secureStore.secureKeyPair.getCryptPublicKeyBytes(secureKeyPairSerializer)
                    val localSignPublicKey =
                        secureStore.secureKeyPair.getSignPublicKeyBytes(secureKeyPairSerializer)

                    val sas =
                        CryptographyUtils.computeSAS(
                            localCryptPublicKey,
                            request.cryptPublicKey,
                        )

                    val currentTimestamp = nowEpochMilliseconds()

                    // A repeat exchange from the same peer (initiator warm-up +
                    // real exchange, or a client retry) supersedes its earlier
                    // pending entry: release that entry's resources first so one
                    // confirm brings the counter back to zero and the SAS overlay
                    // auto-closes (#4684).
                    releasePendingKeyExchange(appInstanceId)
                    pendingKeyExchangeStore.put(
                        appInstanceId,
                        PendingKeyExchange(
                            signPublicKey = request.signPublicKey,
                            cryptPublicKey = request.cryptPublicKey,
                            sas = sas,
                            timestamp = currentTimestamp,
                            // The signed request timestamp is the initiator's
                            // generation marker; a client cancel must echo it.
                            generation = request.timestamp,
                        ),
                    )

                    appTokenApi.setSASToken(sas)
                    appTokenApi.acquireVerifier(appInstanceId)

                    val signature =
                        CryptographyUtils.signKeyExchangeResponse(
                            secureStore.secureKeyPair.signKeyPair.privateKey,
                            localSignPublicKey,
                            localCryptPublicKey,
                            currentTimestamp,
                        )

                    KeyExchangeResponse(
                        signPublicKey = localSignPublicKey,
                        cryptPublicKey = localCryptPublicKey,
                        timestamp = currentTimestamp,
                        signature = signature,
                    )
                }.onSuccess { response ->
                    successResponse(call, response)
                }.onFailure { e ->
                    logger.error(e) { "v2 exchange failed for $appInstanceId" }
                    failResponse(call, StandardErrorCode.EXCHANGE_FAIL.toErrorCode())
                }
            }
        }
    }

    post("/sync/trust/v2/confirm") {
        getAppInstanceId(call)?.let { appInstanceId ->
            pairingVersionCoordinator.withPeerLock(appInstanceId) {
                if (hasActivePairingV3Session(appInstanceId)) {
                    releasePendingKeyExchange(appInstanceId)
                    logger.warn { "refusing v2 confirm during active pairing v3 session for $appInstanceId" }
                    failResponse(call, StandardErrorCode.PAIRING_VERSION_UNSUPPORTED.toErrorCode())
                    return@withPeerLock
                }
                runCatching {
                    val request = call.receive(TrustConfirmRequest::class)

                    val pending =
                        when (val lookup = pendingKeyExchangeStore.lookup(appInstanceId)) {
                            is PendingKeyExchangeLookup.Live -> lookup.exchange
                            PendingKeyExchangeLookup.Expired -> {
                                // The expired exchange still owns one token-refresh
                                // count and one pending verifier; release both so the
                                // overlay can close once the peer retries (#4684).
                                releasePendingKeyExchange(appInstanceId)
                                logger.warn { "v2 confirm: pending exchange expired for $appInstanceId" }
                                failResponse(call, StandardErrorCode.EXCHANGE_TIMEOUT.toErrorCode())
                                return@withPeerLock
                            }

                            PendingKeyExchangeLookup.None -> {
                                logger.warn { "v2 confirm: no pending exchange for $appInstanceId" }
                                failResponse(call, StandardErrorCode.EXCHANGE_TIMEOUT.toErrorCode())
                                return@withPeerLock
                            }
                        }

                    val receiveSignPublicKey =
                        secureKeyPairSerializer.decodeSignPublicKey(pending.signPublicKey)

                    val verifyResult =
                        CryptographyUtils.verifyTrustConfirm(
                            receiveSignPublicKey,
                            request,
                        )

                    if (!verifyResult) {
                        logger.warn { "v2 confirm verify fail for $appInstanceId" }
                        failResponse(call, StandardErrorCode.SIGN_INVALID.toErrorCode())
                        return@withPeerLock
                    }

                    secureStore.saveCryptPublicKey(appInstanceId, pending.cryptPublicKey)

                    val currentTimestamp = nowEpochMilliseconds()
                    val signature =
                        CryptographyUtils.signTrustConfirm(
                            secureStore.secureKeyPair.signKeyPair.privateKey,
                            currentTimestamp,
                        )

                    TrustConfirmResponse(
                        timestamp = currentTimestamp,
                        signature = signature,
                    )
                }.onSuccess { response ->
                    val host = call.request.headers["crosspaste-host"]
                    val clientSyncInfo = call.clientSyncInfo()
                    // Consumes the pending exchange and unconditionally releases
                    // its refresh count + verifier — the stored entry, not the
                    // overlay's visibility, owns those resources.
                    releasePendingKeyExchange(appInstanceId)
                    trustSyncInfo(appInstanceId, host, clientSyncInfo)
                    successResponse(call, response)
                }.onFailure { e ->
                    logger.error(e) { "v2 confirm failed for $appInstanceId" }
                    failResponse(call, StandardErrorCode.TRUST_FAIL.toErrorCode())
                }
            }
        }
    }

    // Client-side cancellation of a pending v2 exchange (pairing dialog was
    // closed before confirm). Idempotent: releasing a peer with no pending
    // entry is a no-op. When the caller echoes the exchange timestamp it
    // received (generation marker), only that exact exchange is released — a
    // cancel delayed past a newer exchange from the same peer must not tear
    // down the newer one. Callers without the header (older extension versions)
    // keep the original unconditional-release semantics.
    post("/sync/trust/v2/cancel") {
        getAppInstanceId(call)?.let { appInstanceId ->
            pairingVersionCoordinator.withPeerLock(appInstanceId) {
                val requestedGeneration = call.request.headers[HEADER_EXCHANGE_TIMESTAMP]?.toLongOrNull()
                if (requestedGeneration == null ||
                    pendingKeyExchangeStore.generationMatches(appInstanceId, requestedGeneration)
                ) {
                    releasePendingKeyExchange(appInstanceId)
                    logger.info { "v2 exchange cancelled by $appInstanceId" }
                } else {
                    logger.info { "v2 cancel from $appInstanceId ignored (exchange superseded)" }
                }
                successResponse(call)
            }
        }
    }
}
