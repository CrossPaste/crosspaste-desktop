package com.crosspaste.paste

import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.clientapi.ClientApiResult
import com.crosspaste.net.clientapi.ConnectionRefused
import com.crosspaste.net.clientapi.FailureResult
import com.crosspaste.net.clientapi.SuccessResult
import com.crosspaste.net.clientapi.UnknownError
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DefaultPasteSyncProcessManagerTest {

    @Test
    fun testGetProcess() =
        runBlocking {
            val manager = DefaultPasteSyncProcessManager()
            val pasteDataId = 123L
            val taskNum = 5

            val process = manager.getProcess(pasteDataId, taskNum)

            assertNotNull(process, "Process should not be null")
            assertEquals(0.0f, process.process.value, "Initial progress should be 0")
        }

    @Test
    fun testGetProcessReturnsSameInstance() =
        runBlocking {
            val manager = DefaultPasteSyncProcessManager()
            val pasteDataId = 123L
            val taskNum = 5

            val process1 = manager.getProcess(pasteDataId, taskNum)
            val process2 = manager.getProcess(pasteDataId, taskNum)

            assertSame(process1, process2, "Should return the same process instance for the same pasteDataId")
        }

    @Test
    fun testGetProcessWithDifferentIds() =
        runBlocking {
            val manager = DefaultPasteSyncProcessManager()
            val pasteDataId1 = 123L
            val pasteDataId2 = 456L
            val taskNum = 3

            val process1 = manager.getProcess(pasteDataId1, taskNum)
            val process2 = manager.getProcess(pasteDataId2, taskNum)

            assertNotSame(process1, process2, "Should return different process instances for different pasteDataIds")
        }

    @Test
    fun testProcessMapInitiallyEmpty() {
        val manager = DefaultPasteSyncProcessManager()
        val processMap = manager.processMap.value

        assertTrue(processMap.isEmpty(), "Process map should be initially empty")
    }

    @Test
    fun testProcessMapContainsCreatedProcess() =
        runBlocking {
            val manager = DefaultPasteSyncProcessManager()
            val pasteDataId = 123L
            val taskNum = 3

            val process = manager.getProcess(pasteDataId, taskNum)
            val processMap = manager.processMap.value

            assertTrue(processMap.containsKey(pasteDataId), "Process map should contain the created process")
            assertEquals(process, processMap[pasteDataId], "Process map should contain the correct process instance")
        }

    @Test
    fun testCleanProcess() =
        runBlocking {
            val manager = DefaultPasteSyncProcessManager()
            val pasteDataId = 123L
            val taskNum = 3

            // Create a process
            manager.getProcess(pasteDataId, taskNum)
            assertTrue(manager.processMap.value.containsKey(pasteDataId), "Process should exist before cleaning")

            // Clean the process
            manager.cleanProcess(pasteDataId)
            assertFalse(manager.processMap.value.containsKey(pasteDataId), "Process should be removed after cleaning")
        }

    @Test
    fun testCleanNonExistentProcess() =
        runBlocking {
            val manager = DefaultPasteSyncProcessManager()
            val pasteDataId = 123L

            // Should not throw exception when cleaning non-existent process
            manager.cleanProcess(pasteDataId)
            assertTrue(manager.processMap.value.isEmpty(), "Process map should remain empty")
        }

    @Test
    fun testRunTaskWithSuccessfulTasks() =
        runBlocking {
            val manager = DefaultPasteSyncProcessManager()
            val pasteDataId = 123L

            val tasks =
                listOf<suspend () -> Pair<Int, ClientApiResult>>(
                    { Pair(0, SuccessResult("Task 0 completed")) },
                    { Pair(1, SuccessResult("Task 1 completed")) },
                    { Pair(2, SuccessResult("Task 2 completed")) },
                )

            val results = manager.runTask(pasteDataId, tasks)

            assertEquals(3, results.size, "Should return results for all tasks")
            results.forEachIndexed { index, (taskIndex, result) ->
                assertEquals(index, taskIndex, "Task index should match")
                assertTrue(result is SuccessResult, "All results should be successful")
            }
        }

    @Test
    fun testRunTaskWithMixedResults() =
        runBlocking {
            val manager = DefaultPasteSyncProcessManager()
            val pasteDataId = 123L

            val tasks =
                listOf<suspend () -> Pair<Int, ClientApiResult>>(
                    { Pair(0, SuccessResult("Task 0 completed")) },
                    {
                        Pair(
                            1,
                            FailureResult(
                                com.crosspaste.exception.PasteException(StandardErrorCode.UNKNOWN_ERROR.toErrorCode()),
                            ),
                        )
                    },
                    { Pair(2, ConnectionRefused) },
                    { Pair(3, SuccessResult("Task 3 completed")) },
                )

            val results = manager.runTask(pasteDataId, tasks)

            assertEquals(4, results.size, "Should return results for all tasks")
            assertTrue(results[0].second is SuccessResult, "Task 0 should succeed")
            assertTrue(results[1].second is FailureResult, "Task 1 should fail")
            assertTrue(results[2].second is ConnectionRefused, "Task 2 should be connection refused")
            assertTrue(results[3].second is SuccessResult, "Task 3 should succeed")
        }

    @Test
    fun testRunTaskUpdatesProgress() =
        runBlocking {
            val manager = DefaultPasteSyncProcessManager()
            val pasteDataId = 123L

            val tasks =
                listOf<suspend () -> Pair<Int, ClientApiResult>>(
                    {
                        delay(50)
                        Pair(0, SuccessResult("Task 0"))
                    },
                    {
                        delay(100)
                        Pair(1, SuccessResult("Task 1"))
                    },
                )

            // Get process before running tasks
            val process = manager.getProcess(pasteDataId, tasks.size)
            assertEquals(0.0f, process.process.value, "Initial progress should be 0")

            // Run tasks
            manager.runTask(pasteDataId, tasks)

            // Progress should be 1.0 (100%) after all tasks complete
            assertEquals(1.0f, process.process.value, "Progress should be 100% after all successful tasks")
        }

    @Test
    fun testRunTaskDoesNotUpdateProgressForFailures() =
        runBlocking {
            val manager = DefaultPasteSyncProcessManager()
            val pasteDataId = 123L

            val tasks =
                listOf<suspend () -> Pair<Int, ClientApiResult>>(
                    { Pair(0, SuccessResult("Success")) },
                    {
                        Pair(
                            1,
                            FailureResult(
                                com.crosspaste.exception.PasteException(StandardErrorCode.UNKNOWN_ERROR.toErrorCode()),
                            ),
                        )
                    },
                    { Pair(2, UnknownError) },
                )

            val process = manager.getProcess(pasteDataId, tasks.size)
            manager.runTask(pasteDataId, tasks)

            // Only 1 out of 3 tasks succeeded
            assertEquals(1.0f / 3.0f, process.process.value, 0.001f, "Progress should reflect only successful tasks")
        }

    @Test
    fun testConcurrentTaskExecution() =
        runBlocking {
            val manager = DefaultPasteSyncProcessManager()
            val pasteDataId = 123L
            val executionOrder = ConcurrentHashMap<Int, Long>()

            val tasks =
                (0..4).map { index ->
                    suspend {
                        val startTime = System.currentTimeMillis()
                        delay(50) // Simulate work
                        executionOrder[index] = startTime
                        Pair(index, SuccessResult("Task $index"))
                    }
                }

            val startTime = System.currentTimeMillis()
            val results = manager.runTask(pasteDataId, tasks)
            val endTime = System.currentTimeMillis()

            assertEquals(5, results.size, "All tasks should complete")
            assertTrue(endTime - startTime < 200, "Tasks should run concurrently (total time < 5 * 50ms)")
            assertEquals(5, executionOrder.size, "All tasks should have recorded execution times")
        }

    @Test
    fun testSemaphoreLimit() =
        runBlocking {
            val manager = DefaultPasteSyncProcessManager()
            val pasteDataId = 123L
            val concurrentTasks = AtomicInteger(0)
            val maxConcurrency = AtomicInteger(0)

            // Create 15 tasks (more than semaphore limit of 10)
            val tasks =
                (0..14).map { index ->
                    suspend {
                        val current = concurrentTasks.incrementAndGet()
                        maxConcurrency.set(maxOf(maxConcurrency.get(), current))
                        delay(100) // Simulate work
                        concurrentTasks.decrementAndGet()
                        Pair(index, SuccessResult("Task $index"))
                    }
                }

            manager.runTask(pasteDataId, tasks)

            assertTrue(maxConcurrency.get() <= 10, "Should not exceed semaphore limit of 10 concurrent tasks")
            assertEquals(0, concurrentTasks.get(), "All tasks should complete")
        }

    @Test
    fun testMultiplePasteDataIdsConcurrently() =
        runBlocking {
            val manager = DefaultPasteSyncProcessManager()
            val results = ConcurrentHashMap<Long, List<Pair<Int, ClientApiResult>>>()

            val jobs =
                (1L..5L).map { pasteDataId ->
                    async {
                        val tasks =
                            (0..2).map { taskIndex ->
                                suspend {
                                    delay(50)
                                    Pair(taskIndex, SuccessResult("Task $taskIndex for paste $pasteDataId"))
                                }
                            }
                        results[pasteDataId] = manager.runTask(pasteDataId, tasks)
                    }
                }

            jobs.awaitAll()

            assertEquals(5, results.size, "Should handle all paste data IDs")
            results.forEach { (pasteDataId, taskResults) ->
                assertEquals(3, taskResults.size, "Each paste should have 3 task results")
                taskResults.forEach { (_, result) ->
                    assertTrue(result is SuccessResult, "All tasks should succeed for paste $pasteDataId")
                }
            }
        }

    @Test
    fun testTaskExceptionHandling() =
        runBlocking {
            val manager = DefaultPasteSyncProcessManager()
            val pasteDataId = 123L

            val tasks =
                listOf<suspend () -> Pair<Int, ClientApiResult>>(
                    { Pair(0, SuccessResult("Success")) },
                    { throw RuntimeException("Task exception") },
                    { Pair(2, SuccessResult("Success after exception")) },
                )

            // Should not throw exception even if individual task throws
            val results = runCatching { manager.runTask(pasteDataId, tasks) }

            assertTrue(results.isFailure, "Should propagate task exceptions")
        }

    @Test
    fun testEmptyTaskList() =
        runBlocking {
            val manager = DefaultPasteSyncProcessManager()
            val pasteDataId = 123L

            val results = manager.runTask(pasteDataId, emptyList())

            assertTrue(results.isEmpty(), "Should return empty list for empty task list")
        }

    @Test
    fun testProgressFlowUpdates() =
        runBlocking {
            val manager = DefaultPasteSyncProcessManager()
            val pasteDataId = 123L
            val progressValues = mutableListOf<Float>()

            val process = manager.getProcess(pasteDataId, 2)

            // Collect progress updates
            val collectJob =
                async {
                    process.process.collect { progress ->
                        progressValues.add(progress)
                    }
                }

            delay(50) // Let collection start

            val tasks =
                listOf<suspend () -> Pair<Int, ClientApiResult>>(
                    {
                        delay(100)
                        Pair(0, SuccessResult("Task 0"))
                    },
                    {
                        delay(200)
                        Pair(1, SuccessResult("Task 1"))
                    },
                )

            manager.runTask(pasteDataId, tasks)

            delay(50) // Let final progress update propagate
            collectJob.cancel()

            assertTrue(progressValues.contains(0.0f), "Should contain initial progress")
            assertTrue(progressValues.contains(0.5f), "Should contain intermediate progress")
            assertTrue(progressValues.contains(1.0f), "Should contain final progress")
        }

    @Test
    fun testThreadSafetyWithHighConcurrency() =
        runBlocking {
            val manager = DefaultPasteSyncProcessManager()
            val numberOfOperations = 50
            val latch = CountDownLatch(numberOfOperations)
            val exceptions = ConcurrentHashMap<Int, Exception>()
            val results = ConcurrentHashMap<Int, Any>()

            repeat(numberOfOperations) { operationId ->
                Thread {
                    try {
                        runBlocking {
                            val pasteDataId = (operationId % 10).toLong()
                            manager.getProcess(pasteDataId, 1)
                            val taskResults =
                                manager.runTask(
                                    pasteDataId,
                                    listOf {
                                        Pair(0, SuccessResult("Operation $operationId"))
                                    },
                                )
                            results[operationId] = taskResults
                        }
                    } catch (e: Exception) {
                        exceptions[operationId] = e
                    } finally {
                        latch.countDown()
                    }
                }.start()
            }

            assertTrue(
                latch.await(30, TimeUnit.SECONDS),
                "All operations should complete within timeout",
            )

            assertEquals(
                0,
                exceptions.size,
                "No exceptions should occur during concurrent operations: ${exceptions.values.map { it.message }}",
            )

            assertEquals(
                numberOfOperations,
                results.size,
                "All operations should produce results",
            )
        }
}
