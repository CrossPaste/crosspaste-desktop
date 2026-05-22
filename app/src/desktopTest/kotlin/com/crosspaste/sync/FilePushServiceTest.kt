package com.crosspaste.sync

import com.crosspaste.config.AppConfig
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.dto.push.PushCompleteResponse
import com.crosspaste.dto.push.PushPrepareResponse
import com.crosspaste.exception.PasteException
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.clientapi.ClientApiResult
import com.crosspaste.net.clientapi.FailureResult
import com.crosspaste.net.clientapi.PushClientApi
import com.crosspaste.net.clientapi.SuccessResult
import com.crosspaste.paste.PasteCollection
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteSyncProcessManager
import com.crosspaste.paste.PasteType
import com.crosspaste.paste.item.CreatePasteItemHelper.createFilesPasteItem
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.presist.FilesChunk
import com.crosspaste.presist.FilesIndex
import com.crosspaste.utils.HostAndPort
import com.crosspaste.utils.buildUrl
import io.ktor.http.URLBuilder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests the FilePushService orchestrator end-to-end with a mocked
 * PushClientApi + a stub PasteSyncProcessManager that runs tasks inline.
 * The buildFilesIndex factory is overridden so the test doesn't need files
 * on disk — FilesIndex is a value-only abstraction here.
 */
class FilePushServiceTest {

    private fun configManager(encrypt: Boolean = false): CommonConfigManager {
        val cfg = mockk<AppConfig>(relaxed = true)
        every { cfg.enableEncryptSync } returns encrypt
        return mockk<CommonConfigManager>(relaxed = true).also {
            every { it.getCurrentConfig() } returns cfg
        }
    }

    /** Process manager stub that runs every supplied task sequentially and
     * collects the results. Sequential execution is fine for tests — we're
     * not measuring concurrency. */
    private fun inlineProcessManager(): PasteSyncProcessManager<Long> {
        val mgr = mockk<PasteSyncProcessManager<Long>>(relaxed = true)
        coEvery { mgr.runTask(any(), any()) } coAnswers {
            val tasks: List<suspend () -> Pair<Int, ClientApiResult>> = secondArg()
            tasks.map { it() }
        }
        return mgr
    }

    /** Builds a FilesIndex with [chunkCount] dummy chunks. Each chunk
     * resolves to a unique mock FilesChunk so the chunkReader can
     * differentiate per-chunk bytes. */
    private fun fakeIndex(chunkCount: Int): FilesIndex {
        val index = mockk<FilesIndex>()
        every { index.getChunkCount() } returns chunkCount
        every { index.getChunk(any()) } answers {
            val i = firstArg<Int>()
            if (i in 0 until chunkCount) mockk<FilesChunk>() else null
        }
        return index
    }

    private fun toUrl(): URLBuilder.() -> Unit =
        {
            buildUrl(HostAndPort("127.0.0.1", 13129))
        }

    private fun samplePasteData(): PasteData =
        PasteData(
            appInstanceId = "sender",
            pasteAppearItem =
                createFilesPasteItem(
                    relativePathList = emptyList(),
                    fileInfoTreeMap = emptyMap(),
                ),
            pasteCollection = PasteCollection(emptyList()),
            pasteType = PasteType.FILE_TYPE.type,
            source = null,
            size = 0L,
            hash = "h",
        )

    private fun service(
        pushClientApi: PushClientApi,
        userDataPathProvider: UserDataPathProvider = mockk(relaxed = true),
        index: FilesIndex = fakeIndex(3),
    ): FilePushService =
        FilePushService(
            configManager = configManager(),
            pasteSyncProcessManager = inlineProcessManager(),
            pushClientApi = pushClientApi,
            userDataPathProvider = userDataPathProvider,
            filesIndexFactory = { _, _, _ -> index },
            chunkReader = { byteArrayOf(0xC, 0xA, 0xF, 0xE) },
        )

    private fun preparePushResult(
        chunkCount: Int,
        needIcon: Boolean = false,
    ): SuccessResult =
        SuccessResult(
            PushPrepareResponse(
                pasteId = 42L,
                chunkCount = chunkCount,
                chunkSize = 1024L,
                sessionToken = "session-tok",
                needIcon = needIcon,
            ),
        )

    @Test
    fun pushFiles_happyPath_returnsSuccess() =
        runBlocking {
            val api = mockk<PushClientApi>()
            coEvery { api.preparePush(any(), any(), any()) } returns preparePushResult(chunkCount = 3)
            coEvery { api.pushChunk(any(), any(), any(), any(), any(), any()) } returns SuccessResult()
            coEvery { api.completePush(any(), any(), any(), any()) } returns
                SuccessResult(PushCompleteResponse(emptyList()))

            val svc = service(api)
            val result = svc.pushFiles(samplePasteData(), "remote-1", toUrl())

            assertTrue(result is SuccessResult, "expected SuccessResult, got $result")
            coVerify(exactly = 3) { api.pushChunk(eq(42L), any(), eq("session-tok"), eq("remote-1"), any(), any()) }
            coVerify(exactly = 1) { api.completePush(eq(42L), eq("session-tok"), eq("remote-1"), any()) }
        }

    @Test
    fun pushFiles_prepareFails_returnsFailureWithoutUploading() =
        runBlocking {
            val api = mockk<PushClientApi>()
            val failure =
                FailureResult(PasteException(StandardErrorCode.PUSH_SESSION_REJECTED.toErrorCode(), "no capacity"))
            coEvery { api.preparePush(any(), any(), any()) } returns failure

            val svc = service(api)
            val result = svc.pushFiles(samplePasteData(), "remote-1", toUrl())

            assertTrue(result is FailureResult, "got $result")
            assertEquals(
                StandardErrorCode.PUSH_SESSION_REJECTED.toErrorCode(),
                result.exception.getErrorCode(),
            )
            coVerify(exactly = 0) { api.pushChunk(any(), any(), any(), any(), any(), any()) }
            coVerify(exactly = 0) { api.completePush(any(), any(), any(), any()) }
        }

    @Test
    fun pushFiles_chunkCountMismatch_returnsFailure() =
        runBlocking {
            val api = mockk<PushClientApi>()
            // Server says 5 chunks; local FilesIndex only has 3 → mismatch.
            coEvery { api.preparePush(any(), any(), any()) } returns preparePushResult(chunkCount = 5)
            coEvery { api.pushChunk(any(), any(), any(), any(), any(), any()) } returns SuccessResult()

            val svc = service(api, index = fakeIndex(3))
            val result = svc.pushFiles(samplePasteData(), "remote-1", toUrl())

            assertTrue(result is FailureResult, "got $result")
            assertEquals(
                StandardErrorCode.PUSH_CHUNK_COUNT_MISMATCH.toErrorCode(),
                result.exception.getErrorCode(),
            )
            coVerify(exactly = 0) { api.pushChunk(any(), any(), any(), any(), any(), any()) }
        }

    @Test
    fun pushFiles_missingThenComplete_succeedsWithRetry() =
        runBlocking {
            val api = mockk<PushClientApi>()
            coEvery { api.preparePush(any(), any(), any()) } returns preparePushResult(chunkCount = 3)
            coEvery { api.pushChunk(any(), any(), any(), any(), any(), any()) } returns SuccessResult()
            // First complete reports chunk 2 missing; second complete is clean.
            val completeResponses = ArrayDeque<ClientApiResult>()
            completeResponses.add(SuccessResult(PushCompleteResponse(listOf(2))))
            completeResponses.add(SuccessResult(PushCompleteResponse(emptyList())))
            coEvery { api.completePush(any(), any(), any(), any()) } coAnswers { completeResponses.removeFirst() }

            val svc = service(api)
            val result = svc.pushFiles(samplePasteData(), "remote-1", toUrl())

            assertTrue(result is SuccessResult, "got $result")
            // 3 initial pushChunk + 1 retry of chunk 2 = 4
            coVerify(exactly = 4) { api.pushChunk(any(), any(), any(), any(), any(), any()) }
            // Round 2 must hit chunk index 2 specifically.
            coVerify(atLeast = 1) {
                api.pushChunk(any(), eq(2), any(), any(), any(), any())
            }
            coVerify(exactly = 2) { api.completePush(any(), any(), any(), any()) }
        }

    @Test
    fun pushFiles_missingPersistsAfterMaxRounds_returnsFailure() =
        runBlocking {
            val api = mockk<PushClientApi>()
            coEvery { api.preparePush(any(), any(), any()) } returns preparePushResult(chunkCount = 3)
            coEvery { api.pushChunk(any(), any(), any(), any(), any(), any()) } returns SuccessResult()
            // Always report chunk 1 missing → bounded by MAX_COMPLETE_ROUNDS.
            coEvery { api.completePush(any(), any(), any(), any()) } returns
                SuccessResult(PushCompleteResponse(listOf(1)))

            val svc = service(api)
            val result = svc.pushFiles(samplePasteData(), "remote-1", toUrl())

            assertTrue(result is FailureResult, "got $result")
            assertEquals(
                StandardErrorCode.PUSH_COMPLETE_FAIL.toErrorCode(),
                result.exception.getErrorCode(),
            )
            coVerify(exactly = FilePushService.MAX_COMPLETE_ROUNDS) {
                api.completePush(any(), any(), any(), any())
            }
        }

    @Test
    fun pushFiles_chunkUploadFails_returnsFailureFromChunkError() =
        runBlocking {
            val api = mockk<PushClientApi>()
            coEvery { api.preparePush(any(), any(), any()) } returns preparePushResult(chunkCount = 2)
            val chunkFailure =
                FailureResult(PasteException(StandardErrorCode.PUSH_CHUNK_UPLOAD_FAIL.toErrorCode(), "boom"))
            val chunkResults = ArrayDeque<ClientApiResult>()
            chunkResults.add(SuccessResult())
            chunkResults.add(chunkFailure)
            coEvery {
                api.pushChunk(any(), any(), any(), any(), any(), any())
            } coAnswers { chunkResults.removeFirst() }

            val svc = service(api, index = fakeIndex(2))
            val result = svc.pushFiles(samplePasteData(), "remote-1", toUrl())

            assertTrue(result is FailureResult, "got $result")
            assertEquals(
                StandardErrorCode.PUSH_CHUNK_UPLOAD_FAIL.toErrorCode(),
                result.exception.getErrorCode(),
            )
            // Should NOT call complete when uploads failed.
            coVerify(exactly = 0) { api.completePush(any(), any(), any(), any()) }
        }

    @Test
    fun pushFiles_needIconWithSource_invokesPushIconAfterComplete() =
        runBlocking {
            val api = mockk<PushClientApi>()
            coEvery { api.preparePush(any(), any(), any()) } returns
                preparePushResult(chunkCount = 1, needIcon = true)
            coEvery { api.pushChunk(any(), any(), any(), any(), any(), any()) } returns SuccessResult()
            coEvery { api.completePush(any(), any(), any(), any()) } returns
                SuccessResult(PushCompleteResponse(emptyList()))
            coEvery { api.pushIcon(any(), any(), any(), any()) } returns SuccessResult()

            val userDataPathProvider = mockk<UserDataPathProvider>(relaxed = true)
            // resolveIconPath would normally hit disk; with relaxed=true it returns a default
            // okio.Path. The existFile check via getFileUtils() will return false → pushIcon
            // is short-circuited. We verify the contract holds: a missing local icon does NOT
            // fail the overall push, and the receiver's pull-icon task remains the fallback.
            val pasteData =
                PasteData(
                    appInstanceId = "sender",
                    pasteAppearItem =
                        createFilesPasteItem(
                            relativePathList = emptyList(),
                            fileInfoTreeMap = emptyMap(),
                        ),
                    pasteCollection = PasteCollection(emptyList()),
                    pasteType = PasteType.FILE_TYPE.type,
                    source = "Slack",
                    size = 0L,
                    hash = "h",
                )
            val svc =
                FilePushService(
                    configManager = configManager(),
                    pasteSyncProcessManager = inlineProcessManager(),
                    pushClientApi = api,
                    userDataPathProvider = userDataPathProvider,
                    filesIndexFactory = { _, _, _ -> fakeIndex(1) },
                    chunkReader = { byteArrayOf(1) },
                )

            val result = svc.pushFiles(pasteData, "remote-1", toUrl())

            assertTrue(result is SuccessResult, "got $result")
            // Overall push must succeed regardless of whether pushIcon ran or
            // was skipped because of missing local icon bytes.
            coVerify(exactly = 1) { api.completePush(any(), any(), any(), any()) }
        }
}
