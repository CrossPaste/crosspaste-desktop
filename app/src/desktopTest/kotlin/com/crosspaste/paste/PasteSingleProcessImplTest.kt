package com.crosspaste.paste

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PasteSingleProcessImplTest {

    @Test
    fun testInitialProgress() {
        val process = PasteSingleProcessImpl(5)

        assertEquals(0.0f, process.process.value, "Initial progress should be 0")
    }

    @Test
    fun testSingleTaskSuccess() {
        val process = PasteSingleProcessImpl(1)

        process.success(0)

        assertEquals(1.0f, process.process.value, "Progress should be 100% after single task success")
    }

    @Test
    fun testMultipleTasksProgress() {
        val process = PasteSingleProcessImpl(4)

        // Complete tasks one by one
        process.success(0)
        assertEquals(0.25f, process.process.value, 0.001f, "Progress should be 25% after first task")

        process.success(1)
        assertEquals(0.5f, process.process.value, 0.001f, "Progress should be 50% after second task")

        process.success(2)
        assertEquals(0.75f, process.process.value, 0.001f, "Progress should be 75% after third task")

        process.success(3)
        assertEquals(1.0f, process.process.value, 0.001f, "Progress should be 100% after all tasks")
    }

    @Test
    fun testOutOfRangeIndex() {
        val process = PasteSingleProcessImpl(3)

        // Test negative index
        process.success(-1)
        assertEquals(0.0f, process.process.value, "Progress should remain 0 for negative index")

        // Test index too large
        process.success(3)
        assertEquals(0.0f, process.process.value, "Progress should remain 0 for out-of-range index")

        // Test valid index still works
        process.success(0)
        assertEquals(1.0f / 3.0f, process.process.value, 0.001f, "Valid index should still work")
    }

    @Test
    fun testDuplicateTaskSuccess() {
        val process = PasteSingleProcessImpl(3)

        // Complete same task multiple times
        process.success(0)
        assertEquals(1.0f / 3.0f, process.process.value, 0.001f, "Progress should be 1/3 after first completion")

        process.success(0)
        assertEquals(
            1.0f / 3.0f,
            process.process.value,
            0.001f,
            "Progress should remain same after duplicate completion",
        )

        // Complete different task
        process.success(1)
        assertEquals(2.0f / 3.0f, process.process.value, 0.001f, "Progress should increase with new task")
    }

    @Test
    fun testRandomOrderCompletion() {
        val process = PasteSingleProcessImpl(5)

        // Complete tasks in random order
        process.success(3)
        assertEquals(0.2f, process.process.value, 0.001f, "Progress should be 20% after first task")

        process.success(0)
        assertEquals(0.4f, process.process.value, 0.001f, "Progress should be 40% after second task")

        process.success(4)
        assertEquals(0.6f, process.process.value, 0.001f, "Progress should be 60% after third task")

        process.success(1)
        assertEquals(0.8f, process.process.value, 0.001f, "Progress should be 80% after fourth task")

        process.success(2)
        assertEquals(1.0f, process.process.value, 0.001f, "Progress should be 100% after all tasks")
    }

    @Test
    fun testZeroTasks() {
        val process = PasteSingleProcessImpl(0)

        assertEquals(0.0f, process.process.value, "Progress should be 0 for zero tasks")

        // Any success call should not change anything (and not crash)
        process.success(0)
        assertEquals(0.0f, process.process.value, "Progress should remain 0 with zero tasks")
    }

    @Test
    fun testConcurrentAccess() =
        runBlocking {
            val process = PasteSingleProcessImpl(100)
            val numberOfThreads = 50
            val successfulOperations = ConcurrentHashMap<Int, Boolean>()

            val jobs =
                (0 until numberOfThreads).map { threadId ->
                    async {
                        // Each thread completes 2 tasks
                        val task1 = threadId * 2
                        val task2 = threadId * 2 + 1

                        if (task1 < 100) {
                            process.success(task1)
                            successfulOperations[task1] = true
                        }

                        if (task2 < 100) {
                            process.success(task2)
                            successfulOperations[task2] = true
                        }
                    }
                }

            jobs.awaitAll()

            // All valid tasks should be completed
            assertEquals(
                successfulOperations.size.toFloat() / 100.0f,
                process.process.value,
                0.001f,
                "Progress should reflect all completed tasks",
            )
        }

    @Test
    fun testThreadSafety() {
        val process = PasteSingleProcessImpl(1000)
        val numberOfThreads = 100
        val latch = CountDownLatch(numberOfThreads)
        val exceptions = ConcurrentHashMap<Int, Exception>()

        repeat(numberOfThreads) { threadId ->
            Thread {
                try {
                    // Each thread completes 10 tasks
                    repeat(10) { taskOffset ->
                        val taskIndex = threadId * 10 + taskOffset
                        if (taskIndex < 1000) {
                            process.success(taskIndex)
                        }
                    }
                } catch (e: Exception) {
                    exceptions[threadId] = e
                } finally {
                    latch.countDown()
                }
            }.start()
        }

        assertTrue(
            latch.await(30, TimeUnit.SECONDS),
            "All threads should complete within timeout",
        )

        assertEquals(
            0,
            exceptions.size,
            "No exceptions should occur during concurrent access: ${exceptions.values.map { it.message }}",
        )

        assertEquals(
            1.0f,
            process.process.value,
            "All tasks should be completed",
        )
    }

    @Test
    fun testProgressFlowEmission() =
        runBlocking {
            val process = PasteSingleProcessImpl(3)
            val progressValues = mutableListOf<Float>()

            // Collect progress updates
            val collectJob =
                async {
                    process.process.collect { progress ->
                        progressValues.add(progress)
                    }
                }

            kotlinx.coroutines.delay(50) // Let collection start

            // Update progress
            process.success(0)
            kotlinx.coroutines.delay(10)

            process.success(1)
            kotlinx.coroutines.delay(10)

            process.success(2)
            kotlinx.coroutines.delay(10)

            collectJob.cancel()

            assertTrue(progressValues.contains(0.0f), "Should contain initial progress")
            assertTrue(progressValues.any { kotlin.math.abs(it - 1.0f / 3.0f) < 0.001f }, "Should contain 1/3 progress")
            assertTrue(progressValues.any { kotlin.math.abs(it - 2.0f / 3.0f) < 0.001f }, "Should contain 2/3 progress")
            assertTrue(progressValues.contains(1.0f), "Should contain final progress")
        }

    @Test
    fun testLargeTaskCount() {
        val process = PasteSingleProcessImpl(10000)

        // Complete every 1000th task
        for (i in 0 until 10000 step 1000) {
            process.success(i)
        }

        assertEquals(0.001f, process.process.value, 0.0001f, "Progress should be 0.1% (10 out of 10000)")
    }

    @Test
    fun testSingleTaskMultipleAttempts() {
        val process = PasteSingleProcessImpl(1)

        // Complete the same task multiple times
        repeat(100) {
            process.success(0)
        }

        assertEquals(1.0f, process.process.value, "Progress should be 100% regardless of multiple completions")
    }

    @Test
    fun testMixedValidAndInvalidIndices() {
        val process = PasteSingleProcessImpl(5)

        // Mix valid and invalid indices
        process.success(-10) // Invalid
        process.success(0) // Valid
        process.success(100) // Invalid
        process.success(2) // Valid
        process.success(-1) // Invalid
        process.success(4) // Valid

        assertEquals(0.6f, process.process.value, 0.001f, "Should only count valid indices (3 out of 5)")
    }

    @Test
    fun testProgressMonotonicity() {
        val process = PasteSingleProcessImpl(10)
        var previousProgress = 0.0f

        for (i in 0 until 10) {
            process.success(i)
            val currentProgress = process.process.value

            assertTrue(
                currentProgress >= previousProgress,
                "Progress should be monotonically increasing: $previousProgress -> $currentProgress at step $i",
            )

            previousProgress = currentProgress
        }

        assertEquals(1.0f, previousProgress, "Final progress should be 100%")
    }
}
