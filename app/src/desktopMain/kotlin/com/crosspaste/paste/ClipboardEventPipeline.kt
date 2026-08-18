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
 * clipboard reading:
 *
 * - [onEvent] is cheap and non-blocking: it records the latest event and signals the
 *   worker through a conflated channel. It never touches the clipboard, so the caller
 *   (e.g. a Windows message loop) returns immediately.
 * - The worker waits for the clipboard to stay quiet for [quietWindow] before reading;
 *   every further event restarts the wait (at most [maxQuietWindowRestarts] times), so a
 *   whole burst collapses into one [takeSnapshot] call.
 * - After the snapshot, the sequence is re-validated: if the clipboard changed while the
 *   snapshot was being taken, the snapshot may hold a mixed state, so it is discarded and
 *   retaken — at most [maxRevalidations] times. On exhausting the budget the possibly
 *   intermediate snapshot is accepted, [degradedSnapshotCount] is incremented, and the
 *   newer content is still processed by the next pass (its event has already re-signaled
 *   the channel).
 * - [consume] is awaited by the worker, so consume passes never overlap and record order
 *   matches event order.
 *
 * The default windows are initial values; final parameters must be validated on a real
 * machine over 50+ consecutive Snipping Tool captures (#4793).
 */
class ClipboardEventPipeline<S : Any>(
    private val quietWindow: Duration = DEFAULT_QUIET_WINDOW,
    private val maxQuietWindowRestarts: Int = DEFAULT_MAX_QUIET_WINDOW_RESTARTS,
    private val maxRevalidations: Int = DEFAULT_MAX_REVALIDATIONS,
    private val currentSequence: () -> Int,
    private val takeSnapshot: suspend (ClipboardEvent) -> S?,
    private val consume: suspend (snapshot: S, event: ClipboardEvent) -> Unit,
) {

    companion object {
        // Snipping Tool's two writes land 30–90 ms apart, so the quiet window must span
        // that gap for the burst to collapse into a single snapshot.
        val DEFAULT_QUIET_WINDOW = 100.milliseconds

        // Worst-case added wait ≈ quietWindow * (restarts + 1); Ditto's field experience
        // suggests ~500 ms is an effective total delay for misbehaving writers.
        const val DEFAULT_MAX_QUIET_WINDOW_RESTARTS = 4

        const val DEFAULT_MAX_REVALIDATIONS = 2
    }

    private val logger: KLogger = KotlinLogging.logger {}

    private val signal = Channel<Unit>(Channel.CONFLATED)

    @Volatile
    private var latestEvent: ClipboardEvent? = null

    // Only read and written by the worker coroutine.
    private var lastProcessedSequence: Int? = null

    private val degradedCount = AtomicLong(0)

    val degradedSnapshotCount: Long
        get() = degradedCount.get()

    /**
     * Records a clipboard change and wakes the worker. Safe to call from any thread;
     * events arriving faster than the worker drains them conflate into the latest one.
     */
    fun onEvent(
        sequence: Int,
        source: String?,
    ) {
        latestEvent = ClipboardEvent(sequence, source)
        signal.trySend(Unit)
    }

    fun launchIn(scope: CoroutineScope): Job =
        scope.launch(CoroutineName("ClipboardEventPipelineWorker")) {
            for (unused in signal) {
                runCatching {
                    processBurst()
                }.onFailure { e ->
                    if (e is CancellationException) {
                        throw e
                    }
                    logger.error(e) { "Failed to process clipboard burst" }
                }
            }
        }

    private suspend fun processBurst() {
        var event = latestEvent ?: return
        if (event.sequence == lastProcessedSequence) {
            return
        }

        var restarts = 0
        while (true) {
            delay(quietWindow)
            val newest = latestEvent ?: event
            if (newest.sequence == event.sequence) {
                break
            }
            event = newest
            restarts++
            if (restarts >= maxQuietWindowRestarts) {
                logger.warn {
                    "Clipboard still changing after $restarts quiet-window restarts, " +
                        "proceeding at sequence ${event.sequence}"
                }
                break
            }
        }

        var snapshot: S?
        var revalidations = 0
        while (true) {
            snapshot = takeSnapshot(event)
            val sequenceNow = currentSequence()
            if (sequenceNow == event.sequence) {
                break
            }
            if (revalidations >= maxRevalidations) {
                degradedCount.incrementAndGet()
                logger.warn {
                    "Accepting possibly intermediate snapshot at sequence ${event.sequence} " +
                        "after $revalidations revalidations (clipboard now at $sequenceNow)"
                }
                break
            }
            revalidations++
            // Re-bind to the event actually recorded for the new sequence so source
            // attribution follows the captured content; if its message has not been
            // recorded yet, keep the previous source rather than re-querying now —
            // the foreground app may have changed since the write.
            event = latestEvent?.takeIf { it.sequence == sequenceNow }
                ?: ClipboardEvent(sequenceNow, event.source)
        }

        lastProcessedSequence = event.sequence
        snapshot?.let {
            consume(it, event)
        }
    }
}
