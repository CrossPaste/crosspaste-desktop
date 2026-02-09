package com.crosspaste.task

import com.crosspaste.app.AppControl
import com.crosspaste.app.AppInfo
import com.crosspaste.config.AppConfig
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.db.task.PasteTask
import com.crosspaste.db.task.SyncExtraInfo
import com.crosspaste.db.task.TaskType
import com.crosspaste.exception.PasteException
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.VersionRelation
import com.crosspaste.net.clientapi.FailureResult
import com.crosspaste.net.clientapi.PasteClientApi
import com.crosspaste.net.clientapi.SuccessResult
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteType
import com.crosspaste.sync.SyncHandler
import com.crosspaste.sync.SyncManager
import com.crosspaste.sync.SyncTestFixtures.createConnectedSyncRuntimeInfo
import com.crosspaste.utils.getJsonUtils
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class SyncPasteTaskExecutorTest {

    private val jsonUtils = getJsonUtils()

    private class TestDeps {
        val appControl: AppControl = mockk(relaxed = true)
        val appInfo: AppInfo =
            AppInfo(
                appInstanceId = "local-app-1",
                appVersion = "1.0.0",
                appRevision = "abc",
                userName = "testUser",
            )
        val configManager: CommonConfigManager = mockk(relaxed = true)
        val pasteDao: PasteDao = mockk(relaxed = true)
        val pasteClientApi: PasteClientApi = mockk(relaxed = true)
        val syncManager: SyncManager = mockk(relaxed = true)

        init {
            val config = mockk<AppConfig>(relaxed = true)
            every { config.enableSyncText } returns true
            every { config.enableSyncUrl } returns true
            every { config.enableSyncHtml } returns true
            every { config.enableSyncRtf } returns true
            every { config.enableSyncImage } returns true
            every { config.enableSyncFile } returns true
            every { config.enableSyncColor } returns true
            every { configManager.config } returns MutableStateFlow(config)
            every { configManager.getCurrentConfig() } returns config
            coEvery { appControl.isSendEnabled() } returns true
        }

        fun createExecutor(): SyncPasteTaskExecutor =
            SyncPasteTaskExecutor(
                appControl = appControl,
                appInfo = appInfo,
                configManager = configManager,
                pasteDao = pasteDao,
                pasteClientApi = pasteClientApi,
                syncManager = syncManager,
            )
    }

    private fun createPasteTask(
        pasteDataId: Long? = 1L,
        extraInfo: SyncExtraInfo = SyncExtraInfo(appInstanceId = "local-app-1"),
    ): PasteTask {
        val extraInfoJson = jsonUtils.JSON.encodeToString(extraInfo as com.crosspaste.db.task.PasteTaskExtraInfo)
        return PasteTask(
            taskId = 1L,
            pasteDataId = pasteDataId,
            taskType = TaskType.SYNC_PASTE_TASK,
            createTime = System.currentTimeMillis(),
            modifyTime = System.currentTimeMillis(),
            extraInfo = extraInfoJson,
        )
    }

    private fun createMockPasteData(pasteType: PasteType = PasteType.TEXT_TYPE): PasteData {
        val pasteData = mockk<PasteData>(relaxed = true)
        every { pasteData.getType() } returns pasteType
        return pasteData
    }

    private fun createMockSyncHandler(
        appInstanceId: String = "remote-app-1",
        allowSend: Boolean = true,
        versionRelation: VersionRelation = VersionRelation.EQUAL_TO,
        connectHostAddress: String? = "192.168.1.100",
    ): SyncHandler {
        val handler = mockk<SyncHandler>(relaxed = true)
        val syncRuntimeInfo =
            createConnectedSyncRuntimeInfo(
                appInstanceId = appInstanceId,
                hostAddress = connectHostAddress ?: "192.168.1.100",
            ).copy(allowSend = allowSend)
        every { handler.currentSyncRuntimeInfo } returns syncRuntimeInfo
        every { handler.currentVersionRelation } returns versionRelation
        coEvery { handler.getConnectHostAddress() } returns connectHostAddress
        return handler
    }

    // ========== A. Early exits ==========

    @Test
    fun doExecuteTask_nullPasteDataId_returnsSuccess() =
        runTest {
            val deps = TestDeps()
            val executor = deps.createExecutor()
            val task = createPasteTask(pasteDataId = null)

            val result = executor.doExecuteTask(task)

            assertTrue(result is SuccessPasteTaskResult)
        }

    @Test
    fun doExecuteTask_pasteNotFound_returnsSuccess() =
        runTest {
            val deps = TestDeps()
            val executor = deps.createExecutor()
            val task = createPasteTask()

            coEvery { deps.pasteDao.getNoDeletePasteData(any()) } returns null

            val result = executor.doExecuteTask(task)

            assertTrue(result is SuccessPasteTaskResult)
        }

    @Test
    fun doExecuteTask_syncDisabledForTextType_returnsSuccess() =
        runTest {
            val deps = TestDeps()

            val config = mockk<AppConfig>(relaxed = true)
            every { config.enableSyncText } returns false
            every { deps.configManager.config } returns MutableStateFlow(config)
            every { deps.configManager.getCurrentConfig() } returns config

            val executor = deps.createExecutor()
            val task = createPasteTask()
            val pasteData = createMockPasteData(PasteType.TEXT_TYPE)

            coEvery { deps.pasteDao.getNoDeletePasteData(any()) } returns pasteData

            val result = executor.doExecuteTask(task)

            assertTrue(result is SuccessPasteTaskResult)
        }

    @Test
    fun doExecuteTask_noEligibleHandlers_returnsSuccess() =
        runTest {
            val deps = TestDeps()
            val executor = deps.createExecutor()
            val task = createPasteTask()
            val pasteData = createMockPasteData()

            coEvery { deps.pasteDao.getNoDeletePasteData(any()) } returns pasteData
            coEvery { deps.syncManager.getSyncHandlers() } returns emptyMap()

            val result = executor.doExecuteTask(task)

            assertTrue(result is SuccessPasteTaskResult)
        }

    // ========== B. Handler eligibility ==========

    @Test
    fun doExecuteTask_localPaste_filtersHandlerByAllowSend() =
        runTest {
            val deps = TestDeps()
            val executor = deps.createExecutor()
            val task = createPasteTask()
            val pasteData = createMockPasteData()

            val handlerAllowed = createMockSyncHandler("remote-1", allowSend = true)
            val handlerNotAllowed = createMockSyncHandler("remote-2", allowSend = false)

            coEvery { deps.pasteDao.getNoDeletePasteData(any()) } returns pasteData
            coEvery { deps.syncManager.getSyncHandlers() } returns
                mapOf("remote-1" to handlerAllowed, "remote-2" to handlerNotAllowed)
            coEvery { deps.pasteClientApi.sendPaste(any(), any(), any()) } returns SuccessResult()

            val result = executor.doExecuteTask(task)

            assertTrue(result is SuccessPasteTaskResult)
            coVerify(exactly = 1) { deps.pasteClientApi.sendPaste(any(), eq("remote-1"), any()) }
        }

    @Test
    fun doExecuteTask_localPaste_filtersHandlerByVersionRelation() =
        runTest {
            val deps = TestDeps()
            val executor = deps.createExecutor()
            val task = createPasteTask()
            val pasteData = createMockPasteData()

            val handlerEqual = createMockSyncHandler("remote-1", versionRelation = VersionRelation.EQUAL_TO)
            val handlerIncompat = createMockSyncHandler("remote-2", versionRelation = VersionRelation.LOWER_THAN)

            coEvery { deps.pasteDao.getNoDeletePasteData(any()) } returns pasteData
            coEvery { deps.syncManager.getSyncHandlers() } returns
                mapOf("remote-1" to handlerEqual, "remote-2" to handlerIncompat)
            coEvery { deps.pasteClientApi.sendPaste(any(), any(), any()) } returns SuccessResult()

            val result = executor.doExecuteTask(task)

            assertTrue(result is SuccessPasteTaskResult)
            coVerify(exactly = 1) { deps.pasteClientApi.sendPaste(any(), eq("remote-1"), any()) }
        }

    // ========== C. Sync execution ==========

    @Test
    fun doExecuteTask_allSucceed_returnsSuccess() =
        runTest {
            val deps = TestDeps()
            val executor = deps.createExecutor()
            val task = createPasteTask()
            val pasteData = createMockPasteData()

            val handler = createMockSyncHandler("remote-1")

            coEvery { deps.pasteDao.getNoDeletePasteData(any()) } returns pasteData
            coEvery { deps.syncManager.getSyncHandlers() } returns mapOf("remote-1" to handler)
            coEvery { deps.pasteClientApi.sendPaste(any(), any(), any()) } returns SuccessResult()

            val result = executor.doExecuteTask(task)

            assertTrue(result is SuccessPasteTaskResult)
            coVerify { deps.appControl.completeSendOperation() }
        }

    @Test
    fun doExecuteTask_someFail_returnsFailure() =
        runTest {
            val deps = TestDeps()
            val executor = deps.createExecutor()
            val task = createPasteTask()
            val pasteData = createMockPasteData()

            val handler = createMockSyncHandler("remote-1")

            coEvery { deps.pasteDao.getNoDeletePasteData(any()) } returns pasteData
            coEvery { deps.syncManager.getSyncHandlers() } returns mapOf("remote-1" to handler)
            coEvery { deps.pasteClientApi.sendPaste(any(), any(), any()) } returns
                FailureResult(PasteException(StandardErrorCode.UNKNOWN_ERROR.toErrorCode(), "test error"))

            val result = executor.doExecuteTask(task)

            assertTrue(result is FailurePasteTaskResult)
        }

    @Test
    fun doExecuteTask_sendDisabledByApp_returnsFailure() =
        runTest {
            val deps = TestDeps()
            coEvery { deps.appControl.isSendEnabled() } returns false

            val executor = deps.createExecutor()
            val task = createPasteTask()
            val pasteData = createMockPasteData()

            val handler = createMockSyncHandler("remote-1")

            coEvery { deps.pasteDao.getNoDeletePasteData(any()) } returns pasteData
            coEvery { deps.syncManager.getSyncHandlers() } returns mapOf("remote-1" to handler)

            val result = executor.doExecuteTask(task)

            assertTrue(result is FailurePasteTaskResult)
        }

    @Test
    fun doExecuteTask_noConnectHostAddress_returnsFailure() =
        runTest {
            val deps = TestDeps()
            val executor = deps.createExecutor()
            val task = createPasteTask()
            val pasteData = createMockPasteData()

            val handler = createMockSyncHandler("remote-1", connectHostAddress = null)

            coEvery { deps.pasteDao.getNoDeletePasteData(any()) } returns pasteData
            coEvery { deps.syncManager.getSyncHandlers() } returns mapOf("remote-1" to handler)

            val result = executor.doExecuteTask(task)

            assertTrue(result is FailurePasteTaskResult)
        }

    // ========== D. Retry logic ==========

    @Test
    fun doExecuteTask_nonRetriableError_noRetry() =
        runTest {
            val deps = TestDeps()
            val executor = deps.createExecutor()
            val task = createPasteTask()
            val pasteData = createMockPasteData()

            val handler = createMockSyncHandler("remote-1")

            coEvery { deps.pasteDao.getNoDeletePasteData(any()) } returns pasteData
            coEvery { deps.syncManager.getSyncHandlers() } returns mapOf("remote-1" to handler)
            coEvery { deps.pasteClientApi.sendPaste(any(), any(), any()) } returns
                FailureResult(
                    PasteException(
                        StandardErrorCode.SYNC_NOT_ALLOW_RECEIVE_BY_APP.toErrorCode(),
                        "not allowed",
                    ),
                )

            val result = executor.doExecuteTask(task)

            val failResult = result as FailurePasteTaskResult
            assertTrue(!failResult.needRetry)
        }

    @Test
    fun doExecuteTask_retriableError_retryAllowed() =
        runTest {
            val deps = TestDeps()
            val executor = deps.createExecutor()
            val task = createPasteTask()
            val pasteData = createMockPasteData()

            val handler = createMockSyncHandler("remote-1")

            coEvery { deps.pasteDao.getNoDeletePasteData(any()) } returns pasteData
            coEvery { deps.syncManager.getSyncHandlers() } returns mapOf("remote-1" to handler)
            coEvery { deps.pasteClientApi.sendPaste(any(), any(), any()) } returns
                FailureResult(
                    PasteException(
                        StandardErrorCode.UNKNOWN_ERROR.toErrorCode(),
                        "transient error",
                    ),
                )

            val result = executor.doExecuteTask(task)

            val failResult = result as FailurePasteTaskResult
            assertTrue(failResult.needRetry)
        }

    @Test
    fun doExecuteTask_syncDisabledForImageType_returnsSuccess() =
        runTest {
            val deps = TestDeps()

            val config = mockk<AppConfig>(relaxed = true)
            every { config.enableSyncImage } returns false
            every { deps.configManager.config } returns MutableStateFlow(config)
            every { deps.configManager.getCurrentConfig() } returns config

            val executor = deps.createExecutor()
            val task = createPasteTask()
            val pasteData = createMockPasteData(PasteType.IMAGE_TYPE)

            coEvery { deps.pasteDao.getNoDeletePasteData(any()) } returns pasteData

            val result = executor.doExecuteTask(task)

            assertTrue(result is SuccessPasteTaskResult)
        }
}
