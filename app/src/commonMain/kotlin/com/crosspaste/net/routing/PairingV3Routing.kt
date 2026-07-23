package com.crosspaste.net.routing

import com.crosspaste.dto.pairing.v3.PairingCancelV3
import com.crosspaste.dto.pairing.v3.PairingCommitV3
import com.crosspaste.dto.pairing.v3.PairingIntentV3
import com.crosspaste.dto.pairing.v3.PairingProofV3
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.pairing.v3.PairingProtocolV3Service
import com.crosspaste.pairing.v3.PairingV3ServerResult
import com.crosspaste.pairing.v3.toStandardErrorCode
import com.crosspaste.utils.failResponse
import com.crosspaste.utils.getAppInstanceId
import com.crosspaste.utils.successResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.routing.*

private val logger = KotlinLogging.logger {}

/**
 * Pairing v3 endpoints (design doc §19 Phase 3). Thin transport layer: every
 * protocol and cryptographic decision lives in [PairingProtocolV3Service]; this
 * file only decodes DTOs, extracts the caller identity, and maps refusals to the
 * shared error-code transport.
 */
fun Routing.pairingV3Routing(
    pairingProtocolV3Service: PairingProtocolV3Service,
    trustSyncInfo: (String, String?, SyncInfo?) -> Unit,
) {
    post("/sync/pairing/v3/intent") {
        getAppInstanceId(call)?.let { appInstanceId ->
            val intent = call.receive(PairingIntentV3::class)
            val remoteAddress = runCatching { call.request.origin.remoteHost }.getOrNull()
            when (val result = pairingProtocolV3Service.handleIntent(intent, appInstanceId, remoteAddress)) {
                is PairingV3ServerResult.Ok -> successResponse(call, result.value)
                is PairingV3ServerResult.Refused ->
                    failResponse(call, result.code.toStandardErrorCode().toErrorCode())
            }
        }
    }

    post("/sync/pairing/v3/proof") {
        getAppInstanceId(call)?.let { appInstanceId ->
            val proof = call.receive(PairingProofV3::class)
            val remoteAddress = runCatching { call.request.origin.remoteHost }.getOrNull()
            when (val result = pairingProtocolV3Service.handleProof(proof, appInstanceId, remoteAddress)) {
                is PairingV3ServerResult.Ok -> successResponse(call, result.value)
                is PairingV3ServerResult.Refused ->
                    failResponse(call, result.code.toStandardErrorCode().toErrorCode())
            }
        }
    }

    post("/sync/pairing/v3/commit") {
        getAppInstanceId(call)?.let { appInstanceId ->
            val commit = call.receive(PairingCommitV3::class)
            when (val result = pairingProtocolV3Service.handleCommit(commit, appInstanceId)) {
                is PairingV3ServerResult.Ok -> {
                    val host = runCatching { call.request.origin.remoteHost }.getOrNull()
                    logger.info { "pairing v3 commit accepted, trusting $appInstanceId" }
                    trustSyncInfo(appInstanceId, host, null)
                    successResponse(call, result.value)
                }

                is PairingV3ServerResult.Refused ->
                    failResponse(call, result.code.toStandardErrorCode().toErrorCode())
            }
        }
    }

    post("/sync/pairing/v3/cancel") {
        getAppInstanceId(call)?.let { appInstanceId ->
            val cancel = call.receive(PairingCancelV3::class)
            when (val result = pairingProtocolV3Service.handleCancel(cancel, appInstanceId)) {
                is PairingV3ServerResult.Ok -> successResponse(call)
                is PairingV3ServerResult.Refused ->
                    failResponse(call, result.code.toStandardErrorCode().toErrorCode())
            }
        }
    }
}
