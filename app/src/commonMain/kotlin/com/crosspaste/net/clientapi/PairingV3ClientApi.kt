package com.crosspaste.net.clientapi

import com.crosspaste.dto.pairing.v3.PairingCancelV3
import com.crosspaste.dto.pairing.v3.PairingCommitAckV3
import com.crosspaste.dto.pairing.v3.PairingCommitV3
import com.crosspaste.dto.pairing.v3.PairingIntentV3
import com.crosspaste.dto.pairing.v3.PairingOfferV3
import com.crosspaste.dto.pairing.v3.PairingProofResponseV3
import com.crosspaste.dto.pairing.v3.PairingProofV3
import com.crosspaste.net.PasteClient
import com.crosspaste.net.exception.ExceptionHandler
import com.crosspaste.utils.buildUrl
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.call.*
import io.ktor.http.*
import io.ktor.util.reflect.*

/**
 * Transport-only client for the pairing v3 endpoints.
 *
 * This layer moves DTOs and maps transport failures; every cryptographic
 * validation (offer signature, confirmation MACs, receipt) happens in
 * `PairingProtocolV3Service`, which owns the session state on both roles.
 */
class PairingV3ClientApi(
    private val pasteClient: PasteClient,
    private val exceptionHandler: ExceptionHandler,
) {

    private val logger = KotlinLogging.logger {}

    suspend fun sendIntent(
        intent: PairingIntentV3,
        toUrl: URLBuilder.() -> Unit,
    ): ClientApiResult =
        request(logger, exceptionHandler, request = {
            pasteClient.post(
                intent,
                typeInfo<PairingIntentV3>(),
                urlBuilder = {
                    toUrl()
                    buildUrl("sync", "pairing", "v3", "intent")
                },
            )
        }) { response ->
            response.body<PairingOfferV3>()
        }

    suspend fun sendProof(
        proof: PairingProofV3,
        toUrl: URLBuilder.() -> Unit,
    ): ClientApiResult =
        request(logger, exceptionHandler, request = {
            pasteClient.post(
                proof,
                typeInfo<PairingProofV3>(),
                urlBuilder = {
                    toUrl()
                    buildUrl("sync", "pairing", "v3", "proof")
                },
            )
        }) { response ->
            response.body<PairingProofResponseV3>()
        }

    suspend fun sendCommit(
        commit: PairingCommitV3,
        toUrl: URLBuilder.() -> Unit,
    ): ClientApiResult =
        request(logger, exceptionHandler, request = {
            pasteClient.post(
                commit,
                typeInfo<PairingCommitV3>(),
                urlBuilder = {
                    toUrl()
                    buildUrl("sync", "pairing", "v3", "commit")
                },
            )
        }) { response ->
            response.body<PairingCommitAckV3>()
        }

    suspend fun sendCancel(
        cancel: PairingCancelV3,
        toUrl: URLBuilder.() -> Unit,
    ): ClientApiResult =
        request(logger, exceptionHandler, request = {
            pasteClient.post(
                cancel,
                typeInfo<PairingCancelV3>(),
                urlBuilder = {
                    toUrl()
                    buildUrl("sync", "pairing", "v3", "cancel")
                },
            )
        }) { true }
}
