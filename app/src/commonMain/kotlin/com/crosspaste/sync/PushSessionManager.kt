package com.crosspaste.sync

import com.crosspaste.db.paste.PasteDao
import com.crosspaste.paste.PasteState
import com.crosspaste.paste.PasteboardService
import com.crosspaste.presist.FilesIndex
import com.crosspaste.utils.DateUtils.nowEpochMilliseconds
import com.crosspaste.utils.ioDispatcher
import com.crosspaste.utils.namedScope
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.util.collections.ConcurrentMap
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

internal class PushSessionReservation(
    private val owner: PushSessionManager,
    private val releaseSlot: () -> Unit,
) {
    private val held = atomic(true)

    internal fun transferTo(manager: PushSessionManager): Boolean =
        owner === manager && held.compareAndSet(expect = true, update = false)

    fun release() {
        if (held.compareAndSet(expect = true, update = false)) {
            releaseSlot()
        }
    }
}

/**
 * Concurrency model: writes (markReceived) go through [lock] so the
 * "check-then-set" on `received[i]` and the count increment stay paired.
 * Reads ([isReceived], [missingChunks], [receivedCount], [isComplete],
 * [lastActivity]) are lockless on purpose — they may observe a slightly stale
 * value while a concurrent markReceived is in flight. The acceptable failure
 * modes are:
 *   - chunk fast-path idempotency may miss a just-set bit → second write of the
 *     same chunk to the same offset (same bytes, harmless redundancy);
 *   - sweep may see isComplete=false right after the last chunk lands → session
 *     gets discarded a sweep cycle later than the optimal moment.
 * The win is no contention between the chunk hot path and sweep / status reads.
 */
class PushSession(
    val pasteId: Long,
    val fromAppInstanceId: String,
    val token: String,
    val filesIndex: FilesIndex,
    createdAt: Long,
) {
    val chunkCount: Int = filesIndex.getChunkCount()
    private val received: BooleanArray = BooleanArray(chunkCount)

    // Mutated only inside `lock`. Read without lock — staleness tolerated, see class kdoc.
    @Volatile private var receivedCountBacking: Int = 0

    @Volatile private var lastActivityBacking: Long = createdAt
    private val lock = Mutex()
    private val finalizeLock = Mutex()

    @Volatile private var finalized = false

    val receivedCount: Int get() = receivedCountBacking
    val lastActivity: Long get() = lastActivityBacking
    val isComplete: Boolean get() = receivedCountBacking >= chunkCount

    suspend fun markReceived(chunkIndex: Int): MarkResult {
        if (chunkIndex !in 0 until chunkCount) {
            return MarkResult.OutOfRange
        }
        lock.withLock {
            if (received[chunkIndex]) {
                lastActivityBacking = nowEpochMilliseconds()
                return MarkResult.AlreadyReceived
            }
            received[chunkIndex] = true
            receivedCountBacking += 1
            lastActivityBacking = nowEpochMilliseconds()
            return MarkResult.Accepted
        }
    }

    // Lockless read — may briefly miss a just-set bit; second write is harmless (same bytes).
    fun isReceived(chunkIndex: Int): Boolean = chunkIndex in 0 until chunkCount && received[chunkIndex]

    // Lockless read — snapshot may include a chunk that's about to be marked received.
    fun missingChunks(): List<Int> = (0 until chunkCount).filter { !received[it] }

    internal suspend fun finalize(block: suspend () -> Result<Unit?>): Result<Unit?> =
        finalizeLock.withLock {
            if (finalized) {
                return@withLock Result.success(Unit)
            }
            block().onSuccess {
                finalized = true
            }
        }

    enum class MarkResult {
        Accepted,
        AlreadyReceived,
        OutOfRange,
    }
}

internal sealed interface PushCompletionResult {
    data object Complete : PushCompletionResult

    data class Incomplete(
        val missingChunks: List<Int>,
    ) : PushCompletionResult

    data object NotFound : PushCompletionResult

    data class Failed(
        val cause: Throwable,
    ) : PushCompletionResult
}

class PushSessionManager(
    private val pasteDao: PasteDao,
    private val pasteboardService: PasteboardService,
    private val maxActive: Int = DEFAULT_MAX_ACTIVE,
    private val sessionTtl: Duration = DEFAULT_SESSION_TTL,
    private val sweepInterval: Duration = DEFAULT_SWEEP_INTERVAL,
    private val scope: CoroutineScope = namedScope(ioDispatcher, "PushSessionManager"),
) {

    companion object {
        const val DEFAULT_MAX_ACTIVE: Int = 16
        val DEFAULT_SESSION_TTL: Duration = 90.seconds
        val DEFAULT_SWEEP_INTERVAL: Duration = 15.seconds
    }

    private val logger = KotlinLogging.logger {}

    private val sessions = ConcurrentMap<Long, PushSession>()

    // Capacity is governed by this counter, not `sessions.size`: a CAS
    // reservation makes the check-then-insert in [create] atomic, so
    // concurrent prepares cannot overshoot [maxActive]. Every path that
    // removes a session from the map must release its slot.
    private val activeSlots = atomic(0)

    init {
        scope.launch {
            while (isActive) {
                delay(sweepInterval)
                sweepExpired()
            }
        }
    }

    fun activeCount(): Int = sessions.size

    private fun tryReserveSlot(): Boolean {
        while (true) {
            val current = activeSlots.value
            if (current >= maxActive) return false
            if (activeSlots.compareAndSet(current, current + 1)) return true
        }
    }

    private fun releaseSlot() {
        activeSlots.decrementAndGet()
    }

    /**
     * Reserves capacity before callers create a LOADING row or preallocate files.
     * The caller must release the reservation in `finally`; a successful [create]
     * transfers ownership to the session, making the release a no-op.
     */
    internal fun tryReserve(): PushSessionReservation? =
        if (tryReserveSlot()) {
            PushSessionReservation(this, ::releaseSlot)
        } else {
            null
        }

    @OptIn(ExperimentalUuidApi::class)
    fun create(
        pasteId: Long,
        fromAppInstanceId: String,
        filesIndex: FilesIndex,
    ): PushSession? {
        val reservation = tryReserve()
        if (reservation == null) {
            logger.warn { "PushSession create rejected: maxActive=$maxActive reached" }
            return null
        }
        return create(reservation, pasteId, fromAppInstanceId, filesIndex)
    }

    @OptIn(ExperimentalUuidApi::class)
    internal fun create(
        reservation: PushSessionReservation,
        pasteId: Long,
        fromAppInstanceId: String,
        filesIndex: FilesIndex,
    ): PushSession? =
        try {
            if (filesIndex.getChunkCount() <= 0) {
                logger.warn { "PushSession create rejected: empty filesIndex for pasteId=$pasteId" }
                return null
            }
            val session =
                PushSession(
                    pasteId = pasteId,
                    fromAppInstanceId = fromAppInstanceId,
                    token = Uuid.random().toString(),
                    filesIndex = filesIndex,
                    createdAt = nowEpochMilliseconds(),
                )
            var inserted = false
            sessions.computeIfAbsent(pasteId) {
                inserted = true
                session
            }
            if (!inserted) {
                logger.warn { "PushSession create rejected: duplicate pasteId=$pasteId" }
                return null
            }
            if (!reservation.transferTo(this)) {
                sessions.remove(pasteId, session)
                logger.warn { "PushSession create rejected: invalid reservation for pasteId=$pasteId" }
                return null
            }
            session
        } finally {
            reservation.release()
        }

    fun get(
        pasteId: Long,
        token: String,
        fromAppInstanceId: String,
    ): PushSession? = sessions[pasteId]?.takeIf { it.token == token && it.fromAppInstanceId == fromAppInstanceId }

    fun peek(pasteId: Long): PushSession? = sessions[pasteId]

    /** Finalizes one complete session exactly once and only then releases its slot. */
    internal suspend fun finalizeIfComplete(session: PushSession): PushCompletionResult {
        if (!session.isComplete) {
            return PushCompletionResult.Incomplete(session.missingChunks())
        }

        if (sessions[session.pasteId] !== session) {
            return durableCompletionResult(session.pasteId, session.fromAppInstanceId)
        }

        val result =
            session.finalize {
                pasteboardService.tryWriteRemotePasteboardWithFile(session.pasteId)
            }
        result.exceptionOrNull()?.let { cause ->
            if (cause is CancellationException) throw cause
            logger.warn(cause) { "PushSession finalize failed: pasteId=${session.pasteId}" }
            return PushCompletionResult.Failed(cause)
        }

        if (sessions.remove(session.pasteId, session)) {
            releaseSlot()
        }
        return PushCompletionResult.Complete
    }

    /**
     * Returns the authoritative completion state for `/complete`. An active
     * session still requires its token; once it is gone, only a durable LOADED
     * row belonging to the authenticated sender qualifies as idempotent success.
     */
    internal suspend fun complete(
        pasteId: Long,
        token: String,
        fromAppInstanceId: String,
    ): PushCompletionResult {
        val session = sessions[pasteId] ?: return durableCompletionResult(pasteId, fromAppInstanceId)
        if (session.token != token || session.fromAppInstanceId != fromAppInstanceId) {
            return PushCompletionResult.NotFound
        }
        val missing = session.missingChunks()
        if (missing.isNotEmpty()) {
            return PushCompletionResult.Incomplete(missing)
        }
        return finalizeIfComplete(session)
    }

    private suspend fun durableCompletionResult(
        pasteId: Long,
        fromAppInstanceId: String,
    ): PushCompletionResult =
        try {
            val pasteData = pasteDao.getNoDeletePasteData(pasteId)
            if (
                pasteData?.pasteState == PasteState.LOADED &&
                pasteData.appInstanceId == fromAppInstanceId
            ) {
                PushCompletionResult.Complete
            } else {
                PushCompletionResult.NotFound
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn(e) { "PushSession durable completion lookup failed: pasteId=$pasteId" }
            PushCompletionResult.Failed(e)
        }

    /**
     * Visible for testing. Last-resort cleanup for sessions that timed out.
     * The orphan-complete branch (all chunks arrived but no finalize ran) is a
     * safety net — [finalizeIfComplete] normally handles it when the last
     * chunk arrives. Sweep retries finalization once, then discards a session
     * whose failure would otherwise hold capacity forever.
     */
    suspend fun sweepExpired() {
        val now = nowEpochMilliseconds()
        val ttlMs = sessionTtl.inWholeMilliseconds
        val expiredIds =
            sessions.entries
                .filter { (_, session) -> now - session.lastActivity > ttlMs }
                .map { it.key }
        for (id in expiredIds) {
            val session = sessions[id] ?: continue
            if (session.isComplete) {
                when (finalizeIfComplete(session)) {
                    PushCompletionResult.Complete ->
                        logger.info { "PushSession sweep finalized orphan-complete: pasteId=$id" }
                    is PushCompletionResult.Failed ->
                        discardExpiredSession(session, "finalization failed")
                    is PushCompletionResult.Incomplete ->
                        discardExpiredSession(session, "incomplete finalization state")
                    PushCompletionResult.NotFound -> Unit
                }
            } else {
                discardExpiredSession(session, "incomplete upload")
            }
        }
    }

    private suspend fun discardExpiredSession(
        session: PushSession,
        reason: String,
    ) {
        if (!sessions.remove(session.pasteId, session)) return
        releaseSlot()
        try {
            pasteDao.markDeletePasteData(session.pasteId).getOrThrow()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn(e) { "PushSession expire: markDeletePasteData(${session.pasteId}) failed" }
        }
        logger.info {
            "PushSession expired and discarded: pasteId=${session.pasteId} reason=$reason " +
                "(${session.receivedCount}/${session.chunkCount} chunks received)"
        }
    }

    fun close() {
        scope.cancel()
        sessions.clear()
        activeSlots.value = 0
    }
}
