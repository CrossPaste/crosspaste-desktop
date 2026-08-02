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
import com.crosspaste.net.ws.WS_MAX_FRAME_SIZE
import com.crosspaste.net.ws.WS_MAX_PAYLOAD_SIZE
import com.crosspaste.net.ws.WsSessionManager
import com.crosspaste.paste.PasteCollection
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteState
import com.crosspaste.paste.PasteType
import com.crosspaste.paste.item.CreatePasteItemHelper.createTextPasteItem
import com.crosspaste.platform.Platform
import com.crosspaste.secure.SecureMessageProcessor
import com.crosspaste.secure.SecureStore
import com.crosspaste.sync.SyncHandler
import com.crosspaste.sync.SyncManager
import com.crosspaste.sync.SyncTestFixtures
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
        val secureStore: SecureStore = mockk(relaxed = true)
        val syncManager: SyncManager = mockk(relaxed = true)
        val wsSessionManager: WsSessionManager =
            mockk(relaxed = true) {
                every { isConnected(any()) } returns false
            }

        val config = mockk<AppConfig>(relaxed = true)

        init {
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

        fun createExecutor(
            wsSingleFramePayloadLimit: Long = WS_MAX_FRAME_SIZE,
            wsChunkedPayloadLimit: Long = WS_MAX_PAYLOAD_SIZE,
        ): SyncPasteTaskExecutor =
            SyncPasteTaskExecutor(
                appControl = appControl,
                appInfo = appInfo,
                configManager = configManager,
                pasteDao = pasteDao,
                pasteClientApi = pasteClientApi,
                secureStore = secureStore,
                syncManager = syncManager,
                wsSessionManager = wsSessionManager,
                wsSingleFramePayloadLimit = wsSingleFramePayloadLimit,
                wsChunkedPayloadLimit = wsChunkedPayloadLimit,
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

    private fun createRealTextPasteData(text: String): PasteData {
        val textItem = createTextPasteItem(identifiers = listOf("text"), text = text)
        return PasteData(
            appInstanceId = "local-app-1",
            pasteAppearItem = textItem,
            pasteCollection = PasteCollection(listOf()),
            pasteType = PasteType.TEXT_TYPE.type,
            source = null,
            size = textItem.size,
            hash = textItem.hash,
            pasteState = PasteState.LOADED,
            createTime = 0L,
        )
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

    // ========== E. WebSocket fallback ==========

    @Test
    fun doExecuteTask_wsPayloadWithinLimit_sendsViaWebSocket() =
        runTest {
            val deps = TestDeps()
            val executor = deps.createExecutor()
            val task = createPasteTask()
            val pasteData = createRealTextPasteData("hello ws")

            val handler = createMockSyncHandler("remote-1", connectHostAddress = null)

            coEvery { deps.pasteDao.getNoDeletePasteData(any()) } returns pasteData
            coEvery { deps.syncManager.getSyncHandlers() } returns mapOf("remote-1" to handler)
            coEvery { deps.wsSessionManager.send(any(), any()) } returns true

            val result = executor.doExecuteTask(task)

            assertTrue(result is SuccessPasteTaskResult)
            coVerify(exactly = 1) { deps.wsSessionManager.send("remote-1", any()) }
        }

    @Test
    fun doExecuteTask_legacyPeerPayloadExceedsSingleFrameLimit_failsWithoutSending() =
        runTest {
            val deps = TestDeps()
            val executor = deps.createExecutor(wsSingleFramePayloadLimit = 64)
            val task = createPasteTask()
            val pasteData = createRealTextPasteData("x".repeat(1024))

            val handler = createMockSyncHandler("remote-1", connectHostAddress = null)

            every { deps.wsSessionManager.supportsChunkedPayload("remote-1") } returns false
            coEvery { deps.pasteDao.getNoDeletePasteData(any()) } returns pasteData
            coEvery { deps.syncManager.getSyncHandlers() } returns mapOf("remote-1" to handler)

            val result = executor.doExecuteTask(task)

            assertTrue(result is FailurePasteTaskResult)
            coVerify(exactly = 0) { deps.wsSessionManager.send(any(), any()) }
        }

    @Test
    fun doExecuteTask_chunkCapablePeer_sendsPayloadAboveSingleFrameLimit() =
        runTest {
            val deps = TestDeps()
            val executor = deps.createExecutor(wsSingleFramePayloadLimit = 64)
            val task = createPasteTask()
            val pasteData = createRealTextPasteData("x".repeat(1024))

            val handler = createMockSyncHandler("remote-1", connectHostAddress = null)

            every { deps.wsSessionManager.supportsChunkedPayload("remote-1") } returns true
            coEvery { deps.pasteDao.getNoDeletePasteData(any()) } returns pasteData
            coEvery { deps.syncManager.getSyncHandlers() } returns mapOf("remote-1" to handler)
            coEvery { deps.wsSessionManager.send(any(), any()) } returns true

            val result = executor.doExecuteTask(task)

            assertTrue(result is SuccessPasteTaskResult)
            coVerify(exactly = 1) { deps.wsSessionManager.send("remote-1", any()) }
        }

    @Test
    fun doExecuteTask_chunkCapablePeerPayloadExceedsMessageLimit_failsWithoutSending() =
        runTest {
            val deps = TestDeps()
            val executor =
                deps.createExecutor(wsSingleFramePayloadLimit = 64, wsChunkedPayloadLimit = 256)
            val task = createPasteTask()
            val pasteData = createRealTextPasteData("x".repeat(1024))

            val handler = createMockSyncHandler("remote-1", connectHostAddress = null)

            every { deps.wsSessionManager.supportsChunkedPayload("remote-1") } returns true
            coEvery { deps.pasteDao.getNoDeletePasteData(any()) } returns pasteData
            coEvery { deps.syncManager.getSyncHandlers() } returns mapOf("remote-1" to handler)

            val result = executor.doExecuteTask(task)

            assertTrue(result is FailurePasteTaskResult)
            coVerify(exactly = 0) { deps.wsSessionManager.send(any(), any()) }
        }

    @Test
    fun doExecuteTask_extensionTarget_toleratesPayloadAboveSingleFrameLimit() =
        runTest {
            val deps = TestDeps()
            val executor = deps.createExecutor(wsSingleFramePayloadLimit = 64)
            val task = createPasteTask()
            val pasteData = createRealTextPasteData("x".repeat(1024))

            val handler = createMockSyncHandler("remote-1", connectHostAddress = null)
            val extensionInfo =
                handler.currentSyncRuntimeInfo.copy(
                    platform = SyncTestFixtures.TEST_PLATFORM.copy(name = Platform.CHROME_EXTENSION),
                )
            every { handler.currentSyncRuntimeInfo } returns extensionInfo

            every { deps.wsSessionManager.supportsChunkedPayload("remote-1") } returns false
            coEvery { deps.pasteDao.getNoDeletePasteData(any()) } returns pasteData
            coEvery { deps.syncManager.getSyncHandlers() } returns mapOf("remote-1" to handler)
            coEvery { deps.wsSessionManager.send(any(), any()) } returns true

            val result = executor.doExecuteTask(task)

            assertTrue(result is SuccessPasteTaskResult)
            coVerify(exactly = 1) { deps.wsSessionManager.send("remote-1", any()) }
        }

    @Test
    fun doExecuteTask_encryptedPayloadExceedsSingleFrameLimit_failsWithoutSending() =
        runTest {
            val deps = TestDeps()
            val executor = deps.createExecutor(wsSingleFramePayloadLimit = 1024)
            val task = createPasteTask()
            // Plaintext JSON stays under the limit; only the encrypted payload exceeds it
            val pasteData = createRealTextPasteData("short text")

            val handler = createMockSyncHandler("remote-1", connectHostAddress = null)
            val processor = mockk<SecureMessageProcessor>()
            every { processor.encrypt(any()) } returns ByteArray(2048)

            every { deps.config.enableEncryptSync } returns true
            coEvery { deps.secureStore.getMessageProcessor("remote-1") } returns processor
            coEvery { deps.pasteDao.getNoDeletePasteData(any()) } returns pasteData
            coEvery { deps.syncManager.getSyncHandlers() } returns mapOf("remote-1" to handler)

            val result = executor.doExecuteTask(task)

            assertTrue(result is FailurePasteTaskResult)
            coVerify(exactly = 0) { deps.wsSessionManager.send(any(), any()) }
        }
}
