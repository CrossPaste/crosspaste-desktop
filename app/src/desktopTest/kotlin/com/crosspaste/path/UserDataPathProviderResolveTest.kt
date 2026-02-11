package com.crosspaste.path

import com.crosspaste.config.AppConfig
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.paste.item.CreatePasteItemHelper.createFilesPasteItem
import com.crosspaste.paste.item.FilesPasteItem
import com.crosspaste.presist.FilesIndexBuilder
import com.crosspaste.presist.SingleFileInfoTree
import com.crosspaste.utils.getJsonUtils
import io.mockk.every
import io.mockk.mockk
import okio.Path.Companion.toOkioPath
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserDataPathProviderResolveTest {

    companion object {
        @TempDir
        lateinit var nioTempFolder: File
    }

    @Suppress("unused")
    private val jsonUtils = getJsonUtils()

    private lateinit var userDataPathProvider: UserDataPathProvider

    private lateinit var downloadDir: File

    private lateinit var storageDir: File

    @BeforeAll
    fun setup() {
        downloadDir = File(nioTempFolder, "Downloads").also { it.mkdirs() }
        storageDir = File(nioTempFolder, "storage").also { it.mkdirs() }

        val appConfig = mockk<AppConfig>()
        every { appConfig.useDefaultStoragePath } returns true

        val configManager = mockk<CommonConfigManager>()
        every { configManager.getCurrentConfig() } returns appConfig

        val platformProvider = mockk<PlatformUserDataPathProvider>()
        every { platformProvider.getUserDefaultStoragePath() } returns storageDir.toOkioPath()

        userDataPathProvider = UserDataPathProvider(configManager, platformProvider)
    }

    private fun createFilesItem(
        vararg names: String,
        basePath: String? = null,
    ): FilesPasteItem =
        createFilesPasteItem(
            basePath = basePath,
            relativePathList = names.toList(),
            fileInfoTreeMap = names.associate { it to SingleFileInfoTree(size = 64, hash = "h_$it") },
        )

    // ===== C. UserDataPathProvider.resolve() rename map =====

    @Test
    fun `download dir no conflict returns empty rename map`() {
        val subDir = File(downloadDir, "test-no-conflict").also { it.mkdirs() }
        val item = createFilesItem("unique_file.txt", basePath = subDir.absolutePath)

        val renameMap =
            userDataPathProvider.resolve(
                "app1",
                "2025-01-01",
                1L,
                item,
                true,
                FilesIndexBuilder(1024),
            )

        assertTrue(renameMap.isEmpty())
        assertTrue(File(subDir, "unique_file.txt").exists())
    }

    @Test
    fun `download dir one file conflicts returns rename map with resolved name`() {
        val subDir = File(downloadDir, "test-one-conflict").also { it.mkdirs() }
        // Pre-create the conflicting file
        File(subDir, "report.pdf").createNewFile()

        val item = createFilesItem("report.pdf", basePath = subDir.absolutePath)

        val renameMap =
            userDataPathProvider.resolve(
                "app1",
                "2025-01-01",
                2L,
                item,
                true,
                FilesIndexBuilder(1024),
            )

        assertEquals(mapOf("report.pdf" to "report(1).pdf"), renameMap)
        // The file on disk should have the resolved name
        assertTrue(File(subDir, "report(1).pdf").exists())
    }

    @Test
    fun `download dir multiple files partial conflict only conflicting in map`() {
        val subDir = File(downloadDir, "test-partial-conflict").also { it.mkdirs() }
        // Only one of two files conflicts
        File(subDir, "exists.txt").createNewFile()

        val item = createFilesItem("exists.txt", "fresh.txt", basePath = subDir.absolutePath)

        val renameMap =
            userDataPathProvider.resolve(
                "app1",
                "2025-01-01",
                3L,
                item,
                true,
                FilesIndexBuilder(1024),
            )

        assertEquals(1, renameMap.size)
        assertEquals("exists(1).txt", renameMap["exists.txt"])
        assertTrue(File(subDir, "exists(1).txt").exists())
        assertTrue(File(subDir, "fresh.txt").exists())
    }

    @Test
    fun `non-download dir basePath null always returns empty map`() {
        // basePath=null means managed storage (unique per paste ID), no conflicts possible
        val item = createFilesItem("any_file.txt", basePath = null)

        val renameMap =
            userDataPathProvider.resolve(
                "app1",
                "2025-01-01",
                4L,
                item,
                true,
                FilesIndexBuilder(1024),
            )

        assertTrue(renameMap.isEmpty())
    }

    @Test
    fun `isPull false always returns empty map even for download dir`() {
        val subDir = File(downloadDir, "test-no-pull").also { it.mkdirs() }
        File(subDir, "conflict.txt").createNewFile()

        val item = createFilesItem("conflict.txt", basePath = subDir.absolutePath)

        val renameMap =
            userDataPathProvider.resolve(
                "app1",
                "2025-01-01",
                5L,
                item,
                false,
                FilesIndexBuilder(1024),
            )

        assertTrue(renameMap.isEmpty())
    }
}
