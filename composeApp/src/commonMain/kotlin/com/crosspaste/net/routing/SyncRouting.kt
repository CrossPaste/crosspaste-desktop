package com.crosspaste.net.routing

import com.crosspaste.app.AppInfo
import com.crosspaste.app.AppTokenApi
import com.crosspaste.app.EndpointInfoFactory
import com.crosspaste.dto.secure.PairingResponse
import com.crosspaste.dto.secure.TrustRequest
import com.crosspaste.dto.secure.TrustResponse
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.SyncApi
import com.crosspaste.net.exception.ExceptionHandler
import com.crosspaste.secure.SecureKeyPairSerializer
import com.crosspaste.secure.SecureStore
import com.crosspaste.utils.CryptographyUtils
import com.crosspaste.utils.failResponse
import com.crosspaste.utils.getAppInstanceId
import com.crosspaste.utils.successResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlin.math.abs

fun Routing.syncRouting(
    appInfo: AppInfo,
    appTokenApi: AppTokenApi,
    endpointInfoFactory: EndpointInfoFactory,
    exceptionHandler: ExceptionHandler,
    secureKeyPairSerializer: SecureKeyPairSerializer,
    secureStore: SecureStore,
    syncApi: SyncApi,
    syncRoutingApi: SyncRoutingApi,
) {
    val logger = KotlinLogging.logger {}

    get("/sync/heartbeat") {
        getAppInstanceId(call)?.let { appInstanceId ->
            val targetAppInstanceId = call.request.headers["targetAppInstanceId"]
            if (targetAppInstanceId != appInfo.appInstanceId) {
                logger.debug { "heartbeat targetAppInstanceId $targetAppInstanceId not match ${appInfo.appInstanceId}" }
                failResponse(call, StandardErrorCode.SYNC_NOT_MATCH_APP_INSTANCE_ID.toErrorCode())
                return@let
            }
            successResponse(call)
        }
    }

    post("/sync/heartbeat/syncInfo") {
        getAppInstanceId(call)?.let { appInstanceId ->
            val targetAppInstanceId = call.request.headers["targetAppInstanceId"]
            if (targetAppInstanceId != appInfo.appInstanceId) {
                logger.debug { "heartbeat targetAppInstanceId $targetAppInstanceId not match ${appInfo.appInstanceId}" }
                failResponse(call, StandardErrorCode.SYNC_NOT_MATCH_APP_INSTANCE_ID.toErrorCode())
                return@let
            }

            try {
                val syncInfo = call.receive(SyncInfo::class)
                syncRoutingApi.updateSyncInfo(syncInfo)
                logger.debug { "$appInstanceId heartbeat to ${appInfo.appInstanceId} success" }
                successResponse(call)
            } catch (e: Exception) {
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
        appTokenApi.toShowToken()
        logger.debug { "show token" }
        successResponse(call)
    }

    get("/sync/syncInfo") {
        val endpointInfo = endpointInfoFactory.createEndpointInfo()
        val syncInfo = SyncInfo(appInfo, endpointInfo)
        successResponse(call, syncInfo)
    }

    get("/sync/telnet") {
        successResponse(call, syncApi.VERSION)
    }

    post("/sync/trust") {
        getAppInstanceId(call)?.let { appInstanceId ->
            try {
                val trustRequest = call.receive(TrustRequest::class)
                val currentTimestamp = Clock.System.now().toEpochMilliseconds()

                if (abs(currentTimestamp - trustRequest.pairingRequest.timestamp) > 5000) {
                    logger.debug { "trustRequest timeout" }
                    failResponse(call, StandardErrorCode.TRUST_TIMEOUT.toErrorCode())
                    return@post
                }

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

                val trustResponse =
                    TrustResponse(
                        pairingResponse = pairingResponse,
                        signature =
                            CryptographyUtils.signPairingResponse(
                                secureStore.secureKeyPair.signKeyPair.privateKey,
                                pairingResponse,
                            ),
                    )

                successResponse(call, trustResponse)
            } catch (_: Exception) {
                failResponse(call, StandardErrorCode.TRUST_FAIL.toErrorCode())
            }
        }
    }
}
