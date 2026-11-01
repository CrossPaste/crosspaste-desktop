package com.crosspaste.app

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class FullscreenPollingTest {
    private val logger = KotlinLogging.logger {}

    @Test
    fun `emits at once, skips repeats, treats failures as not full screen and stops on cancel`() =
        runTest {
            val answers = ArrayDeque(listOf<Boolean?>(false, true, true, null, false))
            var probes = 0
            val emitted = mutableListOf<Boolean>()

            val job =
                pollFullscreen(500.milliseconds, logger) {
                    probes++
                    // null stands for a probe that throws
                    answers.removeFirstOrNull() ?: error("probe failed")
                }.onEach { emitted += it }.launchIn(this)

            runCurrent()
            assertEquals(listOf(false), emitted)

            advanceTimeBy(2000.milliseconds)
            runCurrent()
            assertEquals(listOf(false, true, false), emitted)
            assertEquals(5, probes)

            job.cancel()
            advanceTimeBy(2000.milliseconds)
            assertEquals(5, probes)
        }
}
