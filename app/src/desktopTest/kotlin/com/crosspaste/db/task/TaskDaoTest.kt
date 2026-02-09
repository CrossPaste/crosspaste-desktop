package com.crosspaste.db.task

import com.crosspaste.db.TestDriverFactory
import com.crosspaste.db.createDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TaskDaoTest {

    private val database = createDatabase(TestDriverFactory())
    private val taskDao = TaskDao(database)

    // --- Task creation ---

    @Test
    fun `createTask returns valid task id`() = runTest {
        val taskId = taskDao.createTask(
            pasteDataId = 100L,
            taskType = TaskType.SYNC_PASTE_TASK,
        )
        assert(taskId > 0)
    }

    @Test
    fun `createTask with null pasteDataId succeeds`() = runTest {
        val taskId = taskDao.createTask(
            pasteDataId = null,
            taskType = TaskType.CLEAN_TASK_TASK,
        )
        val task = taskDao.getTask(taskId)
        assertNotNull(task)
        assertNull(task.pasteDataId)
    }

    @Test
    fun `createTask stores correct task type`() = runTest {
        val taskId = taskDao.createTask(
            pasteDataId = 1L,
            taskType = TaskType.DELETE_PASTE_TASK,
        )
        val task = taskDao.getTask(taskId)
        assertNotNull(task)
        assertEquals(TaskType.DELETE_PASTE_TASK, task.taskType)
    }

    @Test
    fun `createTask sets initial status to PREPARING`() = runTest {
        val taskId = taskDao.createTask(
            pasteDataId = 1L,
            taskType = TaskType.SYNC_PASTE_TASK,
        )
        val task = taskDao.getTask(taskId)
        assertNotNull(task)
        assertEquals(TaskStatus.PREPARING, task.status)
    }

    @Test
    fun `createTask with custom extraInfo stores JSON`() = runTest {
        val extraInfo = SyncExtraInfo(appInstanceId = "test-app-1")
        val taskId = taskDao.createTask(
            pasteDataId = 1L,
            taskType = TaskType.SYNC_PASTE_TASK,
            extraInfo = extraInfo,
        )
        val task = taskDao.getTask(taskId)
        assertNotNull(task)
        assert(task.extraInfo.contains("test-app-1"))
    }

    @Test
    fun `createTaskBlock returns valid id synchronously`() {
        val taskId = taskDao.createTaskBlock(
            pasteDataId = 1L,
            taskType = TaskType.PULL_FILE_TASK,
        )
        assert(taskId > 0)
    }

    @Test
    fun `multiple createTask calls return unique ids`() = runTest {
        val id1 = taskDao.createTask(pasteDataId = 1L, taskType = TaskType.SYNC_PASTE_TASK)
        val id2 = taskDao.createTask(pasteDataId = 2L, taskType = TaskType.SYNC_PASTE_TASK)
        val id3 = taskDao.createTask(pasteDataId = 3L, taskType = TaskType.DELETE_PASTE_TASK)
        assert(id1 != id2)
        assert(id2 != id3)
    }

    // --- Task state transitions ---

    @Test
    fun `executingTask changes status to EXECUTING`() = runTest {
        val taskId = taskDao.createTask(pasteDataId = 1L, taskType = TaskType.SYNC_PASTE_TASK)
        taskDao.executingTask(taskId)
        val task = taskDao.getTask(taskId)
        assertNotNull(task)
        assertEquals(TaskStatus.EXECUTING, task.status)
    }

    @Test
    fun `successTask changes status to SUCCESS`() = runTest {
        val taskId = taskDao.createTask(pasteDataId = 1L, taskType = TaskType.SYNC_PASTE_TASK)
        taskDao.executingTask(taskId)
        taskDao.successTask(taskId, newExtraInfo = null)
        val task = taskDao.getTask(taskId)
        assertNotNull(task)
        assertEquals(TaskStatus.SUCCESS, task.status)
    }

    @Test
    fun `successTask with newExtraInfo updates extraInfo`() = runTest {
        val taskId = taskDao.createTask(pasteDataId = 1L, taskType = TaskType.SYNC_PASTE_TASK)
        taskDao.executingTask(taskId)
        val newExtraInfo = """{"type":"base","executionHistories":[]}"""
        taskDao.successTask(taskId, newExtraInfo = newExtraInfo)
        val task = taskDao.getTask(taskId)
        assertNotNull(task)
        assertEquals(TaskStatus.SUCCESS, task.status)
        assertEquals(newExtraInfo, task.extraInfo)
    }

    @Test
    fun `failureTask with needRetry resets status to PREPARING`() = runTest {
        val taskId = taskDao.createTask(pasteDataId = 1L, taskType = TaskType.SYNC_PASTE_TASK)
        taskDao.executingTask(taskId)
        taskDao.failureTask(taskId, needRetry = true, newExtraInfo = null)
        val task = taskDao.getTask(taskId)
        assertNotNull(task)
        assertEquals(TaskStatus.PREPARING, task.status)
    }

    @Test
    fun `failureTask without needRetry sets status to FAILURE`() = runTest {
        val taskId = taskDao.createTask(pasteDataId = 1L, taskType = TaskType.SYNC_PASTE_TASK)
        taskDao.executingTask(taskId)
        taskDao.failureTask(taskId, needRetry = false, newExtraInfo = null)
        val task = taskDao.getTask(taskId)
        assertNotNull(task)
        assertEquals(TaskStatus.FAILURE, task.status)
    }

    @Test
    fun `failureTask with newExtraInfo updates extraInfo`() = runTest {
        val taskId = taskDao.createTask(pasteDataId = 1L, taskType = TaskType.SYNC_PASTE_TASK)
        taskDao.executingTask(taskId)
        val newExtraInfo = """{"type":"sync","appInstanceId":"x","executionHistories":[],"syncFails":["a"]}"""
        taskDao.failureTask(taskId, needRetry = false, newExtraInfo = newExtraInfo)
        val task = taskDao.getTask(taskId)
        assertNotNull(task)
        assertEquals(newExtraInfo, task.extraInfo)
    }

    @Test
    fun `executingTask updates modifyTime`() = runTest {
        val taskId = taskDao.createTask(pasteDataId = 1L, taskType = TaskType.SYNC_PASTE_TASK)
        val taskBefore = taskDao.getTask(taskId)!!
        Thread.sleep(10)
        taskDao.executingTask(taskId)
        val taskAfter = taskDao.getTask(taskId)!!
        assert(taskAfter.modifyTime >= taskBefore.modifyTime)
    }

    // --- Task retrieval ---

    @Test
    fun `getTask returns null for non-existent id`() = runTest {
        val task = taskDao.getTask(99999L)
        assertNull(task)
    }

    @Test
    fun `getTask returns complete task data`() = runTest {
        val taskId = taskDao.createTask(
            pasteDataId = 42L,
            taskType = TaskType.PULL_ICON_TASK,
        )
        val task = taskDao.getTask(taskId)
        assertNotNull(task)
        assertEquals(taskId, task.taskId)
        assertEquals(42L, task.pasteDataId)
        assertEquals(TaskType.PULL_ICON_TASK, task.taskType)
        assertEquals(TaskStatus.PREPARING, task.status)
        assert(task.createTime > 0)
        assert(task.modifyTime > 0)
        assert(task.extraInfo.isNotEmpty())
    }

    // --- Task cleanup ---

    @Test
    fun `cleanSuccessTask removes old success tasks`() = runTest {
        val taskId = taskDao.createTask(pasteDataId = 1L, taskType = TaskType.SYNC_PASTE_TASK)
        taskDao.executingTask(taskId)
        taskDao.successTask(taskId, newExtraInfo = null)

        // Clean with a future time to remove all success tasks
        taskDao.cleanSuccessTask(System.currentTimeMillis() + 10000)
        val task = taskDao.getTask(taskId)
        assertNull(task)
    }

    @Test
    fun `cleanSuccessTask does not remove tasks newer than threshold`() = runTest {
        val taskId = taskDao.createTask(pasteDataId = 1L, taskType = TaskType.SYNC_PASTE_TASK)
        taskDao.executingTask(taskId)
        taskDao.successTask(taskId, newExtraInfo = null)

        // Clean with a past time - should not remove
        taskDao.cleanSuccessTask(1L)
        val task = taskDao.getTask(taskId)
        assertNotNull(task)
    }

    @Test
    fun `cleanFailureTask removes old failure tasks`() = runTest {
        val taskId = taskDao.createTask(pasteDataId = 1L, taskType = TaskType.SYNC_PASTE_TASK)
        taskDao.executingTask(taskId)
        taskDao.failureTask(taskId, needRetry = false, newExtraInfo = null)

        taskDao.cleanFailureTask(System.currentTimeMillis() + 10000)
        val task = taskDao.getTask(taskId)
        assertNull(task)
    }

    @Test
    fun `cleanFailureTask does not remove non-failure tasks`() = runTest {
        val taskId = taskDao.createTask(pasteDataId = 1L, taskType = TaskType.SYNC_PASTE_TASK)
        taskDao.executingTask(taskId)
        taskDao.successTask(taskId, newExtraInfo = null)

        // Cleaning failures should not affect success tasks
        taskDao.cleanFailureTask(System.currentTimeMillis() + 10000)
        val task = taskDao.getTask(taskId)
        assertNotNull(task)
    }

    // --- Full lifecycle ---

    @Test
    fun `complete task lifecycle PREPARING to EXECUTING to SUCCESS`() = runTest {
        val taskId = taskDao.createTask(pasteDataId = 1L, taskType = TaskType.SYNC_PASTE_TASK)
        assertEquals(TaskStatus.PREPARING, taskDao.getTask(taskId)!!.status)

        taskDao.executingTask(taskId)
        assertEquals(TaskStatus.EXECUTING, taskDao.getTask(taskId)!!.status)

        taskDao.successTask(taskId, newExtraInfo = null)
        assertEquals(TaskStatus.SUCCESS, taskDao.getTask(taskId)!!.status)
    }

    @Test
    fun `task lifecycle with retry PREPARING to EXECUTING to PREPARING to EXECUTING to FAILURE`() = runTest {
        val taskId = taskDao.createTask(pasteDataId = 1L, taskType = TaskType.SYNC_PASTE_TASK)

        taskDao.executingTask(taskId)
        taskDao.failureTask(taskId, needRetry = true, newExtraInfo = null)
        assertEquals(TaskStatus.PREPARING, taskDao.getTask(taskId)!!.status)

        taskDao.executingTask(taskId)
        taskDao.failureTask(taskId, needRetry = false, newExtraInfo = null)
        assertEquals(TaskStatus.FAILURE, taskDao.getTask(taskId)!!.status)
    }
}
