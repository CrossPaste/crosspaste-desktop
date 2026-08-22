package com.crosspaste.pairing.v3

import com.crosspaste.utils.DateUtils
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** Who is asking to open/extend the acceptance window. */
enum class WindowOpenSource {
    /** A local user gesture (the Add Device screen is in the foreground). Trusted:
     *  it resets the proof-failure budget and clears a lock. */
    LOCAL,

    /** A remote `/sync/showPairingCode` request. Untrusted: honoured only while the
     *  window is not locked, and never resets the failure budget. */
    REMOTE,
}

/**
 * Explicit pairing-acceptance window on the acceptor.
 *
 * Incoming v3 intents are only accepted while this window is open — typically while
 * the user has the Add Device screen visible. This prevents arbitrary local-network
 * clients from continuously creating PIN cards or exhausting session capacity.
 *
 * The window also carries a cumulative proof-failure budget
 * ([PairingV3.MAX_ACCEPTOR_PROOF_FAILURES], across all peers and generations). A
 * failed proof rotates a fresh PIN immediately, so per-generation attempt caps alone
 * put no ceiling on the total number of online PIN guesses. When the budget is spent
 * the window closes and locks: a [WindowOpenSource.REMOTE] open is refused, so an
 * unattended attacker cannot re-open it via `/sync/showPairingCode` and keep guessing.
 * Only a [WindowOpenSource.LOCAL] open (a user re-entering the Add Device screen)
 * clears the lock and resets the budget.
 */
class PairingAcceptanceWindow(
    internal val maxProofFailures: Int = PairingV3.MAX_ACCEPTOR_PROOF_FAILURES,
    private val nowEpochMillis: () -> Long = { DateUtils.nowEpochMilliseconds() },
) {

    private data class WindowState(
        val openUntil: Long = 0L,
        val proofFailures: Int = 0,
        val inFlightProofs: Int = 0,
        val locked: Boolean = false,
    )

    init {
        require(maxProofFailures > 0) { "maxProofFailures must be positive" }
    }

    // One atomic state is the security source of truth. The public flows below are
    // observational projections for Compose; authorization never reads them.
    private val state = atomic(WindowState())

    private val _openUntil = MutableStateFlow(0L)

    /** Epoch millis until which the window is open; 0 when closed. UI observes this. */
    val openUntil: StateFlow<Long> = _openUntil.asStateFlow()

    private val _locked = MutableStateFlow(false)

    /** True once the failure budget is spent; cleared only by a LOCAL open. UI observes this. */
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    /**
     * Opens (or renews) the window. A LOCAL open always succeeds and resets the
     * failure budget and lock. A REMOTE open is refused (returns false, no-op) while
     * locked, and otherwise renews without touching the budget.
     */
    fun open(
        source: WindowOpenSource,
        duration: Duration = DEFAULT_WINDOW_DURATION,
    ): Boolean {
        val openUntil = nowEpochMillis() + duration.inWholeMilliseconds
        while (true) {
            val current = state.value
            if (source == WindowOpenSource.REMOTE && current.locked) {
                return false
            }
            val updated =
                when (source) {
                    WindowOpenSource.LOCAL ->
                        current.copy(
                            openUntil = openUntil,
                            proofFailures = 0,
                            locked = false,
                        )
                    WindowOpenSource.REMOTE -> current.copy(openUntil = openUntil)
                }
            if (state.compareAndSet(current, updated)) {
                publishState()
                return true
            }
        }
    }

    /**
     * Extends an already-open window without resetting the failure budget or clearing
     * a lock — used by the local UI renewal loop, so keeping the Add Device screen
     * open does not silently refill an attacker's guess budget. No-op while locked.
     */
    fun extend(duration: Duration = DEFAULT_WINDOW_DURATION) {
        val openUntil = nowEpochMillis() + duration.inWholeMilliseconds
        while (true) {
            val current = state.value
            if (current.locked) {
                return
            }
            if (state.compareAndSet(current, current.copy(openUntil = openUntil))) {
                publishState()
                return
            }
        }
    }

    /**
     * Reserves one online proof attempt. Failed and in-flight attempts jointly consume
     * the ceiling, so concurrent sessions cannot overshoot it. A successful proof (or
     * a request rejected before cryptographic verification) releases its reservation.
     */
    internal fun tryBeginProofAttempt(): ProofAttempt? {
        while (true) {
            val current = state.value
            if (current.locked || current.proofFailures + current.inFlightProofs >= maxProofFailures) {
                return null
            }
            val updated = current.copy(inFlightProofs = current.inFlightProofs + 1)
            if (state.compareAndSet(current, updated)) {
                return ProofAttempt(this)
            }
        }
    }

    fun close() {
        updateState { current -> current.copy(openUntil = 0L) }
    }

    fun isOpen(): Boolean {
        val current = state.value
        return !current.locked && nowEpochMillis() < current.openUntil
    }

    fun isLocked(): Boolean = state.value.locked

    private fun completeProofAttempt(failed: Boolean): Boolean {
        while (true) {
            val current = state.value
            check(current.inFlightProofs > 0) { "proof attempt completed without a reservation" }
            val proofFailures = current.proofFailures + if (failed) 1 else 0
            val locked = current.locked || proofFailures >= maxProofFailures
            val updated =
                current.copy(
                    openUntil = if (locked) 0L else current.openUntil,
                    proofFailures = proofFailures,
                    inFlightProofs = current.inFlightProofs - 1,
                    locked = locked,
                )
            if (state.compareAndSet(current, updated)) {
                publishState()
                return !current.locked && locked
            }
        }
    }

    private inline fun updateState(transform: (WindowState) -> WindowState) {
        while (true) {
            val current = state.value
            if (state.compareAndSet(current, transform(current))) {
                publishState()
                return
            }
        }
    }

    // A publisher that raced with a newer transition loops until both projections
    // reflect the latest atomic state, so observers cannot remain permanently stale.
    private fun publishState() {
        while (true) {
            val snapshot = state.value
            _openUntil.value = snapshot.openUntil
            _locked.value = snapshot.locked
            if (state.value === snapshot) {
                return
            }
        }
    }

    internal class ProofAttempt internal constructor(
        private val window: PairingAcceptanceWindow,
    ) {
        private val active = atomic(true)

        /** Converts this reservation into a failed guess. Returns true when it locks the window. */
        fun recordFailure(): Boolean =
            active.compareAndSet(expect = true, update = false) && window.completeProofAttempt(failed = true)

        /** Releases a successful or otherwise unevaluated attempt. Idempotent. */
        fun release() {
            if (active.compareAndSet(expect = true, update = false)) {
                window.completeProofAttempt(failed = false)
            }
        }
    }

    companion object {
        val DEFAULT_WINDOW_DURATION: Duration = 5.minutes
    }
}
