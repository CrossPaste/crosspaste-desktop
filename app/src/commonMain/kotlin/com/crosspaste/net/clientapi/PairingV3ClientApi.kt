package com.crosspaste.net.clientapi

import com.crosspaste.dto.pairing.v3.PairingCancelV3
import com.crosspaste.dto.pairing.v3.PairingCommitAckV3
import com.crosspaste.dto.pairing.v3.PairingCommitV3
import com.crosspaste.dto.pairing.v3.PairingIntentV3
import com.crosspaste.dto.pairing.v3.PairingOfferV3
import com.crosspaste.dto.pairing.v3.PairingProofResponseV3
import com.crosspaste.dto.pairing.v3.PairingProofV3
import com.crosspaste.net.PasteClient
import com.crosspaste.net.SyncInfoAdvertiser
import com.crosspaste.net.SyncInfoHeaderCodec
import com.crosspaste.net.exception.ExceptionHandler
import com.crosspaste.utils.buildUrl
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.call.*
import io.ktor.http.*
import io.ktor.util.reflect.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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
    // Optional so platforms that have not wired an advertiser keep the plain commit.
    private val syncInfoAdvertiser: SyncInfoAdvertiser? = null,
) : PairingV3Transport {

    companion object {
        // The commit is a one-off at the end of pairing, not a hot probe: a slow server
        // port wait is worth a short stall here because the header is what lets a peer
        // that never sees our mDNS reach us back at all.
        val COMMIT_ADVERTISE_TIMEOUT: Duration = 1.seconds
    }

    private val logger = KotlinLogging.logger {}

    override suspend fun sendIntent(
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

    override suspend fun sendProof(
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

    override suspend fun sendCommit(
        commit: PairingCommitV3,
        toUrl: URLBuilder.() -> Unit,
    ): ClientApiResult {
        // Self-register our address on the commit, mirroring what the browser extension
        // does. The acceptor only otherwise learns our address from mDNS, and a peer that
        // never receives our multicast (VM NAT, AP isolation) would finish the pairing
        // trusted but unreachable — it could not push to us or pull our files.
        val advertiseHeader = buildCommitAdvertiseHeader(toUrl)
        return request(logger, exceptionHandler, request = {
            pasteClient.post(
                commit,
                typeInfo<PairingCommitV3>(),
                headersBuilder = {
                    advertiseHeader?.let { append(SyncInfoHeaderCodec.HEADER, it) }
                },
                urlBuilder = {
                    toUrl()
                    buildUrl("sync", "pairing", "v3", "commit")
                },
            )
        }) { response ->
            response.body<PairingCommitAckV3>()
        }
    }

    /**
     * Prefer the interface(s) on the acceptor's subnet (the address it can route to);
     * when none share its subnet, advertise everything we listen on and let the
     * acceptor's reachability probe pick a candidate.
     */
    private suspend fun buildCommitAdvertiseHeader(toUrl: URLBuilder.() -> Unit): String? {
        val advertiser = syncInfoAdvertiser ?: return null
        val peerHost = URLBuilder().apply(toUrl).host
        val hostInfoList =
            advertiser.subnetMatchedHostInfo(peerHost).ifEmpty { advertiser.allHostInfo() }
        if (hostInfoList.isEmpty()) return null
        return advertiser.buildHeader(hostInfoList, COMMIT_ADVERTISE_TIMEOUT)
    }

    override suspend fun sendCancel(
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
