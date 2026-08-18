package com.crosspaste.paste

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ClipboardEventPipelineTest {

    private class Harness(
        quietWindow: Duration = 100.milliseconds,
        burstQuietWindow: Duration = 500.milliseconds,
        maxQuietWindowRestarts: Int = ClipboardEventPipeline.DEFAULT_MAX_QUIET_WINDOW_RESTARTS,
        snapshotQueueCapacity: Int = ClipboardEventPipeline.DEFAULT_SNAPSHOT_QUEUE_CAPACITY,
    ) {
        var sequence = 0

        var onSnapshot: suspend (ClipboardEvent) -> String? = { event -> "snap-${event.sequence}" }

        var onConsume: suspend (String, ClipboardEvent) -> Unit = { _, _ -> }

        val snapshots = mutableListOf<ClipboardEvent>()
        val consumed = mutableListOf<Pair<String, ClipboardEvent>>()

        val pipeline =
            ClipboardEventPipeline(
                quietWindow = quietWindow,
                burstQuietWindow = burstQuietWindow,
                maxQuietWindowRestarts = maxQuietWindowRestarts,
                snapshotQueueCapacity = snapshotQueueCapacity,
                currentSequence = { sequence },
                takeSnapshot = { event ->
                    snapshots.add(event)
                    onSnapshot(event)
                },
                consume = { snapshot, event ->
                    onConsume(snapshot, event)
                    consumed.add(snapshot to event)
                },
            )

        fun write(source: String?): Int {
            sequence++
            pipeline.onEvent(sequence, source)
            return sequence
        }
    }

    @Test
    fun `single event is consumed once with its bound source`() =
        runTest {
            val harness = Harness()
            harness.pipeline.startCapture(this)

            harness.write("AppA")
            advanceUntilIdle()

            assertEquals(listOf(ClipboardEvent(1, "AppA")), harness.snapshots)
            assertEquals(listOf("snap-1" to ClipboardEvent(1, "AppA")), harness.consumed)
            assertEquals(0L, harness.pipeline.discardedSnapshotCount)
            harness.pipeline.close(1.seconds)
        }

    @Test
    fun `burst within quiet window collapses to one snapshot attributed to the last event`() =
        runTest {
            val harness = Harness()
            harness.pipeline.startCapture(this)

            harness.write("SnippingTool")
            advanceTimeBy(60.milliseconds)
            harness.write("SnippingTool")
            advanceUntilIdle()

            assertEquals(listOf(ClipboardEvent(2, "SnippingTool")), harness.snapshots)
            assertEquals(listOf("snap-2" to ClipboardEvent(2, "SnippingTool")), harness.consumed)
            assertEquals(0L, harness.pipeline.discardedSnapshotCount)
            harness.pipeline.close(1.seconds)
        }

    @Test
    fun `detecting a burst extends the wait to the burst quiet window`() =
        runTest {
            val harness = Harness()
            harness.pipeline.startCapture(this)

            harness.write("SnippingTool")
            advanceTimeBy(60.milliseconds)
            harness.write("SnippingTool")

            // The burst is detected at 100 ms; the snapshot is then deferred by the
            // 500 ms burst window instead of another base window.
            advanceTimeBy(500.milliseconds)
            assertTrue(harness.snapshots.isEmpty())

            advanceUntilIdle()
            assertEquals(listOf(ClipboardEvent(2, "SnippingTool")), harness.snapshots)
            harness.pipeline.close(1.seconds)
        }

    @Test
    fun `events separated by more than the quiet window produce one record each in order`() =
        runTest {
            val harness = Harness()
            harness.pipeline.startCapture(this)

            harness.write("AppA")
            advanceTimeBy(500.milliseconds)
            harness.write("AppB")
            advanceUntilIdle()

            assertEquals(
                listOf(
                    "snap-1" to ClipboardEvent(1, "AppA"),
                    "snap-2" to ClipboardEvent(2, "AppB"),
                ),
                harness.consumed,
            )
            harness.pipeline.close(1.seconds)
        }

    @Test
    fun `snapshot is discarded when the sequence changes during the read`() =
        runTest {
            val harness = Harness()
            var firstSnapshot = true
            harness.onSnapshot = { event ->
                if (firstSnapshot) {
                    firstSnapshot = false
                    // A second write lands while the first snapshot is being taken.
                    harness.write("Late")
                }
                "snap-${event.sequence}"
            }
            harness.pipeline.startCapture(this)

            harness.write("AppA")
            advanceUntilIdle()

            // The mixed-state snapshot is dropped; the late event re-enters through its
            // own quiet window carrying its own source.
            assertEquals(
                listOf(ClipboardEvent(1, "AppA"), ClipboardEvent(2, "Late")),
                harness.snapshots,
            )
            assertEquals(listOf("snap-2" to ClipboardEvent(2, "Late")), harness.consumed)
            assertEquals(1L, harness.pipeline.discardedSnapshotCount)
            harness.pipeline.close(1.seconds)
        }

    @Test
    fun `a write during the read does not inherit the previous source and exclusions hold`() =
        runTest {
            val harness = Harness()
            harness.onSnapshot = { event ->
                if (event.source == "AppA" && harness.sequence == 1) {
                    // An excluded app writes while AppA's snapshot is being taken; its
                    // change message has not arrived yet.
                    harness.sequence++
                }
                if (event.source == "ExcludedApp") {
                    null
                } else {
                    "snap-${event.sequence}"
                }
            }
            harness.pipeline.startCapture(this)

            harness.write("AppA")
            advanceUntilIdle()

            // The mixed snapshot was dropped instead of being processed as AppA.
            assertEquals(1L, harness.pipeline.discardedSnapshotCount)
            assertTrue(harness.consumed.isEmpty())

            // The excluded app's own event arrives and is excluded, not recorded.
            harness.pipeline.onEvent(2, "ExcludedApp")
            advanceUntilIdle()

            assertEquals(
                listOf(ClipboardEvent(1, "AppA"), ClipboardEvent(2, "ExcludedApp")),
                harness.snapshots,
            )
            assertTrue(harness.consumed.isEmpty())
            harness.pipeline.close(1.seconds)
        }

    @Test
    fun `consume passes never overlap and record order matches event order`() =
        runTest {
            val harness = Harness()
            var active = 0
            var maxActive = 0
            harness.onConsume = { _, _ ->
                active++
                maxActive = maxOf(maxActive, active)
                delay(1.seconds)
                active--
            }
            harness.pipeline.startCapture(this)

            harness.write("AppA")
            advanceTimeBy(150.milliseconds)
            harness.write("AppB")
            advanceUntilIdle()

            assertEquals(listOf(1, 2), harness.consumed.map { it.second.sequence })
            assertEquals(1, maxActive)
            harness.pipeline.close(1.seconds)
        }

    @Test
    fun `a slow consume does not conflate or drop later events`() =
        runTest {
            val harness = Harness()
            harness.onConsume = { _, _ -> delay(10.seconds) }
            harness.pipeline.startCapture(this)

            harness.write("AppA")
            advanceTimeBy(500.milliseconds)
            // Both writes land while AppA's consume is still running.
            harness.write("AppB")
            advanceTimeBy(1500.milliseconds)
            harness.write("AppC")
            advanceUntilIdle()

            assertEquals(listOf(1, 2, 3), harness.snapshots.map { it.sequence })
            assertEquals(listOf(1, 2, 3), harness.consumed.map { it.second.sequence })
            harness.pipeline.close(1.seconds)
        }

    @Test
    fun `null snapshot consumes nothing and still marks the sequence processed`() =
        runTest {
            val harness = Harness()
            harness.onSnapshot = { null }
            harness.pipeline.startCapture(this)

            harness.write("Excluded")
            advanceUntilIdle()

            assertEquals(1, harness.snapshots.size)
            assertTrue(harness.consumed.isEmpty())

            // A duplicate signal for an already processed sequence does not re-read.
            harness.pipeline.onEvent(1, "Excluded")
            advanceUntilIdle()

            assertEquals(1, harness.snapshots.size)
            harness.pipeline.close(1.seconds)
        }

    @Test
    fun `quiet window restarts are bounded when the clipboard never settles`() =
        runTest {
            val harness = Harness(maxQuietWindowRestarts = 2)
            harness.pipeline.startCapture(this)

            harness.write("AppA")
            repeat(10) {
                advanceTimeBy(60.milliseconds)
                harness.write("AppA")
            }
            advanceUntilIdle()

            assertTrue(harness.consumed.isNotEmpty())
            // First wake at 100 ms adopts seq 2, the burst wait of 500 ms wakes at
            // 600 ms and adopts seq 11, then the restart cap forces the snapshot
            // instead of waiting out the whole write storm.
            assertEquals(
                11,
                harness.consumed
                    .first()
                    .second.sequence,
            )
            harness.pipeline.close(1.seconds)
        }

    @Test
    fun `stopCapture stops new snapshots but already captured ones are still consumed`() =
        runTest {
            val harness = Harness()
            harness.onConsume = { _, _ -> delay(10.seconds) }
            harness.pipeline.startCapture(this)

            harness.write("AppA")
            advanceTimeBy(200.milliseconds)
            harness.write("AppB")
            advanceTimeBy(200.milliseconds)

            // AppA is mid-consume and AppB's snapshot sits in the queue.
            harness.pipeline.stopCapture()
            harness.write("AppC")
            advanceUntilIdle()

            assertEquals(listOf(1, 2), harness.snapshots.map { it.sequence })
            assertEquals(listOf(1, 2), harness.consumed.map { it.second.sequence })
            harness.pipeline.close(1.seconds)
        }

    @Test
    fun `startCapture after stopCapture resumes capturing`() =
        runTest {
            val harness = Harness()
            harness.pipeline.startCapture(this)

            harness.write("AppA")
            advanceUntilIdle()
            harness.pipeline.stopCapture()

            harness.pipeline.startCapture(this)
            harness.write("AppB")
            advanceUntilIdle()

            assertEquals(listOf(1, 2), harness.consumed.map { it.second.sequence })
            harness.pipeline.close(1.seconds)
        }

    @Test
    fun `an event queued before stopCapture is not replayed after restart`() =
        runTest {
            val harness = Harness()
            harness.pipeline.startCapture(this)

            // The signal is queued but the quiet window has not elapsed yet.
            harness.write("AppA")
            harness.pipeline.stopCapture()
            harness.pipeline.startCapture(this)
            advanceUntilIdle()

            assertTrue(harness.snapshots.isEmpty())

            harness.write("AppB")
            advanceUntilIdle()

            assertEquals(listOf(2), harness.consumed.map { it.second.sequence })
            harness.pipeline.close(1.seconds)
        }

    @Test
    fun `stopCapture during a blocking read discards the result without marking it processed`() =
        runTest {
            val harness = Harness()
            var stopOnce = true
            harness.onSnapshot = { event ->
                if (stopOnce) {
                    stopOnce = false
                    harness.pipeline.stopCapture()
                }
                "snap-${event.sequence}"
            }
            harness.pipeline.startCapture(this)

            harness.write("AppA")
            advanceUntilIdle()

            assertEquals(1, harness.snapshots.size)
            assertTrue(harness.consumed.isEmpty())

            // After restart the same sequence is still readable: it was never marked
            // processed by the discarded read.
            harness.pipeline.startCapture(this)
            harness.pipeline.onEvent(1, "AppA")
            advanceUntilIdle()

            assertEquals(2, harness.snapshots.size)
            assertEquals(listOf(1), harness.consumed.map { it.second.sequence })
            harness.pipeline.close(1.seconds)
        }

    @Test
    fun `queue overflow drops the oldest snapshot and keeps the newest states`() =
        runTest {
            val harness = Harness(snapshotQueueCapacity = 2)
            harness.onConsume = { _, _ -> delay(100.seconds) }
            harness.pipeline.startCapture(this)

            harness.write("AppA")
            advanceTimeBy(200.milliseconds)
            harness.write("AppB")
            advanceTimeBy(400.milliseconds)
            harness.write("AppC")
            advanceTimeBy(400.milliseconds)
            // AppA is mid-consume; the queue holds AppB and AppC, so AppD evicts AppB.
            harness.write("AppD")
            advanceUntilIdle()

            assertEquals(listOf(1, 2, 3, 4), harness.snapshots.map { it.sequence })
            assertEquals(listOf(1, 3, 4), harness.consumed.map { it.second.sequence })
            assertEquals(1L, harness.pipeline.droppedSnapshotCount)
            harness.pipeline.close(1.seconds)
        }

    @Test
    fun `close drains the queue within the grace period`() =
        runTest {
            val harness = Harness()
            harness.onConsume = { _, _ -> delay(1.seconds) }
            harness.pipeline.startCapture(this)

            harness.write("AppA")
            advanceTimeBy(150.milliseconds)
            harness.write("AppB")
            advanceTimeBy(150.milliseconds)

            // AppA is mid-consume, AppB is queued; both finish inside the grace period.
            harness.pipeline.close(10.seconds)

            assertEquals(listOf(1, 2), harness.consumed.map { it.second.sequence })
            assertEquals(0L, harness.pipeline.droppedSnapshotCount)
        }

    @Test
    fun `close cancels a consume exceeding the grace period and drops leftovers`() =
        runTest {
            val harness = Harness()
            harness.onConsume = { _, _ -> delay(100.seconds) }
            harness.pipeline.startCapture(this)

            harness.write("AppA")
            advanceTimeBy(200.milliseconds)
            harness.write("AppB")
            advanceTimeBy(200.milliseconds)

            // AppA's consume outlives the grace period; queued AppB is dropped.
            harness.pipeline.close(1.seconds)

            assertTrue(harness.consumed.isEmpty())
            assertEquals(1L, harness.pipeline.droppedSnapshotCount)
        }
}
