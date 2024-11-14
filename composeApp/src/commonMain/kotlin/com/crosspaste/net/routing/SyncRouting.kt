package com.crosspaste.net.routing

import com.crosspaste.app.AppTokenService
import com.crosspaste.dto.secure.PairingResponse
import com.crosspaste.dto.secure.TrustRequest
import com.crosspaste.dto.secure.TrustResponse
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.secure.SecureKeyPairSerializer
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
    secureKeyPairSerializer: SecureKeyPairSerializer,
    secureStore: SecureStore,
) {
    val logger = KotlinLogging.logger {}

    get("/sync/showToken") {
        appTokenService.toShowToken()
        logger.debug { "show token" }
        successResponse(call)
    }

    post("/sync/trust") {
        getAppInstanceId(call)?.let { appInstanceId ->
            try {
                val trustRequest = call.receive(TrustRequest::class)
                val currentTimestamp = Clock.System.now().toEpochMilliseconds()

                if (abs(currentTimestamp - trustRequest.pairingRequest.timestamp) <= 5000) {
                    failResponse(call, StandardErrorCode.TRUST_TIMEOUT.toErrorCode())
                    return@post
                }

                val receiveSignPublicKey = secureKeyPairSerializer.decodeSignPublicKey(
                    trustRequest.pairingRequest.signPublicKey,
                )

                val verifyResult = CryptographyUtils.verifyPairingRequest(
                    receiveSignPublicKey,
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

                secureStore.saveCryptPublicKey(appInstanceId, trustRequest.pairingRequest.cryptPublicKey)

                val signPublicKey = secureStore.getSignPublicKeyBytes()
                val cryptPublicKey = secureStore.getCryptPublicKeyBytes()

                val pairingResponse = PairingResponse(
                    signPublicKey,
                    cryptPublicKey,
                    currentTimestamp,
                )

                val trustResponse = TrustResponse(
                    pairingResponse = pairingResponse,
                    signature = CryptographyUtils.signPairingResponse(
                        secureStore.getSecureKeyPair().signKeyPair.privateKey,
                        pairingResponse,
                    ),
                )

                successResponse(call, trustResponse)
            } catch (e: Exception) {
                failResponse(call, StandardErrorCode.TRUST_FAIL.toErrorCode())
            }
        }
    }
}
