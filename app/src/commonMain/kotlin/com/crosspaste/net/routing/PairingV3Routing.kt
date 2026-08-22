package com.crosspaste.net.routing

import com.crosspaste.dto.pairing.v3.PairingCancelV3
import com.crosspaste.dto.pairing.v3.PairingCommitV3
import com.crosspaste.dto.pairing.v3.PairingIntentV3
import com.crosspaste.dto.pairing.v3.PairingProofV3
import com.crosspaste.dto.pairing.v3.PairingV3ErrorCode
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.net.SyncInfoHeaderCodec
import com.crosspaste.pairing.v3.PairingProtocolV3Service
import com.crosspaste.pairing.v3.PairingV3ServerResult
import com.crosspaste.pairing.v3.PairingVersionCoordinator
import com.crosspaste.pairing.v3.toStandardErrorCode
import com.crosspaste.utils.failResponse
import com.crosspaste.utils.getAppInstanceId
import com.crosspaste.utils.successResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlin.time.TimeSource

private val logger = KotlinLogging.logger {}

private suspend inline fun <reified T : Any> ApplicationCall.receivePairingV3OrNull(): T? =
    runCatching { receive<T>() }
        .onFailure { error -> logger.warn(error) { "Rejected malformed pairing v3 payload" } }
        .getOrNull()

/**
 * Pairing v3 endpoints (design doc §19 Phase 3). Thin transport layer: every
 * protocol and cryptographic decision lives in [PairingProtocolV3Service]; this
 * file only decodes DTOs, extracts the caller identity, and maps refusals to the
 * shared error-code transport.
 */
fun Routing.pairingV3Routing(
    pairingProtocolV3Service: PairingProtocolV3Service,
    pairingVersionCoordinator: PairingVersionCoordinator,
    releasePendingKeyExchange: (String) -> Unit,
    trustSyncInfo: (String, String?, SyncInfo?) -> Unit,
) {
    post("/sync/pairing/v3/intent") {
        getAppInstanceId(call)?.let { appInstanceId ->
            // Timeout diagnosis (#4868 B4): stage timing from request arrival to
            // response, to locate where a >3s stall happens on the acceptor.
            val arrivedAt = TimeSource.Monotonic.markNow()
            logger.info { "pairing v3 intent timing: request arrived from $appInstanceId" }
            val intent =
                call.receivePairingV3OrNull<PairingIntentV3>()
                    ?: run {
                        failResponse(
                            call,
                            PairingV3ErrorCode.PAIRING_IDENTITY_INVALID.toStandardErrorCode().toErrorCode(),
                        )
                        return@let
                    }
            logger.info { "pairing v3 intent timing: payload decoded elapsed=${arrivedAt.elapsedNow()}" }
            pairingVersionCoordinator.withPeerLock(appInstanceId) {
                logger.info { "pairing v3 intent timing: peer lock acquired elapsed=${arrivedAt.elapsedNow()}" }
                val remoteAddress = runCatching { call.request.origin.remoteHost }.getOrNull()
                when (val result = pairingProtocolV3Service.handleIntent(intent, appInstanceId, remoteAddress)) {
                    is PairingV3ServerResult.Ok -> {
                        // The v3 takeover consumes the peer's v2 pending exchange;
                        // release its refresh count and verifier with it.
                        releasePendingKeyExchange(appInstanceId)
                        successResponse(call, result.value)
                    }

                    is PairingV3ServerResult.Refused ->
                        failResponse(call, result.code.toStandardErrorCode().toErrorCode())
                }
            }
            logger.info { "pairing v3 intent timing: response completed elapsed=${arrivedAt.elapsedNow()}" }
        }
    }

    post("/sync/pairing/v3/proof") {
        getAppInstanceId(call)?.let { appInstanceId ->
            val proof =
                call.receivePairingV3OrNull<PairingProofV3>()
                    ?: run {
                        failResponse(
                            call,
                            PairingV3ErrorCode.PAIRING_PROOF_INVALID.toStandardErrorCode().toErrorCode(),
                        )
                        return@let
                    }
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
            val commit =
                call.receivePairingV3OrNull<PairingCommitV3>()
                    ?: run {
                        failResponse(
                            call,
                            PairingV3ErrorCode.PAIRING_PROOF_INVALID.toStandardErrorCode().toErrorCode(),
                        )
                        return@let
                    }
            when (val result = pairingProtocolV3Service.handleCommit(commit, appInstanceId)) {
                is PairingV3ServerResult.Ok -> {
                    val host = runCatching { call.request.origin.remoteHost }.getOrNull()
                    logger.info { "pairing v3 commit accepted, trusting $appInstanceId" }
                    // Non-discoverable initiators (browser extension) self-register via
                    // the crosspaste-sync-info header, mirroring the v2 confirm route;
                    // mDNS-discovered peers still resolve from nearby devices.
                    val clientSyncInfo =
                        call.request.headers[SyncInfoHeaderCodec.HEADER]?.let { encoded ->
                            runCatching { SyncInfoHeaderCodec.decodeOrThrow(encoded) }
                                .onFailure { e ->
                                    logger.warn(e) { "Failed to parse ${SyncInfoHeaderCodec.HEADER} header" }
                                }.getOrNull()
                        }
                    trustSyncInfo(appInstanceId, host, clientSyncInfo)
                    successResponse(call, result.value)
                }

                is PairingV3ServerResult.Refused ->
                    failResponse(call, result.code.toStandardErrorCode().toErrorCode())
            }
        }
    }

    post("/sync/pairing/v3/cancel") {
        getAppInstanceId(call)?.let { appInstanceId ->
            val cancel =
                call.receivePairingV3OrNull<PairingCancelV3>()
                    ?: run {
                        failResponse(
                            call,
                            PairingV3ErrorCode.PAIRING_SESSION_NOT_FOUND.toStandardErrorCode().toErrorCode(),
                        )
                        return@let
                    }
            when (val result = pairingProtocolV3Service.handleCancel(cancel, appInstanceId)) {
                is PairingV3ServerResult.Ok -> successResponse(call)
                is PairingV3ServerResult.Refused ->
                    failResponse(call, result.code.toStandardErrorCode().toErrorCode())
            }
        }
    }
}
