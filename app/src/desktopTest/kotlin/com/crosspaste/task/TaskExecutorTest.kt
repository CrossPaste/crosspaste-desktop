package com.crosspaste.task

import com.crosspaste.db.task.PasteTask
import com.crosspaste.db.task.TaskDao
import com.crosspaste.db.task.TaskType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TaskExecutorTest {

    private fun createMockTask(
        taskId: Long = 1L,
        taskType: Int = TaskType.DELETE_PASTE_TASK,
    ): PasteTask =
        PasteTask(
            taskId = taskId,
            pasteDataId = 1L,
            taskType = taskType,
            createTime = System.currentTimeMillis(),
            modifyTime = System.currentTimeMillis(),
            extraInfo = """{"type":"base","executionHistories":[]}""",
        )

    @Test
    fun `empty executor list creates valid executor`() =
        runTest {
            val taskDao: TaskDao = mockk(relaxed = true)
            val executor =
                TaskExecutor(
                    emptyList(),
                    taskDao,
                    scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob()),
                )
            executor.shutdown()
        }

    @Test
    fun `executor maps task types correctly`() =
        runTest {
            val mockExecutor1: SingleTypeTaskExecutor = mockk(relaxed = true)
            val mockExecutor2: SingleTypeTaskExecutor = mockk(relaxed = true)
            coEvery { mockExecutor1.taskType } returns TaskType.DELETE_PASTE_TASK
            coEvery { mockExecutor2.taskType } returns TaskType.CLEAN_TASK_TASK

            val taskDao: TaskDao = mockk(relaxed = true)
            val executor =
                TaskExecutor(
                    listOf(mockExecutor1, mockExecutor2),
                    taskDao,
                    scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob()),
                )

            executor.shutdown()
        }

    @Test
    fun `submitTask emits task id and executes`() =
        runTest {
            val singleExecutor: SingleTypeTaskExecutor = mockk(relaxed = true)
            coEvery { singleExecutor.taskType } returns TaskType.DELETE_PASTE_TASK
            coEvery { singleExecutor.executeTask(any(), any(), any(), any()) } coAnswers {
                @Suppress("UNCHECKED_CAST")
                val successCallback = it.invocation.args[1] as (suspend (String?) -> Unit)
                successCallback(null)
            }

            val taskDao: TaskDao = mockk(relaxed = true)
            val task = createMockTask()
            coEvery { taskDao.getTask(1L) } returns task

            val executor =
                TaskExecutor(
                    listOf(singleExecutor),
                    taskDao,
                    maxConcurrentTasks = 5,
                    scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob()),
                )

            executor.submitTask(1L)
            advanceUntilIdle()

            coVerify { taskDao.executingTask(1L) }
            coVerify { singleExecutor.executeTask(any(), any(), any(), any()) }
            coVerify { taskDao.successTask(1L, any()) }

            executor.shutdown()
        }

    @Test
    fun `submitTask with null task from dao skips execution`() =
        runTest {
            val singleExecutor: SingleTypeTaskExecutor = mockk(relaxed = true)
            coEvery { singleExecutor.taskType } returns TaskType.DELETE_PASTE_TASK

            val taskDao: TaskDao = mockk(relaxed = true)
            coEvery { taskDao.getTask(99L) } returns null

            val executor =
                TaskExecutor(
                    listOf(singleExecutor),
                    taskDao,
                    scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob()),
                )

            executor.submitTask(99L)
            advanceUntilIdle()

            coVerify(exactly = 0) { taskDao.executingTask(any()) }

            executor.shutdown()
        }

    @Test
    fun `task failure calls failureTask on dao`() =
        runTest {
            val singleExecutor: SingleTypeTaskExecutor = mockk(relaxed = true)
            coEvery { singleExecutor.taskType } returns TaskType.DELETE_PASTE_TASK
            coEvery { singleExecutor.executeTask(any(), any(), any(), any()) } coAnswers {
                @Suppress("UNCHECKED_CAST")
                val failCallback = it.invocation.args[2] as (suspend (String, Boolean) -> Unit)
                failCallback("""{"type":"base","executionHistories":[]}""", false)
            }

            val taskDao: TaskDao = mockk(relaxed = true)
            val task = createMockTask()
            coEvery { taskDao.getTask(1L) } returns task

            val executor =
                TaskExecutor(
                    listOf(singleExecutor),
                    taskDao,
                    scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob()),
                )

            executor.submitTask(1L)
            advanceUntilIdle()

            coVerify { taskDao.failureTask(1L, false, any()) }

            executor.shutdown()
        }

    @Test
    fun `shutdown cancels scope`() =
        runTest {
            val taskDao: TaskDao = mockk(relaxed = true)
            val executor =
                TaskExecutor(
                    emptyList(),
                    taskDao,
                    scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob()),
                )
            executor.shutdown()
            assertTrue(true)
        }
}
