package com.crosspaste.task

import com.crosspaste.clean.CleanTime
import com.crosspaste.config.AppConfig
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.db.task.BaseExtraInfo
import com.crosspaste.db.task.PasteTask
import com.crosspaste.db.task.TaskType
import com.crosspaste.utils.getJsonUtils
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class CleanPasteTaskExecutorTest {

    private val jsonUtils = getJsonUtils()

    private class TestDeps {
        val pasteDao: PasteDao = mockk(relaxed = true)
        val configManager: CommonConfigManager = mockk(relaxed = true)

        init {
            val config = mockk<AppConfig>(relaxed = true)
            every { config.enableExpirationCleanup } returns true
            every { config.enableThresholdCleanup } returns true
            every { config.imageCleanTimeIndex } returns CleanTime.ONE_WEEK.ordinal
            every { config.fileCleanTimeIndex } returns CleanTime.ONE_MONTH.ordinal
            every { config.maxStorage } returns 1024L // 1024 MB
            every { config.cleanupPercentage } returns 20
            every { configManager.getCurrentConfig() } returns config
        }

        fun createExecutor(): CleanPasteTaskExecutor = CleanPasteTaskExecutor(pasteDao, configManager)
    }

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
            taskType = TaskType.CLEAN_PASTE_TASK,
            createTime = System.currentTimeMillis(),
            modifyTime = System.currentTimeMillis(),
            extraInfo = extraInfoJson,
        )
    }

    @Test
    fun `taskType is CLEAN_PASTE_TASK`() {
        val deps = TestDeps()
        val executor = deps.createExecutor()
        assertTrue(executor.taskType == TaskType.CLEAN_PASTE_TASK)
    }

    @Test
    fun `both cleanup disabled skips all cleanup`() =
        runTest {
            val deps = TestDeps()
            val config = mockk<AppConfig>(relaxed = true)
            every { config.enableExpirationCleanup } returns false
            every { config.enableThresholdCleanup } returns false
            every { deps.configManager.getCurrentConfig() } returns config

            val executor = deps.createExecutor()
            val task = createPasteTask()

            val result = executor.doExecuteTask(task)

            assertTrue(result is SuccessPasteTaskResult)
            coVerify(exactly = 0) { deps.pasteDao.markDeleteByCleanTime(any(), any()) }
        }

    @Test
    fun `expiration only runs time-based cleanup but not threshold cleanup`() =
        runTest {
            val deps = TestDeps()
            val config = mockk<AppConfig>(relaxed = true)
            every { config.enableExpirationCleanup } returns true
            every { config.enableThresholdCleanup } returns false
            every { config.imageCleanTimeIndex } returns CleanTime.ONE_WEEK.ordinal
            every { config.fileCleanTimeIndex } returns CleanTime.ONE_MONTH.ordinal
            every { deps.configManager.getCurrentConfig() } returns config

            val executor = deps.createExecutor()
            val task = createPasteTask()

            val result = executor.doExecuteTask(task)

            assertTrue(result is SuccessPasteTaskResult)
            // Time-based cleanup for image and file
            coVerify(exactly = 2) { deps.pasteDao.markDeleteByCleanTime(any(), any()) }
            // No size-based cleanup
            coVerify(exactly = 0) { deps.pasteDao.getSize(any()) }
        }

    @Test
    fun `threshold only runs size-based cleanup but not time-based cleanup`() =
        runTest {
            val deps = TestDeps()
            val config = mockk<AppConfig>(relaxed = true)
            every { config.enableExpirationCleanup } returns false
            every { config.enableThresholdCleanup } returns true
            every { config.maxStorage } returns 1024L
            every { config.cleanupPercentage } returns 20
            every { deps.configManager.getCurrentConfig() } returns config

            // Under threshold so no actual deletion
            coEvery { deps.pasteDao.getSize(true) } returns 100L
            coEvery { deps.pasteDao.getSize(false) } returns 50L

            val executor = deps.createExecutor()
            val task = createPasteTask()

            val result = executor.doExecuteTask(task)

            assertTrue(result is SuccessPasteTaskResult)
            // No time-based cleanup (markDeleteByCleanTime with type param)
            coVerify(exactly = 0) { deps.pasteDao.markDeleteByCleanTime(any(), any()) }
            // Size check was performed
            coVerify(exactly = 1) { deps.pasteDao.getSize(true) }
        }

    @Test
    fun `threshold enabled calls markDeleteByCleanTime for image and file`() =
        runTest {
            val deps = TestDeps()
            // Set storage under threshold so no size-based cleanup
            coEvery { deps.pasteDao.getSize(true) } returns 100L
            coEvery { deps.pasteDao.getSize(false) } returns 50L

            val executor = deps.createExecutor()
            val task = createPasteTask()

            val result = executor.doExecuteTask(task)

            assertTrue(result is SuccessPasteTaskResult)
            // Should call markDeleteByCleanTime twice: once for IMAGE, once for FILE
            coVerify(exactly = 2) { deps.pasteDao.markDeleteByCleanTime(any(), any()) }
        }

    @Test
    fun `size-based cleanup triggered when over threshold`() =
        runTest {
            val deps = TestDeps()
            // maxStorage = 1024 MB → threshold = 1024 * 1024 * 1024 bytes
            // allSize > maxStorage * 1024 * 1024
            val maxStorageBytes = 1024L * 1024 * 1024
            coEvery { deps.pasteDao.getSize(true) } returns maxStorageBytes + 1000
            coEvery { deps.pasteDao.getSize(false) } returns 0L
            coEvery { deps.pasteDao.getMinPasteDataCreateTime() } returns System.currentTimeMillis() - 100000
            coEvery { deps.pasteDao.getSizeByTimeLessThan(any()) } returns maxStorageBytes

            val executor = deps.createExecutor()
            val task = createPasteTask()

            val result = executor.doExecuteTask(task)

            assertTrue(result is SuccessPasteTaskResult)
            // Should call markDeleteByCleanTime for size-based cleanup (no type param)
            coVerify(atLeast = 1) { deps.pasteDao.markDeleteByCleanTime(any()) }
        }

    @Test
    fun `exception during time-based cleanup returns failure with retry`() =
        runTest {
            val deps = TestDeps()
            coEvery { deps.pasteDao.markDeleteByCleanTime(any(), any()) } throws RuntimeException("DB error")

            val executor = deps.createExecutor()
            val task = createPasteTask()

            val result = executor.doExecuteTask(task)

            assertTrue(result is FailurePasteTaskResult)
            val failResult = result as FailurePasteTaskResult
            // First execution: empty history → retries allowed
            assertTrue(failResult.needRetry)
        }

    @Test
    fun `no data to clean returns success`() =
        runTest {
            val deps = TestDeps()
            // Under threshold
            coEvery { deps.pasteDao.getSize(true) } returns 100L
            coEvery { deps.pasteDao.getSize(false) } returns 50L

            val executor = deps.createExecutor()
            val task = createPasteTask()

            val result = executor.doExecuteTask(task)

            assertTrue(result is SuccessPasteTaskResult)
        }
}
