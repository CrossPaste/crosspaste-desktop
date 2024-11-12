package com.crosspaste.net.routing

import com.crosspaste.app.AppTokenService
import com.crosspaste.dto.secure.PairingResponse
import com.crosspaste.dto.secure.TrustRequest
import com.crosspaste.dto.secure.TrustResponse
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.secure.ECDSASerializer
import com.crosspaste.secure.SecureStore
import com.crosspaste.utils.CryptographyUtils
import com.crosspaste.utils.failResponse
import com.crosspaste.utils.getAppInstanceId
import com.crosspaste.utils.successResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlin.math.abs

fun Routing.syncRouting(
    appTokenService: AppTokenService,
    ecdsaSerializer: ECDSASerializer,
    secureStore: SecureStore,
) {
    val logger = KotlinLogging.logger {}

    get("/sync/showToken") {
        appTokenService.toShowToken()
        logger.debug { "show token" }
        successResponse(call)
    }

//    get("/sync/isTrust") {
//        getAppInstanceId(call)?.let { appInstanceId ->
//            val targetAppInstanceId = call.request.headers["targetAppInstanceId"]
//            if (targetAppInstanceId != appInfo.appInstanceId) {
//                logger.debug { "isTrust targetAppInstanceId $targetAppInstanceId not match ${appInfo.appInstanceId}" }
//                failResponse(call, StandardErrorCode.SYNC_NOT_MATCH_APP_INSTANCE_ID.toErrorCode())
//                return@let
//            }
//
//            val existIdentity = secureStore.existIdentity(appInstanceId)
//
//            if (!existIdentity) {
//                logger.debug { "${appInfo.appInstanceId} not trust $appInstanceId in /sync/isTrust api" }
//                failResponse(call, StandardErrorCode.UNTRUSTED_IDENTITY.toErrorCode(), "not trust $appInstanceId")
//                return@let
//            }
//
//            logger.debug { "${appInfo.appInstanceId} isTrust $appInstanceId" }
//            successResponse(call)
//        }
//    }

    post("/sync/trust") {
        getAppInstanceId(call)?.let { appInstanceId ->
            try {
                val trustRequest = call.receive(TrustRequest::class)
                val currentTimestamp = Clock.System.now().toEpochMilliseconds()

                if (abs(currentTimestamp - trustRequest.pairingRequest.timestamp) <= 5000) {
                    failResponse(call, StandardErrorCode.TRUST_TIMEOUT.toErrorCode())
                    return@post
                }

                val publicKey = ecdsaSerializer.decodePublicKey(trustRequest.pairingRequest.identityKey)
                val verifyResult = CryptographyUtils.verifyPairingRequest(
                    publicKey,
                    trustRequest.pairingRequest,
                    trustRequest.signature,
                )

                if (!verifyResult) {
                    failResponse(call, StandardErrorCode.SIGN_INVALID.toErrorCode())
                    return@post
                }

                val sameToken = appTokenService.sameToken(trustRequest.pairingRequest.token)
                if (!sameToken) {
                    failResponse(call, StandardErrorCode.TOKEN_INVALID.toErrorCode())
                    return@post
                }

                secureStore.saveIdentity(appInstanceId, trustRequest.pairingRequest.identityKey)

                val identityKey = secureStore.getPublicKey()

                val trustResponse = TrustResponse(
                    pairingResponse = PairingResponse(
                        identityKey,
                        currentTimestamp,
                    ),
                    signature = CryptographyUtils.signPairingRequest(
                        ecdsaSerializer.decodePrivateKey(identityKey),
                        trustRequest.pairingRequest,
                    ),
                )

                successResponse(call, trustResponse)
            } catch (e: Exception) {
                failResponse(call, StandardErrorCode.TRUST_FAIL.toErrorCode())
            }
        }
    }
}
