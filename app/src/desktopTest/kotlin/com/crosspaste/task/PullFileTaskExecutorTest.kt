package com.crosspaste.task

import com.crosspaste.config.AppConfig
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.db.task.ExecutionHistory
import com.crosspaste.db.task.PasteTask
import com.crosspaste.db.task.PasteTaskExtraInfo
import com.crosspaste.db.task.PullExtraInfo
import com.crosspaste.db.task.TaskStatus
import com.crosspaste.db.task.TaskType
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.clientapi.createFailureResult
import com.crosspaste.paste.PasteCollection
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteType
import com.crosspaste.paste.item.CreatePasteItemHelper.createFilesPasteItem
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.path.PlatformUserDataPathProvider
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.presist.SingleFileInfoTree
import com.crosspaste.sync.FilePullResult
import com.crosspaste.sync.FilePullService
import com.crosspaste.utils.getJsonUtils
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toOkioPath
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Guards the conflict-rename persistence on the pull path. Before it, a first
 * attempt that renamed a colliding file and then failed never wrote the rename
 * back, so the retry (which reuses paths instead of re-resolving) truncated the
 * user's original file and the final cleanup deleted it.
 */
class PullFileTaskExecutorTest {

    private val jsonUtils = getJsonUtils()

    private val pasteId = 7L

    private fun realPathProvider(storageRoot: File): UserDataPathProvider {
        val appConfig =
            mockk<AppConfig>(relaxed = true).also {
                every { it.useDefaultStoragePath } returns true
            }
        val configManager =
            mockk<CommonConfigManager>(relaxed = true).also {
                every { it.getCurrentConfig() } returns appConfig
            }
        val platformProvider =
            mockk<PlatformUserDataPathProvider>().also {
                every { it.getUserDefaultStoragePath() } returns storageRoot.toOkioPath()
            }
        return UserDataPathProvider(configManager, platformProvider)
    }

    private fun externalFilePasteData(
        destination: File,
        fileName: String,
    ): PasteData {
        val fileSize = 1024L
        val filesItem =
            createFilesPasteItem(
                basePath = destination.absolutePath,
                relativePathList = listOf(fileName),
                fileInfoTreeMap = mapOf(fileName to SingleFileInfoTree(size = fileSize, hash = "h")),
            )
        return PasteData(
            id = pasteId,
            appInstanceId = "remote-peer",
            pasteAppearItem = filesItem,
            pasteCollection = PasteCollection(emptyList()),
            pasteType = PasteType.FILE_TYPE.type,
            source = null,
            size = fileSize,
            hash = "h",
        )
    }

    private fun pullTask(previousFailures: Int): PasteTask {
        val extraInfo = PullExtraInfo(id = 99L)
        repeat(previousFailures) {
            extraInfo.executionHistories.add(ExecutionHistory(0L, 1L, TaskStatus.FAILURE, "boom"))
        }
        return PasteTask(
            taskId = 1L,
            pasteDataId = pasteId,
            taskType = TaskType.PULL_FILE_TASK,
            createTime = 0L,
            modifyTime = 0L,
            extraInfo = jsonUtils.JSON.encodeToString(PasteTaskExtraInfo.serializer(), extraInfo),
        )
    }

    private fun failureWithRename(): FilePullResult.Failure =
        FilePullResult.Failure(
            failedChunks = mapOf(0 to createFailureResult(StandardErrorCode.PULL_FILE_CHUNK_TASK_FAIL, "chunk 0")),
            pullChunks = intArrayOf(0),
            renameMap = mapOf("big.apk" to "big(1).apk"),
        )

    @Test
    fun `first attempt failure persists the rename so the retry reuses the renamed slot`(
        @TempDir tempDir: File,
    ) = runTest {
        val destination = File(tempDir, "big-files").also { it.mkdirs() }
        val userFile = File(destination, "big.apk").also { it.writeText("user-data") }
        val pasteData = externalFilePasteData(destination, "big.apk")
        val persisted = slot<PasteData>()
        val pasteDao =
            mockk<PasteDao>(relaxed = true).also {
                coEvery { it.getNoDeletePasteData(pasteId) } returns pasteData
                coEvery { it.updateFilePath(capture(persisted)) } returns Unit
            }
        val filePullService =
            mockk<FilePullService>().also {
                coEvery { it.pullFiles(any(), any(), any(), any(), any(), any()) } returns failureWithRename()
            }
        val executor =
            PullFileTaskExecutor(
                filePullService = filePullService,
                pasteDao = pasteDao,
                pasteSyncProcessManager = mockk(relaxed = true),
                pasteboardService = mockk(relaxed = true),
                soundService = mockk(relaxed = true),
                userDataPathProvider = realPathProvider(File(tempDir, "storage").also { it.mkdirs() }),
            )

        val result = executor.doExecuteTask(pullTask(previousFailures = 0))

        val failure = assertIs<FailurePasteTaskResult>(result)
        assertTrue(failure.needRetry, "first failure must leave room for a retry")
        val files = assertNotNull(persisted.captured.getPasteItem(PasteFiles::class))
        assertEquals(listOf("big(1).apk"), files.relativePathList)
        assertTrue(files.fileInfoTreeMap.containsKey("big(1).apk"))
        assertEquals("user-data", userFile.readText(), "the user's original file must be untouched")
        coVerify(exactly = 0) { pasteDao.markDeletePasteData(any()) }
    }

    @Test
    fun `final failure cleans up the renamed slot and leaves the user's file alone`(
        @TempDir tempDir: File,
    ) = runTest {
        val destination = File(tempDir, "big-files").also { it.mkdirs() }
        val userFile = File(destination, "big.apk").also { it.writeText("user-data") }
        val slotFile = File(destination, "big(1).apk").also { it.writeText("") }
        val pasteData = externalFilePasteData(destination, "big.apk")
        val pasteDao =
            mockk<PasteDao>(relaxed = true).also {
                coEvery { it.getNoDeletePasteData(pasteId) } returns pasteData
            }
        val filePullService =
            mockk<FilePullService>().also {
                coEvery { it.pullFiles(any(), any(), any(), any(), any(), any()) } returns failureWithRename()
            }
        val executor =
            PullFileTaskExecutor(
                filePullService = filePullService,
                pasteDao = pasteDao,
                pasteSyncProcessManager = mockk(relaxed = true),
                pasteboardService = mockk(relaxed = true),
                soundService = mockk(relaxed = true),
                userDataPathProvider = realPathProvider(File(tempDir, "storage").also { it.mkdirs() }),
            )

        val result = executor.doExecuteTask(pullTask(previousFailures = 3))

        val failure = assertIs<FailurePasteTaskResult>(result)
        assertFalse(failure.needRetry)
        assertFalse(slotFile.exists(), "the pre-allocated renamed slot must be removed")
        assertTrue(userFile.exists(), "the user's original file must survive the cleanup")
        assertEquals("user-data", userFile.readText())
        coVerify(exactly = 1) { pasteDao.markDeletePasteData(pasteId) }
    }
}
