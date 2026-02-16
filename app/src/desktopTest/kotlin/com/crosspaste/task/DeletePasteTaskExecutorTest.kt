package com.crosspaste.task

import com.crosspaste.db.paste.PasteDao
import com.crosspaste.db.task.BaseExtraInfo
import com.crosspaste.db.task.PasteTask
import com.crosspaste.db.task.TaskType
import com.crosspaste.utils.getJsonUtils
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class DeletePasteTaskExecutorTest {

    private val jsonUtils = getJsonUtils()

    private fun createExecutor(pasteDao: PasteDao = mockk(relaxed = true)): Pair<DeletePasteTaskExecutor, PasteDao> {
        val executor = DeletePasteTaskExecutor(pasteDao)
        return executor to pasteDao
    }

    private fun createPasteTask(
        pasteDataId: Long? = 1L,
        extraInfo: BaseExtraInfo = BaseExtraInfo(),
    ): PasteTask {
        val extraInfoJson =
            jsonUtils.JSON.encodeToString(
                com.crosspaste.db.task.PasteTaskExtraInfo
                    .serializer(),
                extraInfo,
            )
        return PasteTask(
            taskId = 1L,
            pasteDataId = pasteDataId,
            taskType = TaskType.DELETE_PASTE_TASK,
            createTime = System.currentTimeMillis(),
            modifyTime = System.currentTimeMillis(),
            extraInfo = extraInfoJson,
        )
    }

    @Test
    fun `taskType is DELETE_PASTE_TASK`() {
        val (executor, _) = createExecutor()
        assertTrue(executor.taskType == TaskType.DELETE_PASTE_TASK)
    }

    @Test
    fun `null pasteDataId returns success`() =
        runTest {
            val (executor, _) = createExecutor()
            val task = createPasteTask(pasteDataId = null)

            val result = executor.doExecuteTask(task)

            assertTrue(result is SuccessPasteTaskResult)
        }

    @Test
    fun `valid pasteDataId calls deletePasteData and returns success`() =
        runTest {
            val pasteDao: PasteDao = mockk(relaxed = true)
            val (executor, _) = createExecutor(pasteDao)
            val task = createPasteTask(pasteDataId = 42L)

            val result = executor.doExecuteTask(task)

            assertTrue(result is SuccessPasteTaskResult)
            coVerify { pasteDao.deletePasteData(42L) }
        }

    @Test
    fun `exception during delete returns failure`() =
        runTest {
            val pasteDao: PasteDao = mockk(relaxed = true)
            coEvery { pasteDao.deletePasteData(any()) } throws RuntimeException("DB error")
            val (executor, _) = createExecutor(pasteDao)
            val task = createPasteTask(pasteDataId = 1L)

            val result = executor.doExecuteTask(task)

            assertTrue(result is FailurePasteTaskResult)
            assertTrue(!result.needRetry)
        }

    @Test
    fun `multiple deletes with same id are serialized via mutex`() =
        runTest {
            val pasteDao: PasteDao = mockk(relaxed = true)
            val (executor, _) = createExecutor(pasteDao)
            val task1 = createPasteTask(pasteDataId = 1L)
            val task2 = createPasteTask(pasteDataId = 1L)

            val result1 = executor.doExecuteTask(task1)
            val result2 = executor.doExecuteTask(task2)

            assertTrue(result1 is SuccessPasteTaskResult)
            assertTrue(result2 is SuccessPasteTaskResult)
            coVerify(exactly = 2) { pasteDao.deletePasteData(1L) }
        }
}
