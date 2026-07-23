package com.crosspaste.pairing.v3

import com.crosspaste.app.AppInfo
import com.crosspaste.dto.pairing.v3.PairingCancelV3
import com.crosspaste.dto.pairing.v3.PairingCommitAckV3
import com.crosspaste.dto.pairing.v3.PairingCommitV3
import com.crosspaste.dto.pairing.v3.PairingIntentV3
import com.crosspaste.dto.pairing.v3.PairingOfferV3
import com.crosspaste.dto.pairing.v3.PairingProofResponseV3
import com.crosspaste.dto.pairing.v3.PairingProofV3
import com.crosspaste.dto.pairing.v3.PairingV3ErrorCode
import com.crosspaste.net.clientapi.ClientApiResult
import com.crosspaste.net.clientapi.FailureResult
import com.crosspaste.net.clientapi.PairingV3ClientApi
import com.crosspaste.net.clientapi.SuccessResult
import com.crosspaste.net.clientapi.UnknownError
import com.crosspaste.secure.SecureKeyPairSerializer
import com.crosspaste.secure.SecureStore
import com.crosspaste.utils.CryptographyUtils
import com.crosspaste.utils.DateUtils
import com.crosspaste.utils.ioDispatcher
import com.crosspaste.utils.namedScope
import dev.whyoleg.cryptography.random.CryptographyRandom
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.URLBuilder
import io.ktor.util.collections.ConcurrentMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.Volatile
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Orchestrates both roles of pairing v3 (design doc `ai/docs/PAIRING_PROTOCOL_V3.md`).
 *
 * Acceptor role (server side): [handleIntent], [handleProof], [handleCommit],
 * [handleCancel] back the `/sync/pairing/v3/...` endpoints; the service drives PIN
 * generation rotation and session expiry. Initiator role (client side):
 * [startPairing] → user enters the PIN → [submitPin]; [refreshOffer] adopts a
 * rotated generation and [retryCommit] finishes an interrupted commit.
 *
 * All cryptographic decisions live here or below (codec, key schedule, PAKE
 * provider); routing and the client API are pure transport. Endpoints never adopt
 * a transcript hash from the peer — each role recomputes it from its own session.
 */
class PairingProtocolV3Service(
    private val appInfo: AppInfo,
    private val pairingV3ClientApi: PairingV3ClientApi,
    private val pakeProvider: PakeProvider,
    private val receiptCache: PairingReceiptCache,
    private val rateLimiter: PairingRateLimiter,
    private val secureKeyPairSerializer: SecureKeyPairSerializer,
    private val secureStore: SecureStore,
    private val sessionStore: PairingSessionStore,
    val acceptanceWindow: PairingAcceptanceWindow,
    private val isPairingV3Enabled: () -> Boolean,
    private val localDisplayNameProvider: () -> String = { appInfo.userName },
    private val pinLifetime: Duration = PairingV3.DEFAULT_PIN_LIFETIME,
    private val generationGrace: Duration = PairingV3.DEFAULT_GENERATION_GRACE,
    private val sessionTtl: Duration = PairingV3.DEFAULT_SESSION_TTL,
    private val scope: CoroutineScope = namedScope(ioDispatcher, "PairingProtocolV3Service"),
    private val nowEpochMillis: () -> Long = { DateUtils.nowEpochMilliseconds() },
) {

    private val logger = KotlinLogging.logger {}

    private class AcceptorRuntime(
        /** The exact signed offer of the CURRENT generation, replayed on intent retries. */
        @Volatile var currentOffer: PairingOfferV3,
        @Volatile var rotationJob: Job? = null,
    ) {
        /** Wakes the rotation loop early, e.g. when a failed proof killed the generation. */
        val rotationNudge = Channel<Unit>(Channel.CONFLATED)

        /**
         * Serializes generation publication (store rotate + offer replacement) against
         * offer reads, so an intent retry can never observe an offer whose generation
         * lags the store session.
         */
        val offerMutex = Mutex()
    }

    private class InitiatorRuntime(
        /** Retained for byte-identical resend: a changed intent would open a second session. */
        val intent: PairingIntentV3,
        val intentHash: ByteArray,
    )

    private val acceptorRuntimes = ConcurrentMap<String, AcceptorRuntime>()

    private val initiatorRuntimes = ConcurrentMap<String, InitiatorRuntime>()

    private val maintenanceMutex = Mutex()

    @Volatile
    private var maintenanceJob: Job? = null

    /** Sanitized live session cards for the UI layer. */
    val uiSessionsFlow = sessionStore.uiSessionsFlow

    /** Downgrade guard (§17.2): legacy trust is refused while either v3 role is active. */
    fun hasActiveSession(appInstanceId: String): Boolean =
        sessionStore.activeSessions().any { session ->
            session.peerAppInstanceId == appInstanceId
        }

    // region Acceptor role

    suspend fun handleIntent(
        intent: PairingIntentV3,
        callerAppInstanceId: String,
        remoteAddress: String?,
    ): PairingV3ServerResult<PairingOfferV3> {
        if (!isPairingV3Enabled()) {
            return PairingV3ServerResult.Refused(PairingV3ErrorCode.PAIRING_VERSION_UNSUPPORTED)
        }
        if (intent.protocolVersion != PairingV3.PROTOCOL_VERSION) {
            return PairingV3ServerResult.Refused(PairingV3ErrorCode.PAIRING_VERSION_UNSUPPORTED)
        }
        if (!acceptanceWindow.isOpen()) {
            return PairingV3ServerResult.Refused(PairingV3ErrorCode.PAIRING_DISABLED)
        }
        validateIntentShape(intent, callerAppInstanceId)?.let { code ->
            return PairingV3ServerResult.Refused(code)
        }
        if (pakeProvider.ciphersuite !in intent.supportedCiphersuites) {
            return PairingV3ServerResult.Refused(PairingV3ErrorCode.PAIRING_CIPHERSUITE_UNSUPPORTED)
        }
        val initiatorSignKey =
            runCatching { secureKeyPairSerializer.decodeSignPublicKey(intent.initiatorSignPublicKey) }
                .getOrNull()
                ?: return PairingV3ServerResult.Refused(PairingV3ErrorCode.PAIRING_IDENTITY_INVALID)
        runCatching { secureKeyPairSerializer.decodeCryptPublicKey(intent.initiatorCryptPublicKey) }
            .getOrNull()
            ?: return PairingV3ServerResult.Refused(PairingV3ErrorCode.PAIRING_IDENTITY_INVALID)
        val signatureValid =
            runCatching {
                CryptographyUtils.verifyData(initiatorSignKey, intent.signature) {
                    PairingTranscriptCodec.encodeIntentSignaturePayload(intent)
                }
            }.getOrDefault(false)
        if (!signatureValid) {
            return PairingV3ServerResult.Refused(PairingV3ErrorCode.PAIRING_IDENTITY_INVALID)
        }
        val fingerprint = PairingKeyFingerprint.of(intent.initiatorSignPublicKey)
        val source =
            PairingAttemptSource(
                peerKeyFingerprint = fingerprint,
                peerAppInstanceId = intent.initiatorAppInstanceId,
                remoteAddress = remoteAddress,
            )
        if (!rateLimiter.tryAcquire(source)) {
            return PairingV3ServerResult.Refused(PairingV3ErrorCode.PAIRING_RATE_LIMITED)
        }
        val intentHash = PairingTranscriptCodec.intentHash(intent)
        return createAcceptorSession(intent, intentHash, fingerprint)
    }

    private suspend fun createAcceptorSession(
        intent: PairingIntentV3,
        intentHash: ByteArray,
        fingerprint: String,
    ): PairingV3ServerResult<PairingOfferV3> {
        var replacedPeerSession = false
        var sessionIdCollisions = 0
        while (true) {
            val sessionIdBytes = randomBytes(PairingV3.SESSION_ID_SIZE)
            val sessionId = toHex(sessionIdBytes)
            val acceptorNonce = randomBytes(PairingV3.NONCE_SIZE)
            val material =
                prepareGeneration(
                    sessionIdBytes = sessionIdBytes,
                    tokenGeneration = 1L,
                    initiatorAppInstanceId = intent.initiatorAppInstanceId,
                    initiatorSignPublicKey = intent.initiatorSignPublicKey,
                    initiatorCryptPublicKey = intent.initiatorCryptPublicKey,
                    intentHash = intentHash,
                    acceptorNonce = acceptorNonce,
                )
            val now = nowEpochMillis()
            val session =
                PairingSession(
                    sessionId = sessionId,
                    sessionIdBytes = sessionIdBytes,
                    role = PakeRole.ACCEPTOR,
                    requestId = toHex(intent.requestId),
                    peerAppInstanceId = intent.initiatorAppInstanceId,
                    peerDisplayName = intent.initiatorDisplayName,
                    peerSignPublicKey = intent.initiatorSignPublicKey,
                    peerCryptPublicKey = intent.initiatorCryptPublicKey,
                    peerKeyFingerprint = fingerprint,
                    localNonce = acceptorNonce,
                    peerNonce = intent.initiatorNonce,
                    selectedCiphersuite = pakeProvider.ciphersuite,
                    negotiatedCapabilities = NEGOTIATED_CAPABILITIES,
                    intentHash = intentHash,
                    offerHash = material.offerHash,
                    localPakeShare = material.localPakeShare,
                    peerPakeShare = null,
                    tokenGeneration = 1L,
                    pin = material.pin,
                    pinExpiresAt = material.pinExpiresAt,
                    generationFrozenUntil = 0L,
                    proofAttempts = 0,
                    pakeSession = material.pakeSession,
                    sessionKeys = null,
                    transcriptHash = null,
                    state = PairingSessionState.PIN_AVAILABLE,
                    createdAt = now,
                    expiresAt = now + sessionTtl.inWholeMilliseconds,
                )
            when (val created = sessionStore.create(session)) {
                is PairingSessionCreateResult.Created -> {
                    acceptorRuntimes[sessionId] = AcceptorRuntime(material.offer)
                    launchRotation(sessionId)
                    ensureMaintenance()
                    logger.info {
                        "pairing v3 session created session=${shortId(sessionId)} " +
                            "peerKey=${PairingKeyFingerprint.display(fingerprint)}"
                    }
                    return PairingV3ServerResult.Ok(material.offer)
                }

                is PairingSessionCreateResult.Duplicate -> {
                    material.destroy()
                    val offer =
                        acceptorRuntimes[created.existing.sessionId]?.let { runtime ->
                            runtime.offerMutex.withLock { runtime.currentOffer }
                        }
                    return if (offer != null) {
                        PairingV3ServerResult.Ok(offer)
                    } else {
                        PairingV3ServerResult.Refused(PairingV3ErrorCode.PAIRING_INVALID_STATE)
                    }
                }

                is PairingSessionCreateResult.PeerAlreadyActive -> {
                    material.destroy()
                    if (replacedPeerSession) {
                        return PairingV3ServerResult.Refused(PairingV3ErrorCode.PAIRING_INVALID_STATE)
                    }
                    // A fresh intent from the SAME verified signing key means the peer
                    // restarted its attempt: the old session can never complete, so it
                    // is cancelled and replaced. The intent signature gates this path.
                    logger.info { "pairing v3 replacing stale session=${shortId(created.existing.sessionId)}" }
                    sessionStore.cancel(created.existing.sessionId)
                    cleanupRuntime(created.existing.sessionId)
                    replacedPeerSession = true
                }

                is PairingSessionCreateResult.IdentityConflict -> {
                    material.destroy()
                    return PairingV3ServerResult.Refused(PairingV3ErrorCode.PAIRING_IDENTITY_INVALID)
                }

                PairingSessionCreateResult.IntentConsumed -> {
                    material.destroy()
                    return PairingV3ServerResult.Refused(PairingV3ErrorCode.PAIRING_SESSION_CONSUMED)
                }

                PairingSessionCreateResult.SessionIdCollision -> {
                    material.destroy()
                    if (++sessionIdCollisions >= MAX_SESSION_ID_COLLISION_RETRIES) {
                        return PairingV3ServerResult.Refused(PairingV3ErrorCode.PAIRING_INVALID_STATE)
                    }
                }

                PairingSessionCreateResult.CapacityExceeded -> {
                    material.destroy()
                    return PairingV3ServerResult.Refused(PairingV3ErrorCode.PAIRING_CAPACITY_EXCEEDED)
                }
            }
        }
    }

    suspend fun handleProof(
        proof: PairingProofV3,
        callerAppInstanceId: String,
        remoteAddress: String?,
    ): PairingV3ServerResult<PairingProofResponseV3> {
        validateProofShape(proof)?.let { code -> return PairingV3ServerResult.Refused(code) }
        val sessionId = toHex(proof.sessionId)
        val session = sessionStore.get(sessionId)
        if (session == null || session.role != PakeRole.ACCEPTOR || session.peerAppInstanceId != callerAppInstanceId) {
            return PairingV3ServerResult.Refused(PairingV3ErrorCode.PAIRING_SESSION_NOT_FOUND)
        }
        val source =
            PairingAttemptSource(
                peerKeyFingerprint = session.peerKeyFingerprint,
                peerAppInstanceId = session.peerAppInstanceId,
                remoteAddress = remoteAddress,
            )
        if (!rateLimiter.tryAcquire(source)) {
            return PairingV3ServerResult.Refused(PairingV3ErrorCode.PAIRING_RATE_LIMITED)
        }
        val negotiating =
            when (
                val begun =
                    sessionStore.beginProof(
                        sessionId,
                        proof.tokenGeneration,
                        generationGrace.inWholeMilliseconds,
                    )
            ) {
                is PairingBeginProofResult.Proceed -> begun.session
                PairingBeginProofResult.WrongGeneration,
                PairingBeginProofResult.PinExpired,
                PairingBeginProofResult.AttemptsExhausted,
                PairingBeginProofResult.GenerationNotReady,
                ->
                    return PairingV3ServerResult.Refused(PairingV3ErrorCode.PAIRING_PIN_EXPIRED)

                is PairingBeginProofResult.InvalidState ->
                    return PairingV3ServerResult.Refused(stateRefusalCode(begun.actual))

                PairingBeginProofResult.NotFound ->
                    return PairingV3ServerResult.Refused(PairingV3ErrorCode.PAIRING_SESSION_NOT_FOUND)
            }
        val pakeSession =
            negotiating.pakeSession
                ?: return proofFailure(sessionId)
        val acceptorShare =
            negotiating.localPakeShare
                ?: return proofFailure(sessionId)
        val sharedSecret =
            runCatching { pakeSession.deriveSharedSecret(proof.initiatorPakeShare) }
                .getOrNull()
                ?: return proofFailure(sessionId)
        val transcriptHash =
            PairingTranscriptCodec.transcriptHash(
                buildTranscript(negotiating, proof.initiatorPakeShare, acceptorShare),
            )
        if (!transcriptHash.contentEquals(proof.transcriptHash)) {
            sharedSecret.fill(0)
            return proofFailure(sessionId)
        }
        val keys = PairingKeySchedule.deriveSessionKeys(transcriptHash, sharedSecret)
        sharedSecret.fill(0)
        val expectedConfirmation = PairingKeySchedule.initiatorConfirmation(keys, transcriptHash)
        if (!PairingKeySchedule.constantTimeEquals(expectedConfirmation, proof.initiatorKeyConfirmation)) {
            keys.clear()
            return proofFailure(sessionId)
        }
        val identityValid =
            runCatching {
                val peerSignKey = secureKeyPairSerializer.decodeSignPublicKey(negotiating.peerSignPublicKey)
                CryptographyUtils.verifyData(peerSignKey, proof.initiatorIdentitySignature) {
                    PairingKeySchedule.identitySignaturePayload(PakeRole.INITIATOR, transcriptHash)
                }
            }.getOrDefault(false)
        if (!identityValid) {
            keys.clear()
            return proofFailure(sessionId)
        }
        when (
            val confirmed =
                sessionStore.completeProof(
                    sessionId,
                    proof.tokenGeneration,
                    transcriptHash,
                    keys,
                    proof.initiatorPakeShare,
                )
        ) {
            is PairingCompleteProofResult.Confirmed -> Unit
            PairingCompleteProofResult.WrongGeneration,
            PairingCompleteProofResult.DeadlineExceeded,
            -> {
                keys.clear()
                return PairingV3ServerResult.Refused(PairingV3ErrorCode.PAIRING_PIN_EXPIRED)
            }

            is PairingCompleteProofResult.InvalidState -> {
                keys.clear()
                return PairingV3ServerResult.Refused(stateRefusalCode(confirmed.actual))
            }

            PairingCompleteProofResult.NotFound -> {
                keys.clear()
                return PairingV3ServerResult.Refused(PairingV3ErrorCode.PAIRING_SESSION_NOT_FOUND)
            }
        }
        val response =
            PairingProofResponseV3(
                sessionId = proof.sessionId,
                transcriptHash = transcriptHash,
                acceptorKeyConfirmation = PairingKeySchedule.acceptorConfirmation(keys, transcriptHash),
                acceptorIdentitySignature =
                    CryptographyUtils.signData(secureStore.secureKeyPair.signKeyPair.privateKey) {
                        PairingKeySchedule.identitySignaturePayload(PakeRole.ACCEPTOR, transcriptHash)
                    },
            )
        logger.info { "pairing v3 proof accepted session=${shortId(sessionId)}" }
        return PairingV3ServerResult.Ok(response)
    }

    suspend fun handleCommit(
        commit: PairingCommitV3,
        callerAppInstanceId: String,
    ): PairingV3ServerResult<PairingCommitAckV3> {
        validateCommitShape(commit)?.let { code -> return PairingV3ServerResult.Refused(code) }
        val sessionId = toHex(commit.sessionId)
        val commitMacHex = toHex(commit.commitMac)
        val session = sessionStore.get(sessionId)
        if (session == null) {
            // The session card may already be pruned; the receipt cache still resolves
            // retries, but only for the exact peer that earned the receipt.
            return when (val cached = receiptCache.lookup(sessionId, callerAppInstanceId, commitMacHex)) {
                is PairingReceiptCache.Lookup.Hit -> PairingV3ServerResult.Ok(cached.ack)
                PairingReceiptCache.Lookup.Conflict ->
                    PairingV3ServerResult.Refused(PairingV3ErrorCode.PAIRING_SESSION_CONSUMED)

                PairingReceiptCache.Lookup.Miss ->
                    PairingV3ServerResult.Refused(PairingV3ErrorCode.PAIRING_SESSION_NOT_FOUND)
            }
        }
        if (session.role != PakeRole.ACCEPTOR || session.peerAppInstanceId != callerAppInstanceId) {
            return PairingV3ServerResult.Refused(PairingV3ErrorCode.PAIRING_SESSION_NOT_FOUND)
        }
        return when (session.state) {
            PairingSessionState.TRUSTED ->
                when (val cached = receiptCache.lookup(sessionId, callerAppInstanceId, commitMacHex)) {
                    is PairingReceiptCache.Lookup.Hit -> PairingV3ServerResult.Ok(cached.ack)
                    else -> PairingV3ServerResult.Refused(PairingV3ErrorCode.PAIRING_SESSION_CONSUMED)
                }

            PairingSessionState.PEER_CONFIRMED,
            PairingSessionState.COMMITTING,
            -> acceptCommit(session, commit, sessionId, commitMacHex)

            PairingSessionState.REJECTED -> PairingV3ServerResult.Refused(PairingV3ErrorCode.PAIRING_REJECTED)
            PairingSessionState.CANCELLED -> PairingV3ServerResult.Refused(PairingV3ErrorCode.PAIRING_CANCELLED)
            PairingSessionState.EXPIRED -> PairingV3ServerResult.Refused(PairingV3ErrorCode.PAIRING_SESSION_EXPIRED)
            PairingSessionState.FAILED -> PairingV3ServerResult.Refused(PairingV3ErrorCode.PAIRING_SESSION_CONSUMED)
            else -> PairingV3ServerResult.Refused(PairingV3ErrorCode.PAIRING_INVALID_STATE)
        }
    }

    private suspend fun acceptCommit(
        session: PairingSession,
        commit: PairingCommitV3,
        sessionId: String,
        commitMacHex: String,
    ): PairingV3ServerResult<PairingCommitAckV3> {
        val keys = session.sessionKeys ?: return PairingV3ServerResult.Refused(PairingV3ErrorCode.PAIRING_INVALID_STATE)
        val transcriptHash =
            session.transcriptHash
                ?: return PairingV3ServerResult.Refused(PairingV3ErrorCode.PAIRING_INVALID_STATE)
        if (!transcriptHash.contentEquals(commit.transcriptHash)) {
            return PairingV3ServerResult.Refused(PairingV3ErrorCode.PAIRING_PROOF_INVALID)
        }
        val expectedMac = PairingKeySchedule.commitMac(keys, transcriptHash)
        if (!PairingKeySchedule.constantTimeEquals(expectedMac, commit.commitMac)) {
            return PairingV3ServerResult.Refused(PairingV3ErrorCode.PAIRING_PROOF_INVALID)
        }
        if (session.state == PairingSessionState.PEER_CONFIRMED) {
            val begun = sessionStore.beginCommit(sessionId)
            val concurrentCommit =
                begun is PairingSessionTransitionResult.InvalidState &&
                    begun.actual == PairingSessionState.COMMITTING
            if (begun !is PairingSessionTransitionResult.Success && !concurrentCommit) {
                return PairingV3ServerResult.Refused(PairingV3ErrorCode.PAIRING_INVALID_STATE)
            }
        }
        val ack =
            PairingCommitAckV3(
                sessionId = commit.sessionId,
                transcriptHash = transcriptHash,
                receiptMac = PairingKeySchedule.receiptMac(keys, transcriptHash),
            )
        var canonicalAck: PairingCommitAckV3? = null
        val completed =
            runCatching {
                sessionStore.completeTrust(sessionId) {
                    when (val cached = receiptCache.lookup(sessionId, session.peerAppInstanceId, commitMacHex)) {
                        is PairingReceiptCache.Lookup.Hit -> {
                            canonicalAck = cached.ack
                            true
                        }

                        PairingReceiptCache.Lookup.Conflict -> false
                        PairingReceiptCache.Lookup.Miss -> {
                            secureStore.saveCryptPublicKey(session.peerAppInstanceId, session.peerCryptPublicKey)
                            when (
                                val recorded =
                                    receiptCache.recordOrLookup(
                                        sessionId,
                                        session.peerAppInstanceId,
                                        commitMacHex,
                                        ack,
                                    )
                            ) {
                                is PairingReceiptCache.Record.Inserted -> {
                                    canonicalAck = recorded.ack
                                    true
                                }

                                is PairingReceiptCache.Record.Hit -> {
                                    canonicalAck = recorded.ack
                                    true
                                }

                                PairingReceiptCache.Record.Conflict -> false
                            }
                        }
                    }
                }
            }.getOrElse { error ->
                logger.error(error) { "pairing v3 key persistence failed session=${shortId(sessionId)}" }
                return PairingV3ServerResult.Refused(PairingV3ErrorCode.PAIRING_INVALID_STATE)
            }
        if (completed is PairingSessionTransitionResult.Success) {
            cleanupRuntime(sessionId)
            logger.info { "pairing v3 session completed session=${shortId(sessionId)}" }
            return PairingV3ServerResult.Ok(requireNotNull(canonicalAck))
        }
        // Concurrent byte-identical commits wait for the first finalization and
        // then resolve to its original receipt.
        when (val cached = receiptCache.lookup(sessionId, session.peerAppInstanceId, commitMacHex)) {
            is PairingReceiptCache.Lookup.Hit -> return PairingV3ServerResult.Ok(cached.ack)
            PairingReceiptCache.Lookup.Conflict ->
                return PairingV3ServerResult.Refused(PairingV3ErrorCode.PAIRING_SESSION_CONSUMED)

            PairingReceiptCache.Lookup.Miss -> Unit
        }
        return when (sessionStore.get(sessionId)?.state) {
            PairingSessionState.REJECTED -> PairingV3ServerResult.Refused(PairingV3ErrorCode.PAIRING_REJECTED)
            PairingSessionState.CANCELLED -> PairingV3ServerResult.Refused(PairingV3ErrorCode.PAIRING_CANCELLED)
            PairingSessionState.EXPIRED -> PairingV3ServerResult.Refused(PairingV3ErrorCode.PAIRING_SESSION_EXPIRED)
            PairingSessionState.FAILED,
            PairingSessionState.TRUSTED,
            -> PairingV3ServerResult.Refused(PairingV3ErrorCode.PAIRING_SESSION_CONSUMED)

            else -> PairingV3ServerResult.Refused(PairingV3ErrorCode.PAIRING_INVALID_STATE)
        }
    }

    suspend fun handleCancel(
        cancel: PairingCancelV3,
        callerAppInstanceId: String,
    ): PairingV3ServerResult<Unit> {
        if (cancel.sessionId.size != PairingV3.SESSION_ID_SIZE) {
            return PairingV3ServerResult.Refused(PairingV3ErrorCode.PAIRING_SESSION_NOT_FOUND)
        }
        val sessionId = toHex(cancel.sessionId)
        val session = sessionStore.get(sessionId)
        // Advisory and idempotent: only the exact session of the calling peer is affected.
        if (session != null && session.peerAppInstanceId == callerAppInstanceId && session.isActive) {
            sessionStore.cancel(sessionId)
            cleanupRuntime(sessionId)
            logger.info { "pairing v3 session cancelled by peer session=${shortId(sessionId)}" }
        }
        return PairingV3ServerResult.Ok(Unit)
    }

    /** Local reject wins any active session that has not entered serialized trust finalization. */
    suspend fun rejectSession(sessionId: String): Boolean {
        val rejected = sessionStore.reject(sessionId) is PairingSessionTransitionResult.Success
        if (rejected) {
            cleanupRuntime(sessionId)
        }
        return rejected
    }

    /** Removes a terminal session card (UI dismiss). */
    suspend fun dismissSession(sessionId: String): Boolean = sessionStore.removeTerminal(sessionId)

    // endregion

    // region Initiator role

    suspend fun startPairing(
        targetAppInstanceId: String,
        targetDisplayName: String,
        toUrl: URLBuilder.() -> Unit,
    ): PairingV3StartResult {
        if (!isPairingV3Enabled()) {
            return PairingV3StartResult.Refused(PairingV3ErrorCode.PAIRING_VERSION_UNSUPPORTED)
        }
        if (targetAppInstanceId.isEmpty() || targetAppInstanceId == appInfo.appInstanceId) {
            return PairingV3StartResult.Refused(PairingV3ErrorCode.PAIRING_IDENTITY_INVALID)
        }
        val signPublicKey = secureStore.secureKeyPair.getSignPublicKeyBytes(secureKeyPairSerializer)
        val cryptPublicKey = secureStore.secureKeyPair.getCryptPublicKeyBytes(secureKeyPairSerializer)
        val unsigned =
            PairingIntentV3(
                protocolVersion = PairingV3.PROTOCOL_VERSION,
                requestId = randomBytes(PairingV3.REQUEST_ID_SIZE),
                initiatorAppInstanceId = appInfo.appInstanceId,
                targetAppInstanceId = targetAppInstanceId,
                initiatorDisplayName = localDisplayNameProvider().take(MAX_DISPLAY_NAME_LENGTH),
                initiatorSignPublicKey = signPublicKey,
                initiatorCryptPublicKey = cryptPublicKey,
                initiatorNonce = randomBytes(PairingV3.NONCE_SIZE),
                supportedCiphersuites = listOf(pakeProvider.ciphersuite),
                signature = ByteArray(0),
            )
        val intent =
            unsigned.copy(
                signature =
                    CryptographyUtils.signData(secureStore.secureKeyPair.signKeyPair.privateKey) {
                        PairingTranscriptCodec.encodeIntentSignaturePayload(unsigned)
                    },
            )
        val intentHash = PairingTranscriptCodec.intentHash(intent)
        val offer =
            when (val sent = pairingV3ClientApi.sendIntent(intent, toUrl)) {
                is SuccessResult -> sent.getResult<PairingOfferV3>()
                is FailureResult -> {
                    val code = pairingV3ErrorCodeOf(sent.exception.getErrorCode().code)
                    return if (code != null) {
                        PairingV3StartResult.Refused(code)
                    } else {
                        PairingV3StartResult.NetworkError(sent)
                    }
                }

                else -> return PairingV3StartResult.NetworkError(sent)
            }
        validateOffer(intent, intentHash, offer)?.let { code ->
            return PairingV3StartResult.Refused(code)
        }
        val sessionId = toHex(offer.sessionId)
        val now = nowEpochMillis()
        val session =
            PairingSession(
                sessionId = sessionId,
                sessionIdBytes = offer.sessionId,
                role = PakeRole.INITIATOR,
                requestId = toHex(intent.requestId),
                peerAppInstanceId = targetAppInstanceId,
                peerDisplayName = targetDisplayName,
                peerSignPublicKey = offer.acceptorSignPublicKey,
                peerCryptPublicKey = offer.acceptorCryptPublicKey,
                peerKeyFingerprint = PairingKeyFingerprint.of(offer.acceptorSignPublicKey),
                localNonce = intent.initiatorNonce,
                peerNonce = offer.acceptorNonce,
                selectedCiphersuite = offer.selectedCiphersuite,
                negotiatedCapabilities = NEGOTIATED_CAPABILITIES,
                intentHash = intentHash,
                offerHash = PairingTranscriptCodec.offerHash(offer),
                localPakeShare = null,
                peerPakeShare = offer.acceptorPakeShare,
                tokenGeneration = offer.tokenGeneration,
                pin = null,
                pinExpiresAt = clampPinExpiry(offer.pinExpiresAt, now),
                generationFrozenUntil = 0L,
                proofAttempts = 0,
                pakeSession = null,
                sessionKeys = null,
                transcriptHash = null,
                state = PairingSessionState.PIN_AVAILABLE,
                createdAt = now,
                expiresAt = now + sessionTtl.inWholeMilliseconds,
            )
        return when (sessionStore.create(session)) {
            is PairingSessionCreateResult.Created -> {
                initiatorRuntimes[sessionId] = InitiatorRuntime(intent, intentHash)
                ensureMaintenance()
                PairingV3StartResult.Started(
                    sessionId = sessionId,
                    tokenGeneration = session.tokenGeneration,
                    pinExpiresAt = session.pinExpiresAt,
                    peerKeyFingerprintDisplay = PairingKeyFingerprint.display(session.peerKeyFingerprint),
                )
            }

            else -> PairingV3StartResult.Refused(PairingV3ErrorCode.PAIRING_INVALID_STATE)
        }
    }

    /**
     * Runs PAKE with the user-entered PIN, proves it to the acceptor, verifies the
     * acceptor's proof, and commits. The caller keeps ownership of [pin] clearing.
     */
    suspend fun submitPin(
        sessionId: String,
        pin: CharArray,
        toUrl: URLBuilder.() -> Unit,
    ): PairingV3PinResult {
        val session = sessionStore.get(sessionId)
        if (session == null || session.role != PakeRole.INITIATOR) {
            return PairingV3PinResult.Refused(PairingV3ErrorCode.PAIRING_SESSION_NOT_FOUND)
        }
        if (pin.size != PairingV3.PIN_LENGTH || pin.any { digit -> digit !in '0'..'9' }) {
            return PairingV3PinResult.Refused(PairingV3ErrorCode.PAIRING_PROOF_INVALID)
        }
        val peerShare =
            session.peerPakeShare
                ?: return PairingV3PinResult.Refused(PairingV3ErrorCode.PAIRING_INVALID_STATE)
        val ownSignPublicKey = secureStore.secureKeyPair.getSignPublicKeyBytes(secureKeyPairSerializer)
        val ownCryptPublicKey = secureStore.secureKeyPair.getCryptPublicKeyBytes(secureKeyPairSerializer)
        val pinContext =
            PairingTranscriptCodec.encodePinContext(
                sessionId = session.sessionIdBytes,
                tokenGeneration = session.tokenGeneration,
                acceptorAppInstanceId = session.peerAppInstanceId,
                initiatorAppInstanceId = appInfo.appInstanceId,
                acceptorSignPublicKey = session.peerSignPublicKey,
                acceptorCryptPublicKey = session.peerCryptPublicKey,
                initiatorSignPublicKey = ownSignPublicKey,
                initiatorCryptPublicKey = ownCryptPublicKey,
            )
        val pakeSession =
            runCatching {
                pakeProvider.createSession(
                    role = PakeRole.INITIATOR,
                    pin = pin,
                    context =
                        PakeContext(
                            sessionId = session.sessionIdBytes,
                            initiatorAppInstanceId = appInfo.appInstanceId,
                            acceptorAppInstanceId = session.peerAppInstanceId,
                            pinContext = pinContext,
                        ),
                )
            }.getOrNull()
                ?: return PairingV3PinResult.Refused(PairingV3ErrorCode.PAIRING_INVALID_STATE)
        val localShare = runCatching { pakeSession.localShare() }.getOrNull()
        if (localShare == null) {
            pakeSession.destroy()
            return PairingV3PinResult.Refused(PairingV3ErrorCode.PAIRING_INVALID_STATE)
        }
        val attached =
            sessionStore.transition(sessionId, setOf(PairingSessionState.PIN_AVAILABLE)) { current ->
                current.copy(
                    pin = pin.copyOf(),
                    pakeSession = pakeSession,
                    localPakeShare = localShare,
                )
            }
        if (attached !is PairingSessionTransitionResult.Success) {
            pakeSession.destroy()
            return PairingV3PinResult.Refused(PairingV3ErrorCode.PAIRING_INVALID_STATE)
        }
        val negotiating =
            when (
                val begun =
                    sessionStore.beginProof(sessionId, session.tokenGeneration, generationGrace.inWholeMilliseconds)
            ) {
                is PairingBeginProofResult.Proceed -> begun.session
                PairingBeginProofResult.WrongGeneration,
                PairingBeginProofResult.PinExpired,
                PairingBeginProofResult.GenerationNotReady,
                ->
                    return PairingV3PinResult.Refused(PairingV3ErrorCode.PAIRING_PIN_EXPIRED)

                PairingBeginProofResult.AttemptsExhausted ->
                    return PairingV3PinResult.Refused(PairingV3ErrorCode.PAIRING_PIN_EXPIRED)

                is PairingBeginProofResult.InvalidState ->
                    return PairingV3PinResult.Refused(PairingV3ErrorCode.PAIRING_INVALID_STATE)

                PairingBeginProofResult.NotFound ->
                    return PairingV3PinResult.Refused(PairingV3ErrorCode.PAIRING_SESSION_NOT_FOUND)
            }
        val sharedSecret =
            runCatching { pakeSession.deriveSharedSecret(peerShare) }.getOrNull()
                ?: run {
                    sessionStore.recordProofFailure(sessionId)
                    return PairingV3PinResult.Refused(PairingV3ErrorCode.PAIRING_PROOF_INVALID)
                }
        val transcriptHash =
            PairingTranscriptCodec.transcriptHash(
                buildTranscript(negotiating, localShare, peerShare),
            )
        val keys = PairingKeySchedule.deriveSessionKeys(transcriptHash, sharedSecret)
        sharedSecret.fill(0)
        val proof =
            PairingProofV3(
                sessionId = session.sessionIdBytes,
                tokenGeneration = session.tokenGeneration,
                initiatorPakeShare = localShare,
                transcriptHash = transcriptHash,
                initiatorKeyConfirmation = PairingKeySchedule.initiatorConfirmation(keys, transcriptHash),
                initiatorIdentitySignature =
                    CryptographyUtils.signData(secureStore.secureKeyPair.signKeyPair.privateKey) {
                        PairingKeySchedule.identitySignaturePayload(PakeRole.INITIATOR, transcriptHash)
                    },
            )
        val response =
            when (val sent = pairingV3ClientApi.sendProof(proof, toUrl)) {
                is SuccessResult -> sent.getResult<PairingProofResponseV3>()
                is FailureResult -> {
                    keys.clear()
                    sessionStore.recordProofFailure(sessionId)
                    val code = pairingV3ErrorCodeOf(sent.exception.getErrorCode().code)
                    return if (code != null) {
                        PairingV3PinResult.Refused(code)
                    } else {
                        PairingV3PinResult.NetworkError(sent, commitPending = false)
                    }
                }

                else -> {
                    keys.clear()
                    sessionStore.recordProofFailure(sessionId)
                    return PairingV3PinResult.NetworkError(sent, commitPending = false)
                }
            }
        val responseValid =
            response.sessionId.contentEquals(session.sessionIdBytes) &&
                response.transcriptHash.contentEquals(transcriptHash) &&
                PairingKeySchedule.constantTimeEquals(
                    PairingKeySchedule.acceptorConfirmation(keys, transcriptHash),
                    response.acceptorKeyConfirmation,
                ) &&
                runCatching {
                    val peerSignKey = secureKeyPairSerializer.decodeSignPublicKey(session.peerSignPublicKey)
                    CryptographyUtils.verifyData(peerSignKey, response.acceptorIdentitySignature) {
                        PairingKeySchedule.identitySignaturePayload(PakeRole.ACCEPTOR, transcriptHash)
                    }
                }.getOrDefault(false)
        if (!responseValid) {
            // The acceptor (or a MITM) failed mutual confirmation: hard failure.
            keys.clear()
            sessionStore.fail(sessionId)
            cleanupRuntime(sessionId)
            return PairingV3PinResult.Refused(PairingV3ErrorCode.PAIRING_PROOF_INVALID)
        }
        when (
            sessionStore.completeProof(sessionId, session.tokenGeneration, transcriptHash, keys, peerShare)
        ) {
            is PairingCompleteProofResult.Confirmed -> Unit
            else -> {
                keys.clear()
                return PairingV3PinResult.Refused(PairingV3ErrorCode.PAIRING_PIN_EXPIRED)
            }
        }
        return performCommit(sessionId, toUrl)
    }

    /**
     * Completes an interrupted commit: the session must hold a verified transcript
     * (PEER_CONFIRMED or COMMITTING). The commit bytes are deterministic, so a
     * retry is byte-identical and the acceptor answers with the original receipt.
     */
    suspend fun retryCommit(
        sessionId: String,
        toUrl: URLBuilder.() -> Unit,
    ): PairingV3PinResult = performCommit(sessionId, toUrl)

    private suspend fun performCommit(
        sessionId: String,
        toUrl: URLBuilder.() -> Unit,
    ): PairingV3PinResult {
        val session = sessionStore.get(sessionId)
        if (session == null || session.role != PakeRole.INITIATOR) {
            return PairingV3PinResult.Refused(PairingV3ErrorCode.PAIRING_SESSION_NOT_FOUND)
        }
        if (session.state != PairingSessionState.PEER_CONFIRMED && session.state != PairingSessionState.COMMITTING) {
            return PairingV3PinResult.Refused(PairingV3ErrorCode.PAIRING_INVALID_STATE)
        }
        val keys =
            session.sessionKeys
                ?: return PairingV3PinResult.Refused(PairingV3ErrorCode.PAIRING_INVALID_STATE)
        val transcriptHash =
            session.transcriptHash
                ?: return PairingV3PinResult.Refused(PairingV3ErrorCode.PAIRING_INVALID_STATE)
        if (session.state == PairingSessionState.PEER_CONFIRMED &&
            sessionStore.beginCommit(sessionId) !is PairingSessionTransitionResult.Success
        ) {
            return PairingV3PinResult.Refused(PairingV3ErrorCode.PAIRING_INVALID_STATE)
        }
        val commit =
            PairingCommitV3(
                sessionId = session.sessionIdBytes,
                transcriptHash = transcriptHash,
                commitMac = PairingKeySchedule.commitMac(keys, transcriptHash),
            )
        var lastFailure: ClientApiResult? = null
        var ack: PairingCommitAckV3? = null
        var attempt = 0
        while (attempt < COMMIT_ATTEMPTS) {
            when (val sent = pairingV3ClientApi.sendCommit(commit, toUrl)) {
                is SuccessResult -> {
                    ack = sent.getResult<PairingCommitAckV3>()
                    attempt = COMMIT_ATTEMPTS
                }

                is FailureResult -> {
                    val code = pairingV3ErrorCodeOf(sent.exception.getErrorCode().code)
                    if (code != null) {
                        return PairingV3PinResult.Refused(code)
                    }
                    lastFailure = sent
                    attempt = COMMIT_ATTEMPTS
                }

                else -> {
                    // Transient transport failure: the commit bytes are deterministic,
                    // so an immediate bounded retry is safe and idempotent.
                    lastFailure = sent
                    attempt++
                    if (attempt < COMMIT_ATTEMPTS) {
                        delay(COMMIT_RETRY_DELAY)
                    }
                }
            }
        }
        val receivedAck =
            ack ?: return PairingV3PinResult.NetworkError(
                lastFailure ?: UnknownError,
                commitPending = true,
            )
        val ackValid =
            receivedAck.sessionId.contentEquals(session.sessionIdBytes) &&
                receivedAck.transcriptHash.contentEquals(transcriptHash) &&
                PairingKeySchedule.constantTimeEquals(
                    PairingKeySchedule.receiptMac(keys, transcriptHash),
                    receivedAck.receiptMac,
                )
        if (!ackValid) {
            sessionStore.fail(sessionId)
            cleanupRuntime(sessionId)
            return PairingV3PinResult.Refused(PairingV3ErrorCode.PAIRING_PROOF_INVALID)
        }
        val completed =
            runCatching {
                sessionStore.completeTrust(sessionId) {
                    secureStore.saveCryptPublicKey(session.peerAppInstanceId, session.peerCryptPublicKey)
                    true
                }
            }.getOrElse { error ->
                logger.error(error) { "pairing v3 key persistence failed session=${shortId(sessionId)}" }
                return PairingV3PinResult.NetworkError(
                    lastFailure ?: UnknownError,
                    commitPending = true,
                )
            }
        if (completed !is PairingSessionTransitionResult.Success) {
            return when (sessionStore.get(sessionId)?.state) {
                PairingSessionState.REJECTED -> PairingV3PinResult.Refused(PairingV3ErrorCode.PAIRING_REJECTED)
                PairingSessionState.CANCELLED -> PairingV3PinResult.Refused(PairingV3ErrorCode.PAIRING_CANCELLED)
                PairingSessionState.EXPIRED ->
                    PairingV3PinResult.Refused(PairingV3ErrorCode.PAIRING_SESSION_EXPIRED)

                else -> PairingV3PinResult.Refused(PairingV3ErrorCode.PAIRING_INVALID_STATE)
            }
        }
        cleanupRuntime(sessionId)
        logger.info { "pairing v3 session completed session=${shortId(sessionId)}" }
        return PairingV3PinResult.Paired(session.peerAppInstanceId)
    }

    /**
     * Re-fetches the acceptor's current offer by resending the byte-identical
     * intent and adopts its generation (rotated PIN, fresh PAKE share). Used after
     * `PAIRING_PIN_EXPIRED` or a transport failure during the proof step.
     */
    suspend fun refreshOffer(
        sessionId: String,
        toUrl: URLBuilder.() -> Unit,
    ): PairingV3RefreshResult {
        val runtime =
            initiatorRuntimes[sessionId]
                ?: return PairingV3RefreshResult.Refused(PairingV3ErrorCode.PAIRING_SESSION_NOT_FOUND)
        val session = sessionStore.get(sessionId)
        if (session == null || session.role != PakeRole.INITIATOR) {
            return PairingV3RefreshResult.Refused(PairingV3ErrorCode.PAIRING_SESSION_NOT_FOUND)
        }
        if (session.state != PairingSessionState.PIN_AVAILABLE &&
            session.state != PairingSessionState.PAKE_NEGOTIATING
        ) {
            return PairingV3RefreshResult.Refused(PairingV3ErrorCode.PAIRING_INVALID_STATE)
        }
        val offer =
            when (val sent = pairingV3ClientApi.sendIntent(runtime.intent, toUrl)) {
                is SuccessResult -> sent.getResult<PairingOfferV3>()
                is FailureResult -> {
                    val code = pairingV3ErrorCodeOf(sent.exception.getErrorCode().code)
                    if (code != null) {
                        if (code == PairingV3ErrorCode.PAIRING_SESSION_CONSUMED ||
                            code == PairingV3ErrorCode.PAIRING_SESSION_NOT_FOUND ||
                            code == PairingV3ErrorCode.PAIRING_REJECTED ||
                            code == PairingV3ErrorCode.PAIRING_CANCELLED
                        ) {
                            sessionStore.fail(sessionId)
                            cleanupRuntime(sessionId)
                        }
                        return PairingV3RefreshResult.Refused(code)
                    }
                    return PairingV3RefreshResult.NetworkError(sent)
                }

                else -> return PairingV3RefreshResult.NetworkError(sent)
            }
        validateOffer(runtime.intent, runtime.intentHash, offer)?.let { code ->
            return PairingV3RefreshResult.Refused(code)
        }
        if (!offer.sessionId.contentEquals(session.sessionIdBytes)) {
            // The acceptor no longer knows this session and opened a new one; adopting
            // it silently would detach the store from the runtime intent — refuse.
            return PairingV3RefreshResult.Refused(PairingV3ErrorCode.PAIRING_TRANSCRIPT_MISMATCH)
        }
        val offerHash = PairingTranscriptCodec.offerHash(offer)
        val now = nowEpochMillis()
        val adopted =
            sessionStore.transition(
                sessionId,
                setOf(PairingSessionState.PIN_AVAILABLE, PairingSessionState.PAKE_NEGOTIATING),
            ) { current ->
                runCatching { current.pin?.fill('\u0000') }
                runCatching { current.pakeSession?.destroy() }
                runCatching { current.sessionKeys?.clear() }
                current.copy(
                    state = PairingSessionState.PIN_AVAILABLE,
                    tokenGeneration = offer.tokenGeneration,
                    pinExpiresAt = clampPinExpiry(offer.pinExpiresAt, now),
                    generationFrozenUntil = 0L,
                    proofAttempts = 0,
                    pin = null,
                    pakeSession = null,
                    sessionKeys = null,
                    localPakeShare = null,
                    peerPakeShare = offer.acceptorPakeShare,
                    offerHash = offerHash,
                    transcriptHash = null,
                )
            }
        return when (adopted) {
            is PairingSessionTransitionResult.Success ->
                PairingV3RefreshResult.Refreshed(
                    tokenGeneration = adopted.session.tokenGeneration,
                    pinExpiresAt = adopted.session.pinExpiresAt,
                )

            else -> PairingV3RefreshResult.Refused(PairingV3ErrorCode.PAIRING_INVALID_STATE)
        }
    }

    /** Initiator-side cancel: aborts locally and notifies the acceptor best-effort. */
    suspend fun cancelSession(
        sessionId: String,
        toUrl: (URLBuilder.() -> Unit)?,
    ): Boolean {
        val session = sessionStore.get(sessionId) ?: return false
        val cancelled = sessionStore.cancel(sessionId) is PairingSessionTransitionResult.Success
        if (cancelled) {
            cleanupRuntime(sessionId)
            if (toUrl != null) {
                runCatching {
                    pairingV3ClientApi.sendCancel(PairingCancelV3(session.sessionIdBytes), toUrl)
                }
            }
        }
        return cancelled
    }

    // endregion

    // region Shared internals

    private class GenerationMaterial(
        val pin: CharArray,
        val pakeSession: PakeSession,
        val localPakeShare: ByteArray,
        val offer: PairingOfferV3,
        val offerHash: ByteArray,
        val pinExpiresAt: Long,
    ) {
        fun destroy() {
            runCatching { pin.fill('\u0000') }
            runCatching { pakeSession.destroy() }
        }
    }

    private suspend fun prepareGeneration(
        sessionIdBytes: ByteArray,
        tokenGeneration: Long,
        initiatorAppInstanceId: String,
        initiatorSignPublicKey: ByteArray,
        initiatorCryptPublicKey: ByteArray,
        intentHash: ByteArray,
        acceptorNonce: ByteArray,
    ): GenerationMaterial {
        val ownSignPublicKey = secureStore.secureKeyPair.getSignPublicKeyBytes(secureKeyPairSerializer)
        val ownCryptPublicKey = secureStore.secureKeyPair.getCryptPublicKeyBytes(secureKeyPairSerializer)
        val pinContext =
            PairingTranscriptCodec.encodePinContext(
                sessionId = sessionIdBytes,
                tokenGeneration = tokenGeneration,
                acceptorAppInstanceId = appInfo.appInstanceId,
                initiatorAppInstanceId = initiatorAppInstanceId,
                acceptorSignPublicKey = ownSignPublicKey,
                acceptorCryptPublicKey = ownCryptPublicKey,
                initiatorSignPublicKey = initiatorSignPublicKey,
                initiatorCryptPublicKey = initiatorCryptPublicKey,
            )
        val pinSecret = randomBytes(PairingV3.PIN_SECRET_SIZE)
        val pin =
            try {
                PairingPinGenerator.derivePin(pinSecret, pinContext)
            } finally {
                pinSecret.fill(0)
            }
        val pakeSession =
            pakeProvider.createSession(
                role = PakeRole.ACCEPTOR,
                pin = pin,
                context =
                    PakeContext(
                        sessionId = sessionIdBytes,
                        initiatorAppInstanceId = initiatorAppInstanceId,
                        acceptorAppInstanceId = appInfo.appInstanceId,
                        pinContext = pinContext,
                    ),
            )
        val localPakeShare =
            try {
                pakeSession.localShare()
            } catch (e: Exception) {
                runCatching { pin.fill('\u0000') }
                pakeSession.destroy()
                throw e
            }
        val pinExpiresAt = nowEpochMillis() + pinLifetime.inWholeMilliseconds
        val unsignedOffer =
            PairingOfferV3(
                protocolVersion = PairingV3.PROTOCOL_VERSION,
                selectedCiphersuite = pakeProvider.ciphersuite,
                sessionId = sessionIdBytes,
                requestHash = intentHash,
                tokenGeneration = tokenGeneration,
                pinExpiresAt = pinExpiresAt,
                initiatorAppInstanceId = initiatorAppInstanceId,
                acceptorAppInstanceId = appInfo.appInstanceId,
                acceptorSignPublicKey = ownSignPublicKey,
                acceptorCryptPublicKey = ownCryptPublicKey,
                acceptorNonce = acceptorNonce,
                acceptorPakeShare = localPakeShare,
                signature = ByteArray(0),
            )
        val offer =
            unsignedOffer.copy(
                signature =
                    CryptographyUtils.signData(secureStore.secureKeyPair.signKeyPair.privateKey) {
                        PairingTranscriptCodec.encodeOfferSignaturePayload(unsignedOffer)
                    },
            )
        return GenerationMaterial(
            pin = pin,
            pakeSession = pakeSession,
            localPakeShare = localPakeShare,
            offer = offer,
            offerHash = PairingTranscriptCodec.offerHash(offer),
            pinExpiresAt = pinExpiresAt,
        )
    }

    private fun launchRotation(sessionId: String) {
        val job =
            scope.launch {
                while (isActive) {
                    val session = sessionStore.get(sessionId) ?: break
                    if (session.state != PairingSessionState.PIN_AVAILABLE &&
                        session.state != PairingSessionState.PAKE_NEGOTIATING
                    ) {
                        break
                    }
                    val now = nowEpochMillis()
                    val dueAt = maxOf(session.pinExpiresAt, session.generationFrozenUntil)
                    if (now < dueAt) {
                        withTimeoutOrNull((dueAt - now).milliseconds) {
                            acceptorRuntimes[sessionId]?.rotationNudge?.receive()
                        }
                        continue
                    }
                    val intentHash = session.intentHash ?: break
                    val runtime = acceptorRuntimes[sessionId] ?: break
                    val material =
                        runCatching {
                            prepareGeneration(
                                sessionIdBytes = session.sessionIdBytes,
                                tokenGeneration = session.tokenGeneration + 1,
                                initiatorAppInstanceId = session.peerAppInstanceId,
                                initiatorSignPublicKey = session.peerSignPublicKey,
                                initiatorCryptPublicKey = session.peerCryptPublicKey,
                                intentHash = intentHash,
                                acceptorNonce = session.localNonce,
                            )
                        }.getOrNull() ?: break
                    // Store rotation and offer replacement publish atomically with
                    // respect to intent-retry reads of the current offer.
                    val rotated =
                        runtime.offerMutex.withLock {
                            val result =
                                sessionStore.rotateGeneration(
                                    sessionId = sessionId,
                                    expectedGeneration = session.tokenGeneration,
                                    newPin = material.pin,
                                    pinExpiresAt = material.pinExpiresAt,
                                    pakeSession = material.pakeSession,
                                    localPakeShare = material.localPakeShare,
                                    offerHash = material.offerHash,
                                )
                            if (result is PairingRotateResult.Rotated) {
                                runtime.currentOffer = material.offer
                            }
                            result
                        }
                    when (rotated) {
                        is PairingRotateResult.Rotated -> {
                            logger.info {
                                "pairing v3 generation rotated session=${shortId(sessionId)} " +
                                    "generation=${rotated.session.tokenGeneration}"
                            }
                        }

                        PairingRotateResult.Frozen,
                        PairingRotateResult.StaleGeneration,
                        -> material.destroy()

                        is PairingRotateResult.InvalidState,
                        PairingRotateResult.NotFound,
                        -> {
                            material.destroy()
                            break
                        }
                    }
                }
            }
        acceptorRuntimes[sessionId]?.rotationJob = job
    }

    private suspend fun ensureMaintenance() {
        maintenanceMutex.withLock {
            if (maintenanceJob?.isActive == true) return
            maintenanceJob = scope.launch { maintenanceLoop() }
        }
    }

    private suspend fun maintenanceLoop() {
        while (true) {
            delay(MAINTENANCE_INTERVAL)
            sessionStore.expireDueSessions().forEach { expired ->
                cleanupRuntime(expired.sessionId)
            }
            sessionStore.pruneTerminal(TERMINAL_RETENTION.inWholeMilliseconds)
            if (sessionStore.uiSessionsFlow.value.isEmpty()) {
                // Stop only under the mutex, re-checking emptiness: a session created
                // after the read above saw this job as active and did not relaunch,
                // so exiting without the re-check would leave no maintenance running.
                val stop =
                    maintenanceMutex.withLock {
                        if (sessionStore.uiSessionsFlow.value.isEmpty()) {
                            maintenanceJob = null
                            true
                        } else {
                            false
                        }
                    }
                if (stop) {
                    return
                }
            }
        }
    }

    private fun cleanupRuntime(sessionId: String) {
        acceptorRuntimes.remove(sessionId)?.rotationJob?.cancel()
        initiatorRuntimes.remove(sessionId)
    }

    private suspend fun proofFailure(sessionId: String): PairingV3ServerResult.Refused {
        sessionStore.recordProofFailure(sessionId)
        // The failed proof invalidated the generation; rotate a fresh PIN right away
        // instead of leaving the session unusable until the scheduled wake-up.
        acceptorRuntimes[sessionId]?.rotationNudge?.trySend(Unit)
        logger.info { "pairing v3 proof rejected session=${shortId(sessionId)} reason=proof_invalid" }
        return PairingV3ServerResult.Refused(PairingV3ErrorCode.PAIRING_PROOF_INVALID)
    }

    private suspend fun buildTranscript(
        session: PairingSession,
        initiatorPakeShare: ByteArray,
        acceptorPakeShare: ByteArray,
    ): PairingTranscript {
        val ownSignPublicKey = secureStore.secureKeyPair.getSignPublicKeyBytes(secureKeyPairSerializer)
        val ownCryptPublicKey = secureStore.secureKeyPair.getCryptPublicKeyBytes(secureKeyPairSerializer)
        val intentHash = requireNotNull(session.intentHash) { "session without intent hash" }
        val offerHash = requireNotNull(session.offerHash) { "session without offer hash" }
        return when (session.role) {
            PakeRole.ACCEPTOR ->
                PairingTranscript(
                    protocolVersion = PairingV3.PROTOCOL_VERSION,
                    selectedCiphersuite = session.selectedCiphersuite,
                    sessionId = session.sessionIdBytes,
                    tokenGeneration = session.tokenGeneration,
                    initiatorAppInstanceId = session.peerAppInstanceId,
                    acceptorAppInstanceId = appInfo.appInstanceId,
                    initiatorNonce = session.peerNonce,
                    acceptorNonce = session.localNonce,
                    initiatorSignPublicKey = session.peerSignPublicKey,
                    initiatorCryptPublicKey = session.peerCryptPublicKey,
                    acceptorSignPublicKey = ownSignPublicKey,
                    acceptorCryptPublicKey = ownCryptPublicKey,
                    initiatorPakeShare = initiatorPakeShare,
                    acceptorPakeShare = acceptorPakeShare,
                    intentHash = intentHash,
                    offerHash = offerHash,
                    negotiatedCapabilities = session.negotiatedCapabilities,
                )

            PakeRole.INITIATOR ->
                PairingTranscript(
                    protocolVersion = PairingV3.PROTOCOL_VERSION,
                    selectedCiphersuite = session.selectedCiphersuite,
                    sessionId = session.sessionIdBytes,
                    tokenGeneration = session.tokenGeneration,
                    initiatorAppInstanceId = appInfo.appInstanceId,
                    acceptorAppInstanceId = session.peerAppInstanceId,
                    initiatorNonce = session.localNonce,
                    acceptorNonce = session.peerNonce,
                    initiatorSignPublicKey = ownSignPublicKey,
                    initiatorCryptPublicKey = ownCryptPublicKey,
                    acceptorSignPublicKey = session.peerSignPublicKey,
                    acceptorCryptPublicKey = session.peerCryptPublicKey,
                    initiatorPakeShare = initiatorPakeShare,
                    acceptorPakeShare = acceptorPakeShare,
                    intentHash = intentHash,
                    offerHash = offerHash,
                    negotiatedCapabilities = session.negotiatedCapabilities,
                )
        }
    }

    private fun validateIntentShape(
        intent: PairingIntentV3,
        callerAppInstanceId: String,
    ): PairingV3ErrorCode? =
        when {
            intent.targetAppInstanceId != appInfo.appInstanceId -> PairingV3ErrorCode.PAIRING_IDENTITY_INVALID
            intent.initiatorAppInstanceId != callerAppInstanceId -> PairingV3ErrorCode.PAIRING_IDENTITY_INVALID
            intent.initiatorAppInstanceId.isEmpty() ||
                intent.initiatorAppInstanceId.length > MAX_APP_INSTANCE_ID_LENGTH ->
                PairingV3ErrorCode.PAIRING_IDENTITY_INVALID

            intent.initiatorAppInstanceId == appInfo.appInstanceId -> PairingV3ErrorCode.PAIRING_IDENTITY_INVALID
            intent.requestId.size != PairingV3.REQUEST_ID_SIZE -> PairingV3ErrorCode.PAIRING_IDENTITY_INVALID
            intent.initiatorNonce.size != PairingV3.NONCE_SIZE -> PairingV3ErrorCode.PAIRING_IDENTITY_INVALID
            intent.initiatorDisplayName.length > MAX_DISPLAY_NAME_LENGTH -> PairingV3ErrorCode.PAIRING_IDENTITY_INVALID
            intent.initiatorSignPublicKey.isEmpty() ||
                intent.initiatorSignPublicKey.size > MAX_KEY_SIZE ->
                PairingV3ErrorCode.PAIRING_IDENTITY_INVALID

            intent.initiatorCryptPublicKey.isEmpty() ||
                intent.initiatorCryptPublicKey.size > MAX_KEY_SIZE ->
                PairingV3ErrorCode.PAIRING_IDENTITY_INVALID

            intent.signature.isEmpty() || intent.signature.size > MAX_SIGNATURE_SIZE ->
                PairingV3ErrorCode.PAIRING_IDENTITY_INVALID

            intent.supportedCiphersuites.isEmpty() ||
                intent.supportedCiphersuites.size > MAX_CIPHERSUITES ||
                intent.supportedCiphersuites.any { suite -> suite.length > MAX_CIPHERSUITE_LENGTH } ->
                PairingV3ErrorCode.PAIRING_CIPHERSUITE_UNSUPPORTED

            else -> null
        }

    private fun validateProofShape(proof: PairingProofV3): PairingV3ErrorCode? =
        when {
            proof.sessionId.size != PairingV3.SESSION_ID_SIZE -> PairingV3ErrorCode.PAIRING_SESSION_NOT_FOUND
            proof.tokenGeneration < 1L -> PairingV3ErrorCode.PAIRING_PIN_EXPIRED
            !isCanonicalPakeShare(proof.initiatorPakeShare) ->
                PairingV3ErrorCode.PAIRING_PROOF_INVALID

            proof.transcriptHash.size != HASH_SIZE -> PairingV3ErrorCode.PAIRING_PROOF_INVALID
            proof.initiatorKeyConfirmation.size != MAC_SIZE -> PairingV3ErrorCode.PAIRING_PROOF_INVALID
            proof.initiatorIdentitySignature.isEmpty() ||
                proof.initiatorIdentitySignature.size > MAX_SIGNATURE_SIZE ->
                PairingV3ErrorCode.PAIRING_PROOF_INVALID

            else -> null
        }

    private fun validateCommitShape(commit: PairingCommitV3): PairingV3ErrorCode? =
        when {
            commit.sessionId.size != PairingV3.SESSION_ID_SIZE -> PairingV3ErrorCode.PAIRING_SESSION_NOT_FOUND
            commit.transcriptHash.size != HASH_SIZE -> PairingV3ErrorCode.PAIRING_PROOF_INVALID
            commit.commitMac.size != MAC_SIZE -> PairingV3ErrorCode.PAIRING_PROOF_INVALID
            else -> null
        }

    private suspend fun validateOffer(
        intent: PairingIntentV3,
        intentHash: ByteArray,
        offer: PairingOfferV3,
    ): PairingV3ErrorCode? {
        if (offer.protocolVersion != PairingV3.PROTOCOL_VERSION) {
            return PairingV3ErrorCode.PAIRING_VERSION_UNSUPPORTED
        }
        if (offer.selectedCiphersuite !in intent.supportedCiphersuites) {
            return PairingV3ErrorCode.PAIRING_CIPHERSUITE_UNSUPPORTED
        }
        if (offer.sessionId.size != PairingV3.SESSION_ID_SIZE ||
            offer.acceptorNonce.size != PairingV3.NONCE_SIZE ||
            offer.tokenGeneration < 1L ||
            !isCanonicalPakeShare(offer.acceptorPakeShare) ||
            !offer.requestHash.contentEquals(intentHash) ||
            offer.initiatorAppInstanceId != intent.initiatorAppInstanceId ||
            offer.acceptorAppInstanceId != intent.targetAppInstanceId
        ) {
            return PairingV3ErrorCode.PAIRING_TRANSCRIPT_MISMATCH
        }
        if (offer.acceptorSignPublicKey.isEmpty() ||
            offer.acceptorSignPublicKey.size > MAX_KEY_SIZE ||
            offer.acceptorCryptPublicKey.isEmpty() ||
            offer.acceptorCryptPublicKey.size > MAX_KEY_SIZE ||
            offer.signature.isEmpty() ||
            offer.signature.size > MAX_SIGNATURE_SIZE
        ) {
            return PairingV3ErrorCode.PAIRING_IDENTITY_INVALID
        }
        val signatureValid =
            runCatching {
                val acceptorSignKey = secureKeyPairSerializer.decodeSignPublicKey(offer.acceptorSignPublicKey)
                runCatching { secureKeyPairSerializer.decodeCryptPublicKey(offer.acceptorCryptPublicKey) }
                    .getOrNull() ?: return@runCatching false
                CryptographyUtils.verifyData(acceptorSignKey, offer.signature) {
                    PairingTranscriptCodec.encodeOfferSignaturePayload(offer)
                }
            }.getOrDefault(false)
        if (!signatureValid) {
            return PairingV3ErrorCode.PAIRING_IDENTITY_INVALID
        }
        return null
    }

    private fun isCanonicalPakeShare(share: ByteArray): Boolean =
        share.size == PairingV3.PAKE_SHARE_SIZE &&
            share[0] == PairingV3.PAKE_SHARE_UNCOMPRESSED_PREFIX

    /**
     * The acceptor's `pinExpiresAt` is on the acceptor's clock, which may be
     * skewed. Locally it only feeds countdown display and local hygiene gates, so
     * the initiator projects its own full PIN lifetime instead of trusting the
     * peer clock — the acceptor remains the enforcement point for real expiry.
     */
    @Suppress("UnusedParameter")
    private fun clampPinExpiry(
        peerPinExpiresAt: Long,
        now: Long,
    ): Long = now + pinLifetime.inWholeMilliseconds

    private fun stateRefusalCode(state: PairingSessionState): PairingV3ErrorCode =
        when (state) {
            PairingSessionState.REJECTED -> PairingV3ErrorCode.PAIRING_REJECTED
            PairingSessionState.CANCELLED -> PairingV3ErrorCode.PAIRING_CANCELLED
            PairingSessionState.EXPIRED -> PairingV3ErrorCode.PAIRING_SESSION_EXPIRED
            PairingSessionState.TRUSTED,
            PairingSessionState.FAILED,
            -> PairingV3ErrorCode.PAIRING_SESSION_CONSUMED

            else -> PairingV3ErrorCode.PAIRING_INVALID_STATE
        }

    private fun randomBytes(size: Int): ByteArray = CryptographyRandom.nextBytes(size)

    private fun toHex(bytes: ByteArray): String =
        bytes.joinToString("") { byte -> (byte.toInt() and 0xFF).toString(16).padStart(2, '0') }

    private fun shortId(sessionId: String): String = sessionId.take(8)

    // endregion

    companion object {
        /** Frozen as empty until the first real capability negotiation ships. */
        private val NEGOTIATED_CAPABILITIES: List<String> = emptyList()

        private const val MAX_SESSION_ID_COLLISION_RETRIES = 3
        private const val COMMIT_ATTEMPTS = 3
        private val COMMIT_RETRY_DELAY = 300L.milliseconds
        private val MAINTENANCE_INTERVAL = 1.seconds
        private val TERMINAL_RETENTION = 5.minutes

        private const val HASH_SIZE = 32
        private const val MAC_SIZE = 32
        private const val MAX_KEY_SIZE = 256
        private const val MAX_SIGNATURE_SIZE = 256
        private const val MAX_DISPLAY_NAME_LENGTH = 128
        private const val MAX_APP_INSTANCE_ID_LENGTH = 64
        private const val MAX_CIPHERSUITES = 8
        private const val MAX_CIPHERSUITE_LENGTH = 64
    }
}
