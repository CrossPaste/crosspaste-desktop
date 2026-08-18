package com.crosspaste.paste

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * A clipboard change event as observed by the platform listener.
 *
 * [source] is sampled at event time so it stays bound to [sequence]: by the time the
 * worker reads the clipboard, the foreground app / clipboard owner may have changed.
 */
data class ClipboardEvent(
    val sequence: Int,
    val source: String?,
)

/**
 * Single-consumer clipboard event pipeline.
 *
 * Apps like Snipping Tool write the clipboard several times per user operation within a
 * short burst, and each eager full snapshot taken in that window competes with the
 * writer's own clipboard access (#4737). This pipeline decouples event delivery from
 * clipboard reading, and clipboard reading from record processing:
 *
 * - [onEvent] is cheap and non-blocking: it records the latest event and signals the
 *   snapshot worker through a conflated channel. It never touches the clipboard, so the
 *   caller (e.g. a Windows message loop) returns immediately.
 * - The snapshot worker waits for the clipboard to stay quiet for [quietWindow] before
 *   reading. Detecting a further write during the wait marks the burst as such and the
 *   remaining waits use the longer [burstQuietWindow] (at most [maxQuietWindowRestarts]
 *   restarts), so a whole burst collapses into **one** [takeSnapshot] call.
 * - After the snapshot, the sequence is re-validated. If the clipboard changed while the
 *   snapshot was being taken, the snapshot may hold a mixed state and the event carrying
 *   the new sequence's source has not been seen yet — so the snapshot is **discarded**
 *   ([discardedSnapshotCount] tracks this) and the sequence is left unprocessed: the new
 *   write's own event re-enters through a fresh quiet window with its correct source.
 *   Inheriting the previous event's source here would misattribute the new content and
 *   could bypass source exclusions; an immediate re-read would recreate the back-to-back
 *   full snapshots this pipeline exists to avoid.
 * - Captured snapshots are queued to a separate consume worker that awaits each
 *   [consume] serially: consume passes never overlap, record order matches event order,
 *   and a slow consume does not delay or conflate the capture of later events.
 *
 * The default windows are initial values; final parameters must be validated on a real
 * machine over 50+ consecutive Snipping Tool captures (#4793).
 */
class ClipboardEventPipeline<S : Any>(
    private val quietWindow: Duration = DEFAULT_QUIET_WINDOW,
    private val burstQuietWindow: Duration = DEFAULT_BURST_QUIET_WINDOW,
    private val maxQuietWindowRestarts: Int = DEFAULT_MAX_QUIET_WINDOW_RESTARTS,
    private val currentSequence: () -> Int,
    private val takeSnapshot: suspend (ClipboardEvent) -> S?,
    private val consume: suspend (snapshot: S, event: ClipboardEvent) -> Unit,
) {

    companion object {
        // A normal single copy only pays this small delay before the snapshot.
        val DEFAULT_QUIET_WINDOW = 100.milliseconds

        // Snipping Tool's two writes land 30–90 ms apart; once a second write is seen
        // inside the base window, wait out the longer window that Ditto's field
        // experience (~500 ms) suggests misbehaving writers need to finish.
        val DEFAULT_BURST_QUIET_WINDOW = 500.milliseconds

        const val DEFAULT_MAX_QUIET_WINDOW_RESTARTS = 4
    }

    private val logger: KLogger = KotlinLogging.logger {}

    private val signal = Channel<Unit>(Channel.CONFLATED)

    private val snapshotQueue = Channel<Pair<S, ClipboardEvent>>(Channel.UNLIMITED)

    @Volatile
    private var latestEvent: ClipboardEvent? = null

    // Only read and written by the snapshot worker coroutine.
    private var lastProcessedSequence: Int? = null

    private val discardedCount = AtomicLong(0)

    val discardedSnapshotCount: Long
        get() = discardedCount.get()

    /**
     * Records a clipboard change and wakes the snapshot worker. Safe to call from any
     * thread; events arriving faster than the worker drains them conflate into the
     * latest one.
     */
    fun onEvent(
        sequence: Int,
        source: String?,
    ) {
        latestEvent = ClipboardEvent(sequence, source)
        signal.trySend(Unit)
    }

    fun launchIn(scope: CoroutineScope): Job =
        scope.launch(CoroutineName("ClipboardEventPipeline")) {
            launch(CoroutineName("ClipboardEventPipelineConsumer")) {
                consumeLoop()
            }
            launch(CoroutineName("ClipboardEventPipelineSnapshotter")) {
                snapshotLoop()
            }
        }

    private suspend fun snapshotLoop() {
        for (unused in signal) {
            runCatching {
                captureBurst()
            }.onFailure { e ->
                if (e is CancellationException) {
                    throw e
                }
                logger.error(e) { "Failed to capture clipboard burst" }
            }
        }
    }

    private suspend fun consumeLoop() {
        for ((snapshot, event) in snapshotQueue) {
            runCatching {
                consume(snapshot, event)
            }.onFailure { e ->
                if (e is CancellationException) {
                    throw e
                }
                logger.error(e) { "Failed to consume clipboard snapshot at sequence ${event.sequence}" }
            }
        }
    }

    private suspend fun captureBurst() {
        var event = latestEvent ?: return
        if (event.sequence == lastProcessedSequence) {
            return
        }

        var wait = quietWindow
        var restarts = 0
        while (true) {
            delay(wait)
            val newest = latestEvent ?: event
            if (newest.sequence == event.sequence) {
                break
            }
            event = newest
            wait = burstQuietWindow
            restarts++
            if (restarts >= maxQuietWindowRestarts) {
                logger.warn {
                    "Clipboard still changing after $restarts quiet-window restarts, " +
                        "proceeding at sequence ${event.sequence}"
                }
                break
            }
        }

        val snapshot = takeSnapshot(event)
        if (snapshot == null) {
            lastProcessedSequence = event.sequence
            return
        }

        val sequenceNow = currentSequence()
        if (sequenceNow != event.sequence) {
            discardedCount.incrementAndGet()
            logger.warn {
                "Discarding snapshot at sequence ${event.sequence}: clipboard moved to " +
                    "$sequenceNow during the read; awaiting its own event"
            }
            return
        }

        lastProcessedSequence = event.sequence
        snapshotQueue.send(snapshot to event)
    }
}
