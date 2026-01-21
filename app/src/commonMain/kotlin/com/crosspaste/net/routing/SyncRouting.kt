package com.crosspaste.net.routing

import com.crosspaste.app.AppInfo
import com.crosspaste.app.AppTokenApi
import com.crosspaste.dto.secure.PairingResponse
import com.crosspaste.dto.secure.TrustRequest
import com.crosspaste.dto.secure.TrustResponse
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.NetworkInterfaceService
import com.crosspaste.net.SyncApi
import com.crosspaste.net.SyncInfoFactory
import com.crosspaste.net.exception.ExceptionHandler
import com.crosspaste.secure.SecureKeyPairSerializer
import com.crosspaste.secure.SecureStore
import com.crosspaste.utils.CryptographyUtils
import com.crosspaste.utils.DateUtils.nowEpochMilliseconds
import com.crosspaste.utils.failResponse
import com.crosspaste.utils.getAppInstanceId
import com.crosspaste.utils.successResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.request.*
import io.ktor.server.routing.*

fun Routing.syncRouting(
    appInfo: AppInfo,
    appTokenApi: AppTokenApi,
    exceptionHandler: ExceptionHandler,
    networkInterfaceService: NetworkInterfaceService,
    secureKeyPairSerializer: SecureKeyPairSerializer,
    secureStore: SecureStore,
    syncApi: SyncApi,
    syncInfoFactory: SyncInfoFactory,
    syncRoutingApi: SyncRoutingApi,
    trustSyncInfo: (String, String?) -> Unit,
) {
    val logger = KotlinLogging.logger {}

    get("/sync/heartbeat") {
        getAppInstanceId(call)?.let { appInstanceId ->
            val targetAppInstanceId = call.request.headers["targetAppInstanceId"]
            if (targetAppInstanceId != appInfo.appInstanceId) {
                logger.error { "heartbeat targetAppInstanceId $targetAppInstanceId not match ${appInfo.appInstanceId}" }
                failResponse(call, StandardErrorCode.NOT_MATCH_APP_INSTANCE_ID.toErrorCode())
            } else if (!secureStore.existCryptPublicKey(appInstanceId)) {
                logger.error { "heartbeat appInstanceId $appInstanceId not exist crypt public key" }
                failResponse(call, StandardErrorCode.DECRYPT_FAIL.toErrorCode())
            } else {
                successResponse(call, syncApi.VERSION)
            }
        }
    }

    post("/sync/heartbeat/syncInfo") {
        getAppInstanceId(call)?.let { appInstanceId ->
            val targetAppInstanceId = call.request.headers["targetAppInstanceId"]
            if (targetAppInstanceId != appInfo.appInstanceId) {
                logger.error { "heartbeat targetAppInstanceId $targetAppInstanceId not match ${appInfo.appInstanceId}" }
                failResponse(call, StandardErrorCode.NOT_MATCH_APP_INSTANCE_ID.toErrorCode())
            } else if (!secureStore.existCryptPublicKey(appInstanceId)) {
                logger.error { "heartbeat appInstanceId $appInstanceId not exist crypt public key" }
                failResponse(call, StandardErrorCode.DECRYPT_FAIL.toErrorCode())
            } else {
                runCatching {
                    val syncInfo = call.receive(SyncInfo::class)
                    syncRoutingApi.updateSyncInfo(syncInfo)
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
        appTokenApi.startRefresh(showToken = true)
        logger.debug { "show token" }
        successResponse(call)
    }

    get("/sync/syncInfo") {
        val host = call.request.host()
        val hostInfoList =
            networkInterfaceService
                .getCurrentUseNetworkInterfaces()
                .map { it.toHostInfo() }
                .filter { it.hostAddress == host }
        val syncInfo = syncInfoFactory.createSyncInfo(hostInfoList)
        successResponse(call, syncInfo)
    }

    get("/sync/telnet") {
        successResponse(call, syncApi.VERSION)
    }

    post("/sync/trust") {
        getAppInstanceId(call)?.let { appInstanceId ->
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
                    logger.debug { "trustRequest verify fail" }
                    failResponse(call, StandardErrorCode.SIGN_INVALID.toErrorCode())
                    return@post
                }

                val sameToken = appTokenApi.sameToken(trustRequest.pairingRequest.token)
                if (!sameToken) {
                    failResponse(call, StandardErrorCode.TOKEN_INVALID.toErrorCode())
                    return@post
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
                val host = call.request.headers["host"]
                trustSyncInfo(appInstanceId, host)
                successResponse(call, trustResponse)
            }.onFailure {
                failResponse(call, StandardErrorCode.TRUST_FAIL.toErrorCode())
            }
        }
    }
}
