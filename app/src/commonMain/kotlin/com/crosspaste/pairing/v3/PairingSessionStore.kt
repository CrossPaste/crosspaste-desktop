package com.crosspaste.pairing.v3

import com.crosspaste.utils.DateUtils
import io.ktor.util.collections.ConcurrentMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile

sealed interface PairingSessionCreateResult {

    data class Created(
        val session: PairingSession,
    ) : PairingSessionCreateResult

    /** Byte-identical intent retry (same request id AND same canonical intent hash): idempotent. */
    data class Duplicate(
        val existing: PairingSession,
    ) : PairingSessionCreateResult

    /** A different active session already exists for the same full signing-key fingerprint. */
    data class PeerAlreadyActive(
        val existing: PairingSession,
    ) : PairingSessionCreateResult

    /** Same app instance id but a different signing key: surfaced as an explicit identity conflict. */
    data class IdentityConflict(
        val existing: PairingSession,
    ) : PairingSessionCreateResult

    /**
     * This exact intent already drove a session to a terminal state: a completed,
     * rejected, cancelled, expired, or failed intent can never be consumed again.
     */
    data object IntentConsumed : PairingSessionCreateResult

    /** The session id is already present (any state). A session id is never reused or overwritten. */
    data object SessionIdCollision : PairingSessionCreateResult

    data object CapacityExceeded : PairingSessionCreateResult
}

sealed interface PairingSessionTransitionResult {

    data class Success(
        val session: PairingSession,
    ) : PairingSessionTransitionResult

    data class InvalidState(
        val actual: PairingSessionState,
    ) : PairingSessionTransitionResult

    /** The action returned a state that is not a legal successor; nothing was stored. */
    data class IllegalTransition(
        val from: PairingSessionState,
        val to: PairingSessionState,
    ) : PairingSessionTransitionResult

    /**
     * The target state requires cryptographic evidence (transcript hash and derived
     * session keys) that the updated session does not carry; nothing was stored.
     */
    data class EvidenceRequired(
        val target: PairingSessionState,
    ) : PairingSessionTransitionResult

    data object NotFound : PairingSessionTransitionResult
}

sealed interface PairingBeginProofResult {

    data class Proceed(
        val session: PairingSession,
    ) : PairingBeginProofResult

    data object WrongGeneration : PairingBeginProofResult

    data object PinExpired : PairingBeginProofResult

    data object AttemptsExhausted : PairingBeginProofResult

    /**
     * The generation has no published PAKE share / signed offer yet (mid-rotation
     * or preparation failure): no proof can legitimately reference it.
     */
    data object GenerationNotReady : PairingBeginProofResult

    data class InvalidState(
        val actual: PairingSessionState,
    ) : PairingBeginProofResult

    data object NotFound : PairingBeginProofResult
}

sealed interface PairingCompleteProofResult {

    data class Confirmed(
        val session: PairingSession,
    ) : PairingCompleteProofResult

    data object WrongGeneration : PairingCompleteProofResult

    /** The negotiation outlived the frozen generation deadline; it must not confirm. */
    data object DeadlineExceeded : PairingCompleteProofResult

    data class InvalidState(
        val actual: PairingSessionState,
    ) : PairingCompleteProofResult

    data object NotFound : PairingCompleteProofResult
}

sealed interface PairingRotateResult {

    data class Rotated(
        val session: PairingSession,
    ) : PairingRotateResult

    /** A PAKE first message arrived before expiration; the generation is frozen for the grace interval. */
    data object Frozen : PairingRotateResult

    /** The caller prepared material for a generation that is no longer current. */
    data object StaleGeneration : PairingRotateResult

    data class InvalidState(
        val actual: PairingSessionState,
    ) : PairingRotateResult

    data object NotFound : PairingRotateResult
}

/**
 * Bounded, thread-safe store of pairing sessions.
 *
 * Transitions for one session are serialized by a per-session mutex, and every
 * store commit (session replacement + snapshot publication) happens inside one
 * global critical section so the published [uiSessionsFlow] can never move
 * backwards. Lock order is always session mutex → [storeMutex], never reversed.
 *
 * State changes are validated against [PairingSessionState.successors]: terminal
 * states are absorbing and TRUSTED is only reachable from COMMITTING. States from
 * PEER_CONFIRMED onward additionally require the session to carry its cryptographic
 * evidence (transcript hash + derived session keys); protocol steps use the
 * semantic entry points ([beginProof], [completeProof], [beginCommit],
 * [completeTrust], [reject], [cancel], [fail]) rather than raw transitions.
 */
class PairingSessionStore(
    private val maxActiveIncoming: Int = PairingV3.DEFAULT_MAX_ACTIVE_INCOMING_SESSIONS,
    private val maxIntentTombstones: Int = PairingV3.DEFAULT_MAX_INTENT_TOMBSTONES,
    private val intentTombstoneTtlMillis: Long = PairingV3.DEFAULT_INTENT_TOMBSTONE_TTL.inWholeMilliseconds,
    private val nowEpochMillis: () -> Long = { DateUtils.nowEpochMilliseconds() },
) {

    private class StoredSession(
        @Volatile var session: PairingSession,
    ) {
        val mutex = Mutex()
    }

    private class IntentTombstone(
        val recordedAt: Long,
    )

    private val sessions = ConcurrentMap<String, StoredSession>()

    /**
     * Consumed-intent index, keyed by canonical intent hash. Independent of the
     * session map on purpose: dismissing or pruning a terminal session card must
     * not erase the replay record. Bounded by [maxIntentTombstones] and
     * [intentTombstoneTtlMillis]; guarded by [storeMutex].
     */
    private val consumedIntents = mutableMapOf<String, IntentTombstone>()

    /** Guards session-map mutation, tombstones, and snapshot publication as one atomic commit. */
    private val storeMutex = Mutex()

    private val _uiSessionsFlow = MutableStateFlow<List<PairingSessionUiState>>(emptyList())

    /** Live sanitized snapshot for UI session cards, ordered by creation time. */
    val uiSessionsFlow: StateFlow<List<PairingSessionUiState>> = _uiSessionsFlow.asStateFlow()

    /**
     * For the protocol service only: the returned session carries key material and
     * mutable arrays that must not be handed to UI or mutated outside a transition.
     */
    fun get(sessionId: String): PairingSession? = sessions[sessionId]?.session

    fun activeSessions(): List<PairingSession> = snapshot().filter { session -> session.isActive }

    /**
     * Applies capacity and deduplication policy for incoming (acceptor-role) sessions.
     * Peer identity comparisons use the FULL signing-key fingerprint. Initiator-role
     * sessions bypass the peer policies but never an existing session id: a session
     * id is unique for the store's lifetime and is never overwritten.
     */
    suspend fun create(session: PairingSession): PairingSessionCreateResult =
        storeMutex.withLock {
            if (sessions.containsKey(session.sessionId)) {
                return@withLock PairingSessionCreateResult.SessionIdCollision
            }
            if (session.role == PakeRole.ACCEPTOR) {
                evictDueTombstonesLocked()
                session.intentHash?.let { intentHash ->
                    if (consumedIntents.containsKey(toHex(intentHash))) {
                        return@withLock PairingSessionCreateResult.IntentConsumed
                    }
                }

                val activeIncoming =
                    sessions.values
                        .map { stored -> stored.session }
                        .filter { active -> active.isActive && active.role == PakeRole.ACCEPTOR }

                activeIncoming
                    .firstOrNull { active -> active.peerKeyFingerprint == session.peerKeyFingerprint }
                    ?.let { existing ->
                        val sameIntent =
                            existing.requestId == session.requestId &&
                                existing.intentHash != null &&
                                session.intentHash != null &&
                                existing.intentHash.contentEquals(session.intentHash)
                        return@withLock if (sameIntent) {
                            PairingSessionCreateResult.Duplicate(existing)
                        } else {
                            PairingSessionCreateResult.PeerAlreadyActive(existing)
                        }
                    }

                activeIncoming
                    .firstOrNull { active -> active.peerAppInstanceId == session.peerAppInstanceId }
                    ?.let { existing ->
                        return@withLock PairingSessionCreateResult.IdentityConflict(existing)
                    }

                if (activeIncoming.size >= maxActiveIncoming) {
                    return@withLock PairingSessionCreateResult.CapacityExceeded
                }
            }
            sessions[session.sessionId] = StoredSession(session)
            publishLocked()
            PairingSessionCreateResult.Created(session)
        }

    /**
     * Serialized, graph-checked state transition. Internal on purpose: protocol
     * steps go through the semantic entry points, which layer the required
     * cryptographic pre-conditions on top of this. [action] runs under the session
     * mutex and returns the updated session; it is only invoked when the current
     * state is in [expected]. Terminal states are absorbing regardless of [expected].
     * An illegal target state, or a confirmation-or-later target without evidence,
     * is rejected without storing anything. On a terminal transition, secrets are
     * cleared on BOTH the previous instance and the returned instance, so an action
     * that drops references cannot bypass zeroization.
     */
    internal suspend fun transition(
        sessionId: String,
        expected: Set<PairingSessionState>,
        action: suspend (PairingSession) -> PairingSession,
    ): PairingSessionTransitionResult {
        val stored = sessions[sessionId] ?: return PairingSessionTransitionResult.NotFound
        return stored.mutex.withLock {
            val current = stored.session
            when {
                current.state.isTerminal || current.state !in expected ->
                    PairingSessionTransitionResult.InvalidState(current.state)

                else -> {
                    val updated = action(current)
                    when {
                        !current.state.canTransitionTo(updated.state) ->
                            PairingSessionTransitionResult.IllegalTransition(current.state, updated.state)

                        updated.state in evidenceStates &&
                            (updated.transcriptHash == null || updated.sessionKeys == null) ->
                            PairingSessionTransitionResult.EvidenceRequired(updated.state)

                        else -> {
                            val result =
                                if (updated.state.isTerminal) {
                                    current.clearSecrets()
                                    updated.clearSecrets()
                                } else {
                                    updated
                                }
                            if (commit(stored, result)) {
                                PairingSessionTransitionResult.Success(result)
                            } else {
                                PairingSessionTransitionResult.NotFound
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Gate for an incoming PAKE first message: validates the generation, its
     * readiness (a published PAKE share and signed offer), the PIN lifetime, and
     * the per-generation attempt budget, then freezes the generation until
     * `pinExpiresAt + graceMillis` — the design grants the in-flight negotiation
     * a bounded grace period BEYOND the PIN lifetime — and moves to PAKE_NEGOTIATING.
     */
    suspend fun beginProof(
        sessionId: String,
        generation: Long,
        graceMillis: Long = PairingV3.DEFAULT_GENERATION_GRACE.inWholeMilliseconds,
    ): PairingBeginProofResult {
        val stored = sessions[sessionId] ?: return PairingBeginProofResult.NotFound
        return stored.mutex.withLock {
            val current = stored.session
            val now = nowEpochMillis()
            when {
                current.state != PairingSessionState.PIN_AVAILABLE ->
                    PairingBeginProofResult.InvalidState(current.state)

                current.tokenGeneration != generation -> PairingBeginProofResult.WrongGeneration

                current.proofAttempts >= PairingV3.MAX_PROOF_ATTEMPTS_PER_GENERATION ->
                    PairingBeginProofResult.AttemptsExhausted

                current.pin == null ||
                    current.pakeSession == null ||
                    current.localPakeShare == null ||
                    current.offerHash == null -> PairingBeginProofResult.GenerationNotReady

                !current.isPinUsable(now) -> PairingBeginProofResult.PinExpired

                else -> {
                    val updated =
                        current.copy(
                            state = PairingSessionState.PAKE_NEGOTIATING,
                            generationFrozenUntil = current.pinExpiresAt + graceMillis,
                        )
                    if (commit(stored, updated)) {
                        PairingBeginProofResult.Proceed(updated)
                    } else {
                        PairingBeginProofResult.NotFound
                    }
                }
            }
        }
    }

    /**
     * Confirms the peer proof: re-validates the generation and the frozen deadline
     * (a negotiation that outlived its grace must not confirm), attaches the
     * cryptographic evidence, and moves to PEER_CONFIRMED. The caller has already
     * verified the PAKE confirmation MAC and identity signature; [transcriptHash]
     * and [sessionKeys] are the evidence this store requires from here on.
     */
    suspend fun completeProof(
        sessionId: String,
        generation: Long,
        transcriptHash: ByteArray,
        sessionKeys: PairingSessionKeys,
        peerPakeShare: ByteArray,
    ): PairingCompleteProofResult {
        val stored = sessions[sessionId] ?: return PairingCompleteProofResult.NotFound
        return stored.mutex.withLock {
            val current = stored.session
            val now = nowEpochMillis()
            when {
                current.state != PairingSessionState.PAKE_NEGOTIATING ->
                    PairingCompleteProofResult.InvalidState(current.state)

                current.tokenGeneration != generation -> PairingCompleteProofResult.WrongGeneration

                now >= current.generationFrozenUntil -> PairingCompleteProofResult.DeadlineExceeded

                else -> {
                    val updated =
                        current.copy(
                            state = PairingSessionState.PEER_CONFIRMED,
                            transcriptHash = transcriptHash,
                            sessionKeys = sessionKeys,
                            peerPakeShare = peerPakeShare,
                        )
                    if (commit(stored, updated)) {
                        PairingCompleteProofResult.Confirmed(updated)
                    } else {
                        PairingCompleteProofResult.NotFound
                    }
                }
            }
        }
    }

    /** PEER_CONFIRMED → COMMITTING. Evidence presence is enforced by the transition guard. */
    suspend fun beginCommit(sessionId: String): PairingSessionTransitionResult =
        transition(sessionId, setOf(PairingSessionState.PEER_CONFIRMED)) { current ->
            current.copy(state = PairingSessionState.COMMITTING)
        }

    /**
     * COMMITTING → TRUSTED, after the caller verified the commit MAC and persisted
     * the peer key. The returned session already has its secrets cleared.
     */
    suspend fun completeTrust(sessionId: String): PairingSessionTransitionResult =
        transition(sessionId, setOf(PairingSessionState.COMMITTING)) { current ->
            current.copy(state = PairingSessionState.TRUSTED)
        }

    suspend fun reject(sessionId: String): PairingSessionTransitionResult =
        abort(sessionId, PairingSessionState.REJECTED)

    suspend fun cancel(sessionId: String): PairingSessionTransitionResult =
        abort(sessionId, PairingSessionState.CANCELLED)

    suspend fun fail(sessionId: String): PairingSessionTransitionResult = abort(sessionId, PairingSessionState.FAILED)

    private suspend fun abort(
        sessionId: String,
        target: PairingSessionState,
    ): PairingSessionTransitionResult =
        transition(sessionId, nonTerminalStates) { current ->
            current.copy(state = target)
        }

    /**
     * A failed proof invalidates the current generation immediately: the attempt is
     * recorded, PIN and PAKE state are destroyed, and the session returns to
     * PIN_AVAILABLE awaiting rotation — nothing of this generation stays usable.
     */
    suspend fun recordProofFailure(sessionId: String): PairingSessionTransitionResult =
        transition(sessionId, setOf(PairingSessionState.PAKE_NEGOTIATING)) { current ->
            runCatching { current.pin?.fill('\u0000') }
            runCatching { current.pakeSession?.destroy() }
            runCatching { current.sessionKeys?.clear() }
            current.copy(
                state = PairingSessionState.PIN_AVAILABLE,
                proofAttempts = current.proofAttempts + 1,
                pin = null,
                pinExpiresAt = nowEpochMillis(),
                generationFrozenUntil = 0L,
                pakeSession = null,
                sessionKeys = null,
                localPakeShare = null,
                peerPakeShare = null,
                offerHash = null,
                transcriptHash = null,
            )
        }

    /**
     * Atomically publishes a COMPLETE new PIN generation. The caller prepares the
     * whole generation first — fresh PIN, PAKE session and share, signed offer and
     * its canonical hash — and this call publishes it in one step, so there is
     * never an observable generation that [beginProof] would have to half-trust.
     * [expectedGeneration] guards against publishing material prepared against a
     * stale read. Refused while the generation is frozen by an in-flight negotiation.
     */
    suspend fun rotateGeneration(
        sessionId: String,
        expectedGeneration: Long,
        newPin: CharArray,
        pinExpiresAt: Long,
        pakeSession: PakeSession,
        localPakeShare: ByteArray,
        offerHash: ByteArray,
    ): PairingRotateResult {
        val stored = sessions[sessionId] ?: return PairingRotateResult.NotFound
        return stored.mutex.withLock {
            val current = stored.session
            val now = nowEpochMillis()
            when {
                current.state != PairingSessionState.PIN_AVAILABLE &&
                    current.state != PairingSessionState.PAKE_NEGOTIATING ->
                    PairingRotateResult.InvalidState(current.state)

                now < current.generationFrozenUntil -> PairingRotateResult.Frozen

                current.tokenGeneration != expectedGeneration -> PairingRotateResult.StaleGeneration

                else -> {
                    runCatching { current.pin?.fill('\u0000') }
                    runCatching { current.pakeSession?.destroy() }
                    runCatching { current.sessionKeys?.clear() }
                    val updated =
                        current.copy(
                            state = PairingSessionState.PIN_AVAILABLE,
                            tokenGeneration = current.tokenGeneration + 1,
                            pin = newPin,
                            pinExpiresAt = pinExpiresAt,
                            generationFrozenUntil = 0L,
                            proofAttempts = 0,
                            pakeSession = pakeSession,
                            sessionKeys = null,
                            localPakeShare = localPakeShare,
                            peerPakeShare = null,
                            offerHash = offerHash,
                            transcriptHash = null,
                        )
                    if (commit(stored, updated)) {
                        PairingRotateResult.Rotated(updated)
                    } else {
                        PairingRotateResult.NotFound
                    }
                }
            }
        }
    }

    /** Moves every overdue active session to EXPIRED and returns the expired sessions. */
    suspend fun expireDueSessions(): List<PairingSession> {
        val now = nowEpochMillis()
        val expired = mutableListOf<PairingSession>()
        for (sessionId in sessions.keys.toList()) {
            val candidate = sessions[sessionId]?.session ?: continue
            if (candidate.isActive && now >= candidate.expiresAt) {
                val result =
                    transition(sessionId, nonTerminalStates) { session ->
                        session.copy(state = PairingSessionState.EXPIRED)
                    }
                if (result is PairingSessionTransitionResult.Success) {
                    expired.add(result.session)
                }
            }
        }
        return expired
    }

    /** Removes a terminal session (UI dismiss). Active sessions are never removed silently. */
    suspend fun removeTerminal(sessionId: String): Boolean =
        storeMutex.withLock {
            val stored = sessions[sessionId] ?: return@withLock false
            if (stored.session.isActive) return@withLock false
            sessions.remove(sessionId)
            publishLocked()
            true
        }

    /** Removes terminal sessions older than [retentionMillis] since creation. */
    suspend fun pruneTerminal(retentionMillis: Long) {
        storeMutex.withLock {
            val now = nowEpochMillis()
            val stale =
                sessions.values
                    .map { stored -> stored.session }
                    .filter { session -> !session.isActive && now - session.createdAt >= retentionMillis }
            if (stale.isEmpty()) return@withLock
            stale.forEach { session -> sessions.remove(session.sessionId) }
            publishLocked()
        }
    }

    /**
     * Atomic commit under [storeMutex]. Returns false without writing when the map
     * no longer holds this exact [StoredSession] (removed concurrently): a commit
     * must never resurrect a detached session or publish a stale snapshot as fresh.
     */
    private suspend fun commit(
        stored: StoredSession,
        updated: PairingSession,
    ): Boolean =
        storeMutex.withLock {
            if (sessions[updated.sessionId] !== stored) {
                return@withLock false
            }
            stored.session = updated
            if (updated.state.isTerminal && updated.role == PakeRole.ACCEPTOR) {
                updated.intentHash?.let { intentHash -> recordTombstoneLocked(toHex(intentHash)) }
            }
            publishLocked()
            true
        }

    /** Must only be called while holding [storeMutex]. */
    private fun recordTombstoneLocked(intentHashHex: String) {
        evictDueTombstonesLocked()
        if (consumedIntents.size >= maxIntentTombstones && intentHashHex !in consumedIntents) {
            consumedIntents
                .minByOrNull { entry -> entry.value.recordedAt }
                ?.let { oldest -> consumedIntents.remove(oldest.key) }
        }
        consumedIntents.getOrPut(intentHashHex) { IntentTombstone(nowEpochMillis()) }
    }

    /** Must only be called while holding [storeMutex]. */
    private fun evictDueTombstonesLocked() {
        val cutoff = nowEpochMillis() - intentTombstoneTtlMillis
        val dueKeys = consumedIntents.filter { entry -> entry.value.recordedAt <= cutoff }.keys.toList()
        dueKeys.forEach { key -> consumedIntents.remove(key) }
    }

    private fun toHex(bytes: ByteArray): String =
        bytes.joinToString("") { byte -> (byte.toInt() and 0xFF).toString(16).padStart(2, '0') }

    private fun snapshot(): List<PairingSession> =
        sessions.values.map { stored -> stored.session }.sortedBy { session -> session.createdAt }

    /** Must only be called while holding [storeMutex]. */
    private fun publishLocked() {
        _uiSessionsFlow.value = snapshot().map { session -> session.toUiState() }
    }

    companion object {
        private val nonTerminalStates =
            PairingSessionState.entries.filter { state -> !state.isTerminal }.toSet()

        /** States whose entry requires transcript hash + session keys on the session. */
        private val evidenceStates =
            setOf(
                PairingSessionState.PEER_CONFIRMED,
                PairingSessionState.COMMITTING,
                PairingSessionState.TRUSTED,
            )
    }
}
