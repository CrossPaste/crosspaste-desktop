package com.crosspaste.app

import kotlinx.coroutines.flow.StateFlow

interface AppTokenApi {

    val token: StateFlow<CharArray>

    val showToken: StateFlow<Boolean>

    val refreshProgress: StateFlow<Float>

    val refresh: StateFlow<Boolean>

    val pendingVerifiers: StateFlow<Set<String>>

    fun sameToken(token: Int): Boolean

    fun setSASToken(sas: Int)

    fun startRefresh(showToken: Boolean)

    fun stopRefresh(hideToken: Boolean)

    /**
     * Atomically registers [appInstanceId] as a verifier owner and starts one
     * refresh count. Repeated acquisition by the same verifier only reopens the
     * overlay; it never increments the count twice.
     */
    fun acquireVerifier(
        appInstanceId: String,
        showToken: Boolean = true,
    )

    /**
     * Atomically releases the token-refresh count owned by [appInstanceId]'s
     * pending-verifier entry: the count is decremented ONLY when the verifier
     * was still pending, so the unified server release path and the UI reap can
     * both call this without double-decrementing (a second decrement would
     * steal a count held by another concurrently pairing device). [hideToken]
     * additionally hides the token overlay (user-facing dismissal).
     */
    fun releaseVerifier(
        appInstanceId: String,
        hideToken: Boolean = false,
    )

    fun showPairingCode()
}
