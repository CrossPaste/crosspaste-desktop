package com.crosspaste.pairing.v3

import com.crosspaste.utils.DateUtils
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The identity dimensions of one pairing attempt. Provide every dimension that is
 * known at the call site; unknown dimensions are null and simply not checked.
 */
data class PairingAttemptSource(
    val peerKeyFingerprint: String? = null,
    val peerAppInstanceId: String? = null,
    val remoteAddress: String? = null,
)

/**
 * Sliding-window rate limiter for pairing attempts.
 *
 * One [tryAcquire] call checks the per-fingerprint, per-app-instance, per-address,
 * and global windows atomically under a single mutex: either the attempt fits in
 * every applicable window and is recorded once in each (global counted once), or
 * nothing is consumed. This makes identity rotation ineffective (global window)
 * without letting one attempt double-count or partially consume budgets.
 */
class PairingRateLimiter(
    private val maxPerKey: Int = DEFAULT_MAX_PER_KEY,
    private val maxGlobal: Int = DEFAULT_MAX_GLOBAL,
    private val windowMillis: Long = DEFAULT_WINDOW_MILLIS,
    private val nowEpochMillis: () -> Long = { DateUtils.nowEpochMilliseconds() },
) {

    private val mutex = Mutex()

    private val attemptsByKey = mutableMapOf<String, MutableList<Long>>()

    private val globalAttempts = mutableListOf<Long>()

    suspend fun tryAcquire(source: PairingAttemptSource): Boolean =
        mutex.withLock {
            val now = nowEpochMillis()
            val cutoff = now - windowMillis

            globalAttempts.removeAll { timestamp -> timestamp <= cutoff }
            attemptsByKey.values.forEach { attempts -> attempts.removeAll { timestamp -> timestamp <= cutoff } }
            attemptsByKey.keys
                .filter { key -> attemptsByKey[key]?.isEmpty() != false }
                .forEach { staleKey -> attemptsByKey.remove(staleKey) }

            val keys =
                listOfNotNull(
                    source.peerKeyFingerprint?.let { fingerprint -> "fp:$fingerprint" },
                    source.peerAppInstanceId?.let { appInstanceId -> "app:$appInstanceId" },
                    source.remoteAddress?.let { address -> "ip:$address" },
                )

            val globalExhausted = globalAttempts.size >= maxGlobal
            val anyKeyExhausted =
                keys.any { key -> (attemptsByKey[key]?.size ?: 0) >= maxPerKey }

            if (globalExhausted || anyKeyExhausted) {
                false
            } else {
                globalAttempts.add(now)
                keys.forEach { key -> attemptsByKey.getOrPut(key) { mutableListOf() }.add(now) }
                true
            }
        }

    companion object {
        const val DEFAULT_MAX_PER_KEY: Int = 5
        const val DEFAULT_MAX_GLOBAL: Int = 20
        const val DEFAULT_WINDOW_MILLIS: Long = 60_000L
    }
}
