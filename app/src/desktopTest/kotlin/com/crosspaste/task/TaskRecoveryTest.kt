package com.crosspaste.task

import com.crosspaste.db.TestDriverFactory
import com.crosspaste.db.createDatabase
import com.crosspaste.db.task.PasteTask
import com.crosspaste.db.task.SqlTaskDao
import com.crosspaste.db.task.TaskStatus
import com.crosspaste.db.task.TaskType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Exercises startup recovery against a real [SqlTaskDao]: tasks durably
 * recorded by a "previous process" (rows written before the executor is
 * asked to recover) must run to completion in the "next process".
 */
class TaskRecoveryTest {

    private val driverFactory = TestDriverFactory()
    private val database = createDatabase(driverFactory)
    private val taskDao = SqlTaskDao(database)

    @AfterTest
    fun tearDown() {
        driverFactory.closeDriver()
    }

    /**
     * In production the claim bound is captured at next-process startup, long
     * after the orphaned rows were written; a future bound models that without
     * relying on the test's task creation and recovery call landing in
     * different milliseconds.
     */
    private fun recoveryBound(): Long = System.currentTimeMillis() + 10000

    private class RecordingExecutor : SingleTypeTaskExecutor {
        val executedTaskIds = Channel<Long>(Channel.UNLIMITED)

        override val taskType: Int = TaskType.DELETE_PASTE_TASK

        override suspend fun doExecuteTask(pasteTask: PasteTask): PasteTaskResult {
            executedTaskIds.send(pasteTask.taskId)
            return SuccessPasteTaskResult()
        }
    }

    private fun newExecutor(recording: RecordingExecutor): TaskExecutor =
        TaskExecutor(
            listOf(recording),
            taskDao,
            scope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
        )

    private suspend fun awaitStatus(
        taskId: Long,
        status: Int,
    ) {
        withTimeout(5.seconds) {
            while (taskDao.getTask(taskId)?.status != status) {
                delay(10.milliseconds)
            }
        }
    }

    @Test
    fun `PREPARING task committed but never submitted executes after recovery`() =
        runTest {
            val recording = RecordingExecutor()
            val executor = newExecutor(recording)

            // Simulates a crash between the task commit and the channel submit.
            val taskId = taskDao.createTask(pasteDataId = 1L, taskType = TaskType.DELETE_PASTE_TASK)

            executor.recoverPersistedTasks(recoveryBound())

            withContext(Dispatchers.Default) {
                withTimeout(5.seconds) {
                    assertEquals(taskId, recording.executedTaskIds.receive())
                }
                awaitStatus(taskId, TaskStatus.SUCCESS)
            }

            executor.shutdown()
        }

    @Test
    fun `stale EXECUTING task left by an interrupted process is re-executed`() =
        runTest {
            val recording = RecordingExecutor()
            val executor = newExecutor(recording)

            // Simulates a crash while the task was executing.
            val taskId = taskDao.createTask(pasteDataId = 1L, taskType = TaskType.DELETE_PASTE_TASK)
            taskDao.executingTask(taskId)

            executor.recoverPersistedTasks(recoveryBound())

            withContext(Dispatchers.Default) {
                withTimeout(5.seconds) {
                    assertEquals(taskId, recording.executedTaskIds.receive())
                }
                awaitStatus(taskId, TaskStatus.SUCCESS)
            }

            executor.shutdown()
        }

    @Test
    fun `task submitted by an in-flight producer after shutdown stays PREPARING`() =
        runTest {
            val recording = RecordingExecutor()
            val executor = newExecutor(recording)

            executor.shutdown()

            // Simulates a producer draining during shutdown: the row was
            // committed and the submit must not throw or corrupt its status.
            val taskId = taskDao.createTask(pasteDataId = 1L, taskType = TaskType.DELETE_PASTE_TASK)
            executor.submitTask(taskId)

            assertEquals(TaskStatus.PREPARING, taskDao.getTask(taskId)?.status)
        }
}
