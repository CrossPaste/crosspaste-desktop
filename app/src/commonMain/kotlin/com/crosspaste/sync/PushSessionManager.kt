package com.crosspaste.sync

import com.crosspaste.db.paste.PasteDao
import com.crosspaste.paste.PasteboardService
import com.crosspaste.presist.FilesIndex
import com.crosspaste.utils.DateUtils.nowEpochMilliseconds
import com.crosspaste.utils.ioDispatcher
import com.crosspaste.utils.namedScope
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.util.collections.ConcurrentMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

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

    enum class MarkResult {
        Accepted,
        AlreadyReceived,
        OutOfRange,
    }
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

    init {
        scope.launch {
            while (isActive) {
                delay(sweepInterval)
                sweepExpired()
            }
        }
    }

    fun activeCount(): Int = sessions.size

    @OptIn(ExperimentalUuidApi::class)
    fun create(
        pasteId: Long,
        fromAppInstanceId: String,
        filesIndex: FilesIndex,
    ): PushSession? {
        if (filesIndex.getChunkCount() <= 0) {
            logger.warn { "PushSession create rejected: empty filesIndex for pasteId=$pasteId" }
            return null
        }
        if (sessions.size >= maxActive) {
            logger.warn { "PushSession create rejected: maxActive=$maxActive reached" }
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
        sessions[pasteId] = session
        return session
    }

    fun get(
        pasteId: Long,
        token: String,
        fromAppInstanceId: String,
    ): PushSession? = sessions[pasteId]?.takeIf { it.token == token && it.fromAppInstanceId == fromAppInstanceId }

    fun peek(pasteId: Long): PushSession? = sessions[pasteId]

    /**
     * Atomically claims and finalizes the session. Returns true when this caller
     * owned the claim — caller can treat the finalize as scheduled. Returns
     * false when the session is already gone (auto-finalize on last chunk, a
     * concurrent /complete, or sweep). Idempotent across paths.
     *
     * Finalize is fire-and-forget on the manager's [scope]: file state flips
     * LOADING → LOADED and a write to the local pasteboard is queued.
     */
    fun tryFinalize(pasteId: Long): Boolean {
        if (sessions.remove(pasteId) == null) return false
        scope.launch {
            runCatching {
                pasteboardService.tryWriteRemotePasteboardWithFile(pasteId)
            }.onFailure { e ->
                logger.warn(e) { "PushSession finalize failed: pasteId=$pasteId" }
            }
        }
        return true
    }

    /**
     * Primary finalization trigger — called by the chunk handler right after
     * [PushSession.markReceived]. When the chunk just landed completes the set,
     * the file is ready for the pasteboard NOW, not 90s later when sweep runs.
     */
    fun tryFinalizeIfComplete(pasteId: Long): Boolean {
        val session = sessions[pasteId] ?: return false
        if (!session.isComplete) return false
        return tryFinalize(pasteId)
    }

    /**
     * Visible for testing. Last-resort cleanup for sessions that timed out.
     * The orphan-complete branch (all chunks arrived but no finalize ran) is a
     * safety net — [tryFinalizeIfComplete] normally handles it the moment the
     * last chunk arrives. Sweep only catches it if that path failed (e.g.
     * scope cancelled during shutdown, finalize launch dropped).
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
                if (tryFinalize(id)) {
                    logger.info { "PushSession sweep salvaged orphan-complete: pasteId=$id" }
                }
            } else {
                val removed = sessions.remove(id) ?: continue
                runCatching {
                    pasteDao.markDeletePasteData(id)
                }.onFailure { e ->
                    logger.warn(e) { "PushSession expire: markDeletePasteData($id) failed" }
                }
                logger.info {
                    "PushSession expired and discarded: pasteId=$id " +
                        "(${removed.receivedCount}/${removed.chunkCount} chunks received)"
                }
            }
        }
    }

    fun close() {
        scope.cancel()
        sessions.clear()
    }
}
