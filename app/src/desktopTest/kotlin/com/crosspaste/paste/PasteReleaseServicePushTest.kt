package com.crosspaste.paste

import com.crosspaste.Database
import com.crosspaste.config.AppConfig
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.paste.item.CreatePasteItemHelper.createFilesPasteItem
import com.crosspaste.paste.item.PasteItemReader
import com.crosspaste.path.PlatformUserDataPathProvider
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.presist.SingleFileInfoTree
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
        userDataPathProvider: UserDataPathProvider = mockk(relaxed = true),
    ): PasteReleaseService =
        PasteReleaseService(
            commonConfigManager = commonConfigManager,
            currentPaste = mockk(relaxed = true),
            database = mockk<Database>(relaxed = true),
            pasteDao = pasteDao,
            pasteItemReader = mockk<PasteItemReader>(relaxed = true),
            pasteProcessPlugins = emptyList(),
            searchContentService = mockk(relaxed = true),
            taskSubmitter = mockk<TaskSubmitter>(relaxed = true),
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
    fun releaseRemotePasteDataForPush_markDeletesWhenFilesIndexEmpty() =
        runBlocking {
            val pasteDao = mockk<PasteDao>(relaxed = true)
            coEvery { pasteDao.createPasteData(any(), any()) } returns 99L
            coEvery { pasteDao.markDeletePasteData(any()) } returns Result.success(Unit)

            // userDataPathProvider.resolve is called by buildFilesIndexForReceive with the FilesPasteItem
            // but relaxed=true makes it a no-op — builder stays empty → FilesIndex has 0 chunks
            // → triggers the empty-filesIndex cleanup branch in releaseRemotePasteDataForPush.
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

            assertNull(result, "empty filesIndex should yield null result")
            coVerify(exactly = 1) { pasteDao.markDeletePasteData(99L) }
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
}
