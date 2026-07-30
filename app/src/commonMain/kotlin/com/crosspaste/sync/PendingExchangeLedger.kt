package com.crosspaste.sync

import com.crosspaste.utils.DateUtils.nowEpochMilliseconds
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.atomicfu.updateAndGet

/**
 * Initiator-side ledger of the v2 key exchange we currently own per peer
 * (#4684 / #4690 review).
 *
 * The generation is CLIENT-generated and strictly monotonic — two attempts can
 * never collide, even inside one millisecond or across a wall-clock rollback —
 * and it is recorded BEFORE the exchange request is sent, so even when the
 * request lands on the responder but the response is lost we still hold the
 * marker needed to cancel the orphaned entry. It travels to the responder as
 * the signed `KeyExchangeRequest.timestamp` and is echoed back in the cancel
 * header, so the responder releases exactly the exchange we own and never a
 * newer one.
 */
class PendingExchangeLedger {

    private val lastGeneration = atomic(0L)

    private val generations = atomic<Map<String, Long>>(emptyMap())

    /**
     * Strictly monotonic and unique within this process, while still being a
     * plausible epoch-millisecond timestamp for the signed request field.
     */
    fun nextGeneration(): Long =
        lastGeneration.updateAndGet { previous ->
            maxOf(nowEpochMilliseconds(), previous + 1)
        }

    fun record(
        appInstanceId: String,
        generation: Long,
    ) {
        generations.update { current ->
            current + (appInstanceId to generation)
        }
    }

    /** The generation of the exchange we currently own for the peer, if any. */
    fun current(appInstanceId: String): Long? = generations.value[appInstanceId]

    /**
     * Removes the peer's record ONLY while it still is [generation]. A stale
     * cancel whose dialog was superseded by a newer exchange consumes nothing.
     * The compare-and-set also protects against synchronous manager-side
     * records racing resolver-side cancel or confirm handling.
     */
    fun consume(
        appInstanceId: String,
        generation: Long,
    ): Boolean {
        while (true) {
            val current = generations.value
            if (current[appInstanceId] != generation) return false
            if (generations.compareAndSet(current, current - appInstanceId)) {
                return true
            }
        }
    }
}
