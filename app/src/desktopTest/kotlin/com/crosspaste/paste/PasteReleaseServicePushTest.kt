package com.crosspaste.paste

import com.crosspaste.Database
import com.crosspaste.config.AppConfig
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.paste.item.CreatePasteItemHelper.createFilesPasteItem
import com.crosspaste.paste.item.PasteItemReader
import com.crosspaste.path.PlatformUserDataPathProvider
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.presist.DirFileInfoTree
import com.crosspaste.presist.SingleFileInfoTree
import com.crosspaste.sync.PastePullCursorManager
import com.crosspaste.task.TaskSubmitter
import com.crosspaste.utils.getJsonUtils
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toOkioPath
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PasteReleaseServicePushTest {

    @Suppress("unused")
    private val jsonUtils = getJsonUtils()

    private fun newService(
        pasteDao: PasteDao = mockk(relaxed = true),
        commonConfigManager: CommonConfigManager = defaultConfigManager(),
        taskSubmitter: TaskSubmitter = mockk(relaxed = true),
        userDataPathProvider: UserDataPathProvider = mockk(relaxed = true),
    ): PasteReleaseService =
        PasteReleaseService(
            commonConfigManager = commonConfigManager,
            currentPaste = mockk(relaxed = true),
            database = mockk<Database>(relaxed = true),
            notificationManager = mockk(relaxed = true),
            pasteDao = pasteDao,
            pasteItemReader = mockk<PasteItemReader>(relaxed = true),
            pasteProcessPlugins = emptyList(),
            pastePullCursorManager = mockk<PastePullCursorManager>(relaxed = true),
            searchContentService = mockk(relaxed = true),
            syncRuntimeInfoDao = mockk(relaxed = true),
            taskSubmitter = taskSubmitter,
            userDataPathProvider = userDataPathProvider,
        )

    private fun defaultConfigManager(): CommonConfigManager {
        val appConfig = mockk<AppConfig>(relaxed = true)
        every { appConfig.maxBackupFileSize } returns 100L // 100 MB after bytesSize() multiply
        val configManager = mockk<CommonConfigManager>(relaxed = true)
        every { configManager.getCurrentConfig() } returns appConfig
        return configManager
    }

    /**
     * Build a real [UserDataPathProvider] rooted at [storageRoot]. Required for the
     * happy-path test, which asserts side effects on the real filesystem.
     */
    private fun realPathProvider(storageRoot: File): UserDataPathProvider {
        val appConfig =
            mockk<AppConfig>(relaxed = true).also {
                every { it.useDefaultStoragePath } returns true
                every { it.maxBackupFileSize } returns 100L
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

    @Test
    fun releaseRemotePasteDataForPush_rejectsEmptyMetadataBeforeCreatingRow() =
        runBlocking {
            val pasteDao = mockk<PasteDao>(relaxed = true)
            val service = newService(pasteDao = pasteDao)

            val emptyFilesItem =
                createFilesPasteItem(
                    relativePathList = emptyList(),
                    fileInfoTreeMap = emptyMap(),
                )
            val pasteData =
                PasteData(
                    appInstanceId = "test-mobile",
                    pasteAppearItem = emptyFilesItem,
                    pasteCollection = PasteCollection(emptyList()),
                    pasteType = PasteType.FILE_TYPE.type,
                    source = null,
                    size = 0L,
                    hash = "",
                )

            val result = service.releaseRemotePasteDataForPush(pasteData)

            assertNull(result, "invalid file metadata should yield null result")
            coVerify(exactly = 0) { pasteDao.createPasteData(any(), any()) }
            coVerify(exactly = 0) { pasteDao.markDeletePasteData(any()) }
        }

    @Test
    fun releaseRemotePasteData_discardsInvalidMetadataWithoutFailingBatch() =
        runBlocking {
            val pasteDao = mockk<PasteDao>(relaxed = true)
            val taskSubmitter = mockk<TaskSubmitter>(relaxed = true)
            val service = newService(pasteDao = pasteDao, taskSubmitter = taskSubmitter)
            val emptyFilesItem =
                createFilesPasteItem(
                    relativePathList = emptyList(),
                    fileInfoTreeMap = emptyMap(),
                )
            val pasteData =
                PasteData(
                    appInstanceId = "test-mobile",
                    pasteAppearItem = emptyFilesItem,
                    pasteCollection = PasteCollection(emptyList()),
                    pasteType = PasteType.FILE_TYPE.type,
                    source = null,
                    size = 0L,
                    hash = "",
                )

            val result = service.releaseRemotePasteData(pasteData) {}

            assertTrue(result.isSuccess, "invalid paste is discarded, not failed")
            coVerify(exactly = 0) { pasteDao.createPasteData(any(), any()) }
            coVerify(exactly = 0) { taskSubmitter.submit(any()) }
        }

    /**
     * Regression: before the fix, `releaseRemotePasteDataForPush` built the FilesIndex
     * via `buildFilesIndex` which passed `isPull = false` to `UserDataPathProvider.resolve`,
     * so the parent directory and empty file slots were never created on disk.
     * The very first `/sync/file/push` chunk then died with `FileNotFoundException`
     * (`RandomAccessFile("rw")` does NOT create parent dirs).
     *
     * After the fix it uses `buildFilesIndexForReceive` which passes `isPull = true`,
     * pre-allocating the destination file with the right length and creating its
     * parent directory tree.
     */
    @Test
    fun releaseRemotePasteDataForPush_preallocatesFileSlotsOnDisk(
        @TempDir tempDir: File,
    ) = runBlocking {
        val storage = File(tempDir, "storage").also { it.mkdirs() }
        val newPasteId = 99L
        val fileSize = 128L
        val fileName = "image_0.JPG"

        val pasteDao =
            mockk<PasteDao>(relaxed = true).also {
                coEvery { it.createPasteData(any(), any()) } returns newPasteId
            }

        val service =
            newService(
                pasteDao = pasteDao,
                userDataPathProvider = realPathProvider(storage),
            )

        val filesItem =
            createFilesPasteItem(
                relativePathList = listOf(fileName),
                fileInfoTreeMap = mapOf(fileName to SingleFileInfoTree(size = fileSize, hash = "h")),
            )
        val pasteData =
            PasteData(
                appInstanceId = "test-mobile",
                pasteAppearItem = filesItem,
                pasteCollection = PasteCollection(emptyList()),
                pasteType = PasteType.FILE_TYPE.type,
                source = null,
                size = fileSize,
                hash = "h",
            )

        val result = service.releaseRemotePasteDataForPush(pasteData)

        assertNotNull(result, "non-empty filesIndex should yield a PushPrepareResult")
        assertEquals(newPasteId, result.pasteId)

        // The receive path must have created the parent directory tree and pre-allocated
        // an empty file slot at the destination — otherwise the first chunk write would
        // fail with FileNotFoundException at runtime.
        val ymd =
            com.crosspaste.utils.getDateUtils().run {
                getYMD(epochMillisecondsToLocalDateTime(pasteData.createTime))
            }
        val expectedFile =
            storage
                .toOkioPath()
                .resolve("files")
                .resolve("test-mobile")
                .resolve(ymd)
                .resolve(newPasteId.toString())
                .resolve(fileName)
                .toFile()

        assertTrue(expectedFile.parentFile.isDirectory, "parent directory must exist")
        assertTrue(expectedFile.isFile, "file slot must be pre-allocated")
        assertEquals(fileSize, expectedFile.length(), "pre-allocated slot must have the expected length")
    }

    @Test
    fun discardPushPrepared_marksPreparedPasteDeleted() =
        runBlocking {
            val pasteDao = mockk<PasteDao>(relaxed = true)
            coEvery { pasteDao.markDeletePasteData(42L) } returns Result.success(Unit)
            val service = newService(pasteDao = pasteDao)

            val result = service.discardPushPrepared(42L)

            assertTrue(result.isSuccess)
            coVerify(exactly = 1) { pasteDao.markDeletePasteData(42L) }
        }

    @Test
    fun discardPushPrepared_retriesFailureAndReturnsSuccess() =
        runBlocking {
            val failure = Result.failure<Unit>(IllegalStateException("busy"))
            val pasteDao = mockk<PasteDao>()
            coEvery { pasteDao.markDeletePasteData(42L) } returnsMany
                listOf(failure, failure, Result.success(Unit))
            val service = newService(pasteDao = pasteDao)

            val result = service.discardPushPrepared(42L)

            assertTrue(result.isSuccess)
            coVerify(exactly = 3) { pasteDao.markDeletePasteData(42L) }
        }

    @Test
    fun discardPushPrepared_reportsFailureAfterRetries() =
        runBlocking {
            val failure = Result.failure<Unit>(IllegalStateException("unavailable"))
            val pasteDao = mockk<PasteDao>()
            coEvery { pasteDao.markDeletePasteData(42L) } returns failure
            val service = newService(pasteDao = pasteDao)

            val result = service.discardPushPrepared(42L)

            assertTrue(result.isFailure)
            coVerify(exactly = 3) { pasteDao.markDeletePasteData(42L) }
        }

    @Test
    fun releaseRemotePasteDataForPush_rejectsUnsafeTreeBeforeCreatingPaste(
        @TempDir tempDir: File,
    ) = runBlocking {
        val pasteDao = mockk<PasteDao>(relaxed = true)
        val service =
            newService(
                pasteDao = pasteDao,
                userDataPathProvider = realPathProvider(File(tempDir, "storage")),
            )
        val filesItem =
            createFilesPasteItem(
                relativePathList = listOf("folder"),
                fileInfoTreeMap =
                    mapOf(
                        "folder" to
                            DirFileInfoTree(
                                tree = mapOf("../../escape.txt" to SingleFileInfoTree(1, "hash")),
                                size = 1,
                                hash = "dir-hash",
                            ),
                    ),
            )
        val pasteData =
            PasteData(
                appInstanceId = "test-mobile",
                pasteAppearItem = filesItem,
                pasteCollection = PasteCollection(emptyList()),
                pasteType = PasteType.FILE_TYPE.type,
                size = 1,
                hash = "hash",
            )

        assertNull(service.releaseRemotePasteDataForPush(pasteData))
        coVerify(exactly = 0) { pasteDao.createPasteData(any(), any()) }
    }
}
