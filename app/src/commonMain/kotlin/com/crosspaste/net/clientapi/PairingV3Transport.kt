package com.crosspaste.net.clientapi

import com.crosspaste.dto.pairing.v3.PairingCancelV3
import com.crosspaste.dto.pairing.v3.PairingCommitV3
import com.crosspaste.dto.pairing.v3.PairingIntentV3
import com.crosspaste.dto.pairing.v3.PairingProofV3
import io.ktor.http.URLBuilder

/**
 * Transport seam for the pairing v3 initiator → acceptor calls. Implemented by
 * [PairingV3ClientApi] (real HTTP) in production; a test may provide an
 * in-process implementation that routes each call into the peer's
 * `PairingProtocolV3Service.handle*` methods. No cryptographic logic lives here.
 */
interface PairingV3Transport {

    suspend fun sendIntent(
        intent: PairingIntentV3,
        toUrl: URLBuilder.() -> Unit,
    ): ClientApiResult

    suspend fun sendProof(
        proof: PairingProofV3,
        toUrl: URLBuilder.() -> Unit,
    ): ClientApiResult

    suspend fun sendCommit(
        commit: PairingCommitV3,
        toUrl: URLBuilder.() -> Unit,
    ): ClientApiResult

    suspend fun sendCancel(
        cancel: PairingCancelV3,
        toUrl: URLBuilder.() -> Unit,
    ): ClientApiResult
}
