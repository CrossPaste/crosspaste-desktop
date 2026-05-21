package com.crosspaste.ui.contextmenu

import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression coverage for the sticky-hover index flow that drives nested submenu visibility in
 * [MaterialContextMenuRepresentation]. The behaviors under test were a previous source of UAF-like
 * bugs in the upstream `dzirbel` library: dropping the sticky "last hovered" behavior would make
 * the submenu close as soon as the pointer crosses the gap between parent and child.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HoveredIndexTest {

    @Test
    fun `empty iterable emits -1 and nothing else`() =
        runTest {
            val result = emptyList<InteractionSource>().hoveredIndex().first()
            assertEquals(-1, result)
        }

    @Test
    fun `initial emission for non-empty iterable with no hovers is -1`() =
        runTest(UnconfinedTestDispatcher()) {
            val sources = List(3) { MutableInteractionSource() }
            val emissions = collectIndices(sources)
            assertEquals(listOf(-1), emissions.distinct())
        }

    @Test
    fun `hovering source 0 emits 0`() =
        runTest(UnconfinedTestDispatcher()) {
            val sources = List(3) { MutableInteractionSource() }
            val emissions =
                collectIndices(sources) {
                    sources[0].emit(HoverInteraction.Enter())
                }
            assertTrue(0 in emissions, "expected 0 in $emissions")
        }

    @Test
    fun `hovering source 1 emits 1`() =
        runTest(UnconfinedTestDispatcher()) {
            val sources = List(3) { MutableInteractionSource() }
            val emissions =
                collectIndices(sources) {
                    sources[1].emit(HoverInteraction.Enter())
                }
            assertTrue(1 in emissions, "expected 1 in $emissions")
        }

    @Test
    fun `exit after enter keeps last index sticky`() =
        runTest(UnconfinedTestDispatcher()) {
            val sources = List(2) { MutableInteractionSource() }
            val enter = HoverInteraction.Enter()
            val emissions =
                collectIndices(sources) {
                    sources[1].emit(enter)
                    sources[1].emit(HoverInteraction.Exit(enter))
                }
            assertEquals(1, emissions.last(), "after Exit the index must not fall back to -1")
        }

    @Test
    fun `moving hover between items emits both indices in order`() =
        runTest(UnconfinedTestDispatcher()) {
            val sources = List(3) { MutableInteractionSource() }
            val enter0 = HoverInteraction.Enter()
            val enter2 = HoverInteraction.Enter()
            val emissions =
                collectIndices(sources) {
                    sources[0].emit(enter0)
                    sources[0].emit(HoverInteraction.Exit(enter0))
                    sources[2].emit(enter2)
                }
            val nonNegative = emissions.filter { it >= 0 }
            assertTrue(
                nonNegative.indexOf(0) < nonNegative.indexOf(2),
                "expected 0 before 2 in $nonNegative",
            )
            assertEquals(2, emissions.last(), "last hovered index should stick")
        }

    @Test
    fun `unbalanced exit before any enter does not crash and stays at -1`() =
        runTest(UnconfinedTestDispatcher()) {
            val sources = List(2) { MutableInteractionSource() }
            val ghostEnter = HoverInteraction.Enter()
            val emissions =
                collectIndices(sources) {
                    // Emit Exit without a paired Enter — pushes runningFold counter to -1.
                    sources[0].emit(HoverInteraction.Exit(ghostEnter))
                }
            assertEquals(-1, emissions.last())
        }

    @Test
    fun `overlapping enters from one source stay hovered until all are exited`() =
        runTest(UnconfinedTestDispatcher()) {
            // Compose can deliver nested Enter/Enter/Exit when pointer events overlap (e.g. a
            // child element generates its own pair). The runningFold counter must reflect the
            // outstanding count, not flip to "not hovered" on the first Exit.
            val sources = List(2) { MutableInteractionSource() }
            val enterA = HoverInteraction.Enter()
            val enterB = HoverInteraction.Enter()
            val emissions =
                collectIndices(sources) {
                    sources[0].emit(enterA)
                    sources[0].emit(enterB)
                    sources[0].emit(HoverInteraction.Exit(enterA))
                }
            assertEquals(
                0,
                emissions.last(),
                "source 0 still has an outstanding Enter (counter > 0) so index must remain 0",
            )
        }

    @Test
    fun `balanced exits drop hover off the source but leave the sticky index alone`() =
        runTest(UnconfinedTestDispatcher()) {
            val sources = List(2) { MutableInteractionSource() }
            val enterA = HoverInteraction.Enter()
            val enterB = HoverInteraction.Enter()
            val emissions =
                collectIndices(sources) {
                    sources[0].emit(enterA)
                    sources[0].emit(enterB)
                    sources[0].emit(HoverInteraction.Exit(enterA))
                    sources[0].emit(HoverInteraction.Exit(enterB))
                    // All Enters balanced — combine emits -1, scan keeps the last sticky index.
                }
            assertEquals(0, emissions.last())
        }

    private suspend fun TestScope.collectIndices(
        sources: List<MutableInteractionSource>,
        emit: suspend () -> Unit = {},
    ): List<Int> {
        val emissions = mutableListOf<Int>()
        val job =
            backgroundScope.launch {
                sources.hoveredIndex().toList(emissions)
            }
        testScheduler.runCurrent()
        emit()
        testScheduler.advanceUntilIdle()
        job.cancel()
        return emissions
    }
}
