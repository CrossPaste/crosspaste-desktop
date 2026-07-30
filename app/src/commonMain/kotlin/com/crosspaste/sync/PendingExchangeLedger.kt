package com.crosspaste.sync

import com.crosspaste.utils.DateUtils.nowEpochMilliseconds
import io.ktor.util.collections.ConcurrentMap
import kotlinx.atomicfu.atomic
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

    private val generations: MutableMap<String, Long> = ConcurrentMap()

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
        generations[appInstanceId] = generation
    }

    /** The generation of the exchange we currently own for the peer, if any. */
    fun current(appInstanceId: String): Long? = generations[appInstanceId]

    /** Drops the peer's record (a successful confirm consumed the exchange). */
    fun clear(appInstanceId: String) {
        generations.remove(appInstanceId)
    }

    /**
     * Removes the peer's record ONLY while it still is [generation]. A stale
     * cancel whose dialog was superseded by a newer exchange consumes nothing.
     * Mutations for one peer are serialized by the resolver's per-peer mutex,
     * so the check-then-remove needs no extra atomicity.
     */
    fun consume(
        appInstanceId: String,
        generation: Long,
    ): Boolean {
        if (generations[appInstanceId] != generation) return false
        generations.remove(appInstanceId)
        return true
    }
}
