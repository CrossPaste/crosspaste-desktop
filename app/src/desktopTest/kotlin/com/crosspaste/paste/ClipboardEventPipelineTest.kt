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
        maxQuietWindowRestarts: Int = ClipboardEventPipeline.DEFAULT_MAX_QUIET_WINDOW_RESTARTS,
        maxRevalidations: Int = ClipboardEventPipeline.DEFAULT_MAX_REVALIDATIONS,
    ) {
        var sequence = 0

        var onSnapshot: suspend (ClipboardEvent) -> String? = { event -> "snap-${event.sequence}" }

        var onConsume: suspend (String, ClipboardEvent) -> Unit = { _, _ -> }

        val snapshots = mutableListOf<ClipboardEvent>()
        val consumed = mutableListOf<Pair<String, ClipboardEvent>>()

        val pipeline =
            ClipboardEventPipeline(
                quietWindow = quietWindow,
                maxQuietWindowRestarts = maxQuietWindowRestarts,
                maxRevalidations = maxRevalidations,
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
            val worker = harness.pipeline.launchIn(this)

            harness.write("AppA")
            advanceUntilIdle()

            assertEquals(listOf(ClipboardEvent(1, "AppA")), harness.snapshots)
            assertEquals(listOf("snap-1" to ClipboardEvent(1, "AppA")), harness.consumed)
            assertEquals(0L, harness.pipeline.degradedSnapshotCount)
            worker.cancel()
        }

    @Test
    fun `burst within quiet window collapses to one snapshot attributed to the last event`() =
        runTest {
            val harness = Harness()
            val worker = harness.pipeline.launchIn(this)

            harness.write("SnippingTool")
            advanceTimeBy(60.milliseconds)
            harness.write("SnippingTool")
            advanceUntilIdle()

            assertEquals(listOf(ClipboardEvent(2, "SnippingTool")), harness.snapshots)
            assertEquals(listOf("snap-2" to ClipboardEvent(2, "SnippingTool")), harness.consumed)
            assertEquals(0L, harness.pipeline.degradedSnapshotCount)
            worker.cancel()
        }

    @Test
    fun `events separated by more than the quiet window produce one record each in order`() =
        runTest {
            val harness = Harness()
            val worker = harness.pipeline.launchIn(this)

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
            worker.cancel()
        }

    @Test
    fun `snapshot is retaken when the sequence changes during the read`() =
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
            val worker = harness.pipeline.launchIn(this)

            harness.write("AppA")
            advanceUntilIdle()

            assertEquals(2, harness.snapshots.size)
            assertEquals(listOf("snap-2" to ClipboardEvent(2, "Late")), harness.consumed)
            assertEquals(0L, harness.pipeline.degradedSnapshotCount)
            worker.cancel()
        }

    @Test
    fun `revalidation is bounded and the latest event is still processed afterwards`() =
        runTest {
            val harness = Harness(maxRevalidations = 2)
            harness.onSnapshot = { event ->
                if (harness.sequence < 4) {
                    // The clipboard keeps changing under the reader, and the matching
                    // change messages have not been recorded yet.
                    harness.sequence++
                }
                "snap-${event.sequence}"
            }
            val worker = harness.pipeline.launchIn(this)

            harness.write("AppA")
            advanceUntilIdle()

            assertEquals(3, harness.snapshots.size)
            assertEquals(1L, harness.pipeline.degradedSnapshotCount)
            // The degraded snapshot keeps the source of the event it was re-bound from.
            assertEquals(listOf("snap-3" to ClipboardEvent(3, "AppA")), harness.consumed)

            // The change message for the newest write arrives afterwards and is processed.
            harness.pipeline.onEvent(4, "AppB")
            advanceUntilIdle()

            assertEquals(4, harness.snapshots.size)
            assertEquals("snap-4" to ClipboardEvent(4, "AppB"), harness.consumed.last())
            worker.cancel()
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
            val worker = harness.pipeline.launchIn(this)

            harness.write("AppA")
            advanceTimeBy(150.milliseconds)
            harness.write("AppB")
            advanceUntilIdle()

            assertEquals(listOf(1, 2), harness.consumed.map { it.second.sequence })
            assertEquals(1, maxActive)
            worker.cancel()
        }

    @Test
    fun `null snapshot consumes nothing and still marks the sequence processed`() =
        runTest {
            val harness = Harness()
            harness.onSnapshot = { null }
            val worker = harness.pipeline.launchIn(this)

            harness.write("Excluded")
            advanceUntilIdle()

            assertEquals(1, harness.snapshots.size)
            assertTrue(harness.consumed.isEmpty())

            // A duplicate signal for an already processed sequence does not re-read.
            harness.pipeline.onEvent(1, "Excluded")
            advanceUntilIdle()

            assertEquals(1, harness.snapshots.size)
            worker.cancel()
        }

    @Test
    fun `quiet window restarts are bounded when the clipboard never settles`() =
        runTest {
            val harness = Harness(maxQuietWindowRestarts = 2)
            val worker = harness.pipeline.launchIn(this)

            harness.write("AppA")
            repeat(10) {
                advanceTimeBy(60.milliseconds)
                harness.write("AppA")
            }
            advanceUntilIdle()

            assertTrue(harness.consumed.isNotEmpty())
            // The first pass stops extending after two restarts instead of waiting out
            // the whole write storm.
            assertEquals(
                4,
                harness.consumed
                    .first()
                    .second.sequence,
            )
            worker.cancel()
        }
}
