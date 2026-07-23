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
import com.crosspaste.sync.PendingKeyExchangeStore
import com.crosspaste.utils.CryptographyUtils
import com.crosspaste.utils.DateUtils.nowEpochMilliseconds
import com.crosspaste.utils.HEADER_APP_INSTANCE_ID
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
    pairingVersionCoordinator: PairingVersionCoordinator,
    // No-downgrade rule (pairing v3 design §17.2): while a v3 pairing session is
    // active for a peer, that peer must not be able to fall back to v2 trust.
    hasActivePairingV3Session: (String) -> Boolean = { false },
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
            runCatching {
                val syncInfo = call.receive(SyncInfo::class)
                val host = call.request.host()
                syncRoutingApi.trustSyncInfo(syncInfo, host)
                logger.info { "$appInstanceId heartbeat to ${appInfo.appInstanceId} success" }
            }.onSuccess {
                successResponse(call, syncApi.VERSION)
            }.onFailure { e ->
                logger.error(e) { "$appInstanceId heartbeat to ${appInfo.appInstanceId} fail" }
                if (exceptionHandler.isDecryptFail(e)) {
                    failResponse(call, StandardErrorCode.DECRYPT_FAIL.toErrorCode())
                } else {
                    failResponse(call, StandardErrorCode.UNKNOWN_ERROR.toErrorCode())
                }
            }
        }
    }

    get("/sync/notifyExit") {
        getAppInstanceId(call)?.let { appInstanceId ->
            syncRoutingApi.markExit(appInstanceId)
            successResponse(call)
        }
    }

    get("/sync/notifyRemove") {
        getAppInstanceId(call)?.let { appInstanceId ->
            syncRoutingApi.removeSyncHandler(appInstanceId)
            successResponse(call)
        }
    }

    get("/sync/showToken") {
        val appInstanceId = call.request.headers["appInstanceId"]
        val host = call.request.host()
        if (appInstanceId != null) {
            appTokenApi.addPendingVerifier(appInstanceId)
        }
        appTokenApi.startRefresh(showToken = true)
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
                    appTokenApi.removePendingVerifier(appInstanceId)
                    trustSyncInfo(appInstanceId, host, clientSyncInfo)
                    if (appTokenApi.showToken.value) {
                        appTokenApi.stopRefresh(hideToken = false)
                    }
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

                    pendingKeyExchangeStore.put(
                        appInstanceId,
                        PendingKeyExchange(
                            signPublicKey = request.signPublicKey,
                            cryptPublicKey = request.cryptPublicKey,
                            sas = sas,
                            timestamp = currentTimestamp,
                        ),
                    )

                    appTokenApi.setSASToken(sas)
                    appTokenApi.addPendingVerifier(appInstanceId)
                    appTokenApi.startRefresh(showToken = true)

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
                    pendingKeyExchangeStore.remove(appInstanceId)
                    logger.warn { "refusing v2 confirm during active pairing v3 session for $appInstanceId" }
                    failResponse(call, StandardErrorCode.PAIRING_VERSION_UNSUPPORTED.toErrorCode())
                    return@withPeerLock
                }
                runCatching {
                    val request = call.receive(TrustConfirmRequest::class)

                    val pending = pendingKeyExchangeStore.get(appInstanceId)
                    if (pending == null) {
                        logger.warn { "v2 confirm: no pending exchange for $appInstanceId" }
                        failResponse(call, StandardErrorCode.EXCHANGE_TIMEOUT.toErrorCode())
                        return@withPeerLock
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

                    pendingKeyExchangeStore.remove(appInstanceId)

                    TrustConfirmResponse(
                        timestamp = currentTimestamp,
                        signature = signature,
                    )
                }.onSuccess { response ->
                    val host = call.request.headers["crosspaste-host"]
                    val clientSyncInfo = call.clientSyncInfo()
                    appTokenApi.removePendingVerifier(appInstanceId)
                    trustSyncInfo(appInstanceId, host, clientSyncInfo)
                    if (appTokenApi.showToken.value) {
                        appTokenApi.stopRefresh(hideToken = false)
                    }
                    successResponse(call, response)
                }.onFailure { e ->
                    logger.error(e) { "v2 confirm failed for $appInstanceId" }
                    failResponse(call, StandardErrorCode.TRUST_FAIL.toErrorCode())
                }
            }
        }
    }
}
