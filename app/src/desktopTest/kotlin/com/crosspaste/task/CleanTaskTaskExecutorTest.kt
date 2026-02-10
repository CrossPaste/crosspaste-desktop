package com.crosspaste.task

import com.crosspaste.db.task.BaseExtraInfo
import com.crosspaste.db.task.PasteTask
import com.crosspaste.db.task.TaskDao
import com.crosspaste.db.task.TaskType
import com.crosspaste.utils.getJsonUtils
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class CleanTaskTaskExecutorTest {

    private val jsonUtils = getJsonUtils()

    private fun createPasteTask(extraInfo: BaseExtraInfo = BaseExtraInfo()): PasteTask {
        val extraInfoJson =
            jsonUtils.JSON.encodeToString(
                com.crosspaste.db.task.PasteTaskExtraInfo
                    .serializer(),
                extraInfo,
            )
        return PasteTask(
            taskId = 1L,
            pasteDataId = null,
            taskType = TaskType.CLEAN_TASK_TASK,
            createTime = System.currentTimeMillis(),
            modifyTime = System.currentTimeMillis(),
            extraInfo = extraInfoJson,
        )
    }

    @Test
    fun `taskType is CLEAN_TASK_TASK`() {
        val taskDao: TaskDao = mockk(relaxed = true)
        val executor = CleanTaskTaskExecutor(taskDao)
        assertTrue(executor.taskType == TaskType.CLEAN_TASK_TASK)
    }

    @Test
    fun `successful execution cleans success and failure tasks`() =
        runTest {
            val taskDao: TaskDao = mockk(relaxed = true)
            val executor = CleanTaskTaskExecutor(taskDao)
            val task = createPasteTask()

            val result = executor.doExecuteTask(task)

            assertTrue(result is SuccessPasteTaskResult)
            coVerify { taskDao.cleanSuccessTask(any()) }
            coVerify { taskDao.cleanFailureTask(any()) }
        }

    @Test
    fun `cleanSuccessTask is called with time 12 hours ago`() =
        runTest {
            val taskDao: TaskDao = mockk(relaxed = true)
            val executor = CleanTaskTaskExecutor(taskDao)
            val task = createPasteTask()

            executor.doExecuteTask(task)

            coVerify {
                taskDao.cleanSuccessTask(
                    withArg { timestamp ->
                        val now = System.currentTimeMillis()
                        val twelveHoursMs = 12 * 60 * 60 * 1000L
                        // Timestamp should be roughly 12 hours ago (within 10s tolerance)
                        assertTrue(timestamp in (now - twelveHoursMs - 10000)..(now - twelveHoursMs + 10000))
                    },
                )
            }
        }

    @Test
    fun `cleanFailureTask is called with time 3 days ago`() =
        runTest {
            val taskDao: TaskDao = mockk(relaxed = true)
            val executor = CleanTaskTaskExecutor(taskDao)
            val task = createPasteTask()

            executor.doExecuteTask(task)

            coVerify {
                taskDao.cleanFailureTask(
                    withArg { timestamp ->
                        val now = System.currentTimeMillis()
                        val threeDaysMs = 3 * 24 * 60 * 60 * 1000L
                        // Timestamp should be roughly 3 days ago (within 10s tolerance)
                        assertTrue(timestamp in (now - threeDaysMs - 10000)..(now - threeDaysMs + 10000))
                    },
                )
            }
        }

    @Test
    fun `exception during clean returns failure with retry`() =
        runTest {
            val taskDao: TaskDao = mockk(relaxed = true)
            coEvery { taskDao.cleanSuccessTask(any()) } throws RuntimeException("DB error")
            val executor = CleanTaskTaskExecutor(taskDao)
            val task = createPasteTask()

            val result = executor.doExecuteTask(task)

            assertTrue(result is FailurePasteTaskResult)
            val failResult = result as FailurePasteTaskResult
            // First execution (empty history) → retry allowed (history.size < 2)
            assertTrue(failResult.needRetry)
        }

    @Test
    fun `exception with exhausted retries returns failure without retry`() =
        runTest {
            val taskDao: TaskDao = mockk(relaxed = true)
            coEvery { taskDao.cleanSuccessTask(any()) } throws RuntimeException("DB error")
            val executor = CleanTaskTaskExecutor(taskDao)

            val extraInfo = BaseExtraInfo()
            // Add 2 execution histories to exhaust retries
            extraInfo.executionHistories.add(
                com.crosspaste.db.task.ExecutionHistory(
                    startTime = 0L,
                    endTime = 1L,
                    status = 3,
                    message = "fail1",
                ),
            )
            extraInfo.executionHistories.add(
                com.crosspaste.db.task.ExecutionHistory(
                    startTime = 2L,
                    endTime = 3L,
                    status = 3,
                    message = "fail2",
                ),
            )
            val task = createPasteTask(extraInfo = extraInfo)

            val result = executor.doExecuteTask(task)

            assertTrue(result is FailurePasteTaskResult)
            val failResult = result as FailurePasteTaskResult
            assertTrue(!failResult.needRetry)
        }
}
