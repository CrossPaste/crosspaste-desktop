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
    private val maxProofFailures: Int = PairingV3.MAX_ACCEPTOR_PROOF_FAILURES,
    private val nowEpochMillis: () -> Long = { DateUtils.nowEpochMilliseconds() },
) {

    private val _openUntil = MutableStateFlow(0L)

    /** Epoch millis until which the window is open; 0 when closed. UI observes this. */
    val openUntil: StateFlow<Long> = _openUntil.asStateFlow()

    private val _locked = MutableStateFlow(false)

    /** True once the failure budget is spent; cleared only by a LOCAL open. UI observes this. */
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    // Accumulates across peers/generations so identity/fingerprint rotation cannot
    // dodge the ceiling. Atomic because proof handling runs concurrently per session.
    private val proofFailures = atomic(0)

    /**
     * Opens (or renews) the window. A LOCAL open always succeeds and resets the
     * failure budget and lock. A REMOTE open is refused (returns false, no-op) while
     * locked, and otherwise renews without touching the budget.
     */
    fun open(
        source: WindowOpenSource,
        duration: Duration = DEFAULT_WINDOW_DURATION,
    ): Boolean {
        when (source) {
            WindowOpenSource.LOCAL -> {
                proofFailures.value = 0
                _locked.value = false
            }
            WindowOpenSource.REMOTE ->
                if (_locked.value) {
                    return false
                }
        }
        _openUntil.value = nowEpochMillis() + duration.inWholeMilliseconds
        return true
    }

    /**
     * Extends an already-open window without resetting the failure budget or clearing
     * a lock — used by the local UI renewal loop, so keeping the Add Device screen
     * open does not silently refill an attacker's guess budget. No-op while locked.
     */
    fun extend(duration: Duration = DEFAULT_WINDOW_DURATION) {
        if (_locked.value) {
            return
        }
        _openUntil.value = nowEpochMillis() + duration.inWholeMilliseconds
    }

    /**
     * Records one proof failure against the cumulative budget. When the budget is
     * spent the window closes and locks until a LOCAL open resets it. Returns true
     * when this failure triggered the lock.
     */
    fun recordProofFailure(): Boolean {
        if (proofFailures.incrementAndGet() >= maxProofFailures) {
            _locked.value = true
            _openUntil.value = 0L
            return true
        }
        return false
    }

    fun close() {
        _openUntil.value = 0L
    }

    fun isOpen(): Boolean = nowEpochMillis() < _openUntil.value

    companion object {
        val DEFAULT_WINDOW_DURATION: Duration = 5.minutes
    }
}
