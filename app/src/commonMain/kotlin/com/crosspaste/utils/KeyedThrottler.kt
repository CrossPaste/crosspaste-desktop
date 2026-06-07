package com.crosspaste.utils

import com.crosspaste.utils.DateUtils.nowEpochMilliseconds
import io.ktor.util.collections.ConcurrentMap

/**
 * Per-key leading-edge throttle (a.k.a. cooldown) for collapsing bursts of imperative
 * callbacks — e.g. mDNS `serviceResolved`, scroll load-more — into one action per window.
 *
 * [tryAcquire] fires immediately on the first call for a key, then suppresses follow-ups
 * until [intervalMillis] has elapsed. This is the opposite of a trailing [Flow.equalDebounce]:
 * use Flow operators (`debounce` / `sample` / `conflate`) when the source is already a Flow;
 * reach for this only when the trigger is a plain (often per-key) function call.
 *
 * Best-effort under concurrency: the check-then-record is not atomic, so a burst arriving on
 * several threads within the same instant may let a few extra calls through. That is harmless
 * for the intended use (the window still bounds steady-state firing). Use a stronger primitive
 * if you need strict exactly-once-per-window semantics.
 */
class KeyedThrottler<K>(
    private val intervalMillis: Long,
) {
    private val lastFired: MutableMap<K, Long> = ConcurrentMap()

    /** Returns true (and arms the cooldown) at most once per [intervalMillis] for [key]. */
    fun tryAcquire(
        key: K,
        now: Long = nowEpochMilliseconds(),
    ): Boolean {
        val last = lastFired[key]
        if (last != null && now - last < intervalMillis) {
            return false
        }
        lastFired[key] = now
        return true
    }
}
