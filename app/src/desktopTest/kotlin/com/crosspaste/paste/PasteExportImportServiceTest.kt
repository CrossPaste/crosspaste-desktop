package com.crosspaste.paste

import com.crosspaste.app.AppFileType
import com.crosspaste.config.AppConfig
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.notification.NotificationManager
import com.crosspaste.paste.item.CreatePasteItemHelper.createColorPasteItem
import com.crosspaste.paste.item.CreatePasteItemHelper.createFilesPasteItem
import com.crosspaste.paste.item.CreatePasteItemHelper.createHtmlPasteItem
import com.crosspaste.paste.item.CreatePasteItemHelper.createRtfPasteItem
import com.crosspaste.paste.item.CreatePasteItemHelper.createTextPasteItem
import com.crosspaste.paste.item.CreatePasteItemHelper.createUrlPasteItem
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.paste.item.PasteItemReader
import com.crosspaste.paste.item.PasteText
import com.crosspaste.path.PlatformUserDataPathProvider
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.presist.SingleFileInfoTree
import com.crosspaste.utils.DateUtils
import com.crosspaste.utils.getCodecsUtils
import com.crosspaste.utils.getCompressUtils
import com.crosspaste.utils.getJsonUtils
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import okio.Path.Companion.toOkioPath
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class PasteExportImportServiceTest {

    @Suppress("unused")
    private val jsonUtils = getJsonUtils()

    private val codecsUtils = getCodecsUtils()

    private val compressUtils = getCompressUtils()

    // --- PasteData JSON round-trip for all paste types ---

    private fun createPasteDataForType(
        pasteType: PasteType,
        pasteAppearItem: PasteItem,
        collection: List<PasteItem> = listOf(),
    ): PasteData =
        PasteData(
            appInstanceId = "test-app",
            pasteAppearItem = pasteAppearItem,
            pasteCollection = PasteCollection(collection),
            pasteType = pasteType.type,
            size = pasteAppearItem.size,
            hash = pasteAppearItem.hash,
            pasteState = PasteState.LOADED,
            createTime = DateUtils.nowEpochMilliseconds(),
        )

    @Test
    fun `export import round-trip preserves TextPasteItem`() {
        val item = createTextPasteItem(text = "hello export import")
        val pasteData = createPasteDataForType(PasteType.TEXT_TYPE, item)
        assertExportImportRoundTrip(pasteData)
    }

    @Test
    fun `export import round-trip preserves UrlPasteItem`() {
        val item = createUrlPasteItem(url = "https://example.com/test")
        val pasteData = createPasteDataForType(PasteType.URL_TYPE, item)
        assertExportImportRoundTrip(pasteData)
    }

    @Test
    fun `export import round-trip preserves HtmlPasteItem`() {
        val htmlItem = createHtmlPasteItem(html = "<h1>Hello</h1><p>World</p>")
        val textItem = createTextPasteItem(text = "Hello World")
        val pasteData = createPasteDataForType(PasteType.HTML_TYPE, htmlItem, listOf(textItem))
        assertExportImportRoundTrip(pasteData)
    }

    @Test
    fun `export import round-trip preserves RtfPasteItem`() {
        val rtfItem = createRtfPasteItem(rtf = "{\\rtf1\\ansi Hello RTF}")
        val textItem = createTextPasteItem(text = "Hello RTF")
        val pasteData = createPasteDataForType(PasteType.RTF_TYPE, rtfItem, listOf(textItem))
        assertExportImportRoundTrip(pasteData)
    }

    @Test
    fun `export import round-trip preserves ColorPasteItem`() {
        val item = createColorPasteItem(color = 0xFF00FF00.toInt())
        val pasteData = createPasteDataForType(PasteType.COLOR_TYPE, item)
        assertExportImportRoundTrip(pasteData)
    }

    @Test
    fun `export import round-trip preserves favorite flag`() {
        val item = createTextPasteItem(text = "favorite text")
        val pasteData =
            PasteData(
                appInstanceId = "test-app",
                favorite = true,
                pasteAppearItem = item,
                pasteCollection = PasteCollection(listOf()),
                pasteType = PasteType.TEXT_TYPE.type,
                size = item.size,
                hash = item.hash,
                pasteState = PasteState.LOADED,
                createTime = DateUtils.nowEpochMilliseconds(),
            )
        assertExportImportRoundTrip(pasteData)
    }

    @Test
    fun `export import round-trip preserves source field`() {
        val item = createTextPasteItem(text = "from chrome")
        val pasteData =
            PasteData(
                appInstanceId = "test-app",
                pasteAppearItem = item,
                pasteCollection = PasteCollection(listOf()),
                pasteType = PasteType.TEXT_TYPE.type,
                source = "Google Chrome",
                size = item.size,
                hash = item.hash,
                pasteState = PasteState.LOADED,
                createTime = DateUtils.nowEpochMilliseconds(),
            )
        assertExportImportRoundTrip(pasteData)
    }

    private fun assertExportImportRoundTrip(pasteData: PasteData) {
        val json = pasteData.toStoredJson()
        val base64 = codecsUtils.base64Encode(json.encodeToByteArray())
        val decodedJson = codecsUtils.base64Decode(base64).decodeToString()
        val restored = PasteData.fromStoredJson(decodedJson)

        assertNotNull(restored, "Failed to deserialize PasteData from JSON")
        assertEquals(pasteData.appInstanceId, restored.appInstanceId)
        assertEquals(pasteData.pasteType, restored.pasteType)
        assertEquals(pasteData.hash, restored.hash)
        assertEquals(pasteData.size, restored.size)
        assertEquals(pasteData.favorite, restored.favorite)
        assertEquals(pasteData.source, restored.source)
        assertNotNull(restored.pasteAppearItem, "pasteAppearItem should not be null")
        assertEquals(pasteData.pasteAppearItem!!::class, restored.pasteAppearItem!!::class)
        assertEquals(
            pasteData.pasteCollection.pasteItems.size,
            restored.pasteCollection.pasteItems.size,
        )
    }

    // --- Compress round-trip: zipDir + unzip ---

    @Test
    fun `compressExportFile does not crash after zipDir closes the stream`() {
        val tempDir = Files.createTempDirectory("export-compress-test").toFile()
        tempDir.deleteOnExit()

        // Simulate the export directory structure
        val exportDir = File(tempDir, "export-dir")
        exportDir.mkdirs()
        File(exportDir, "paste.data").writeText("test-base64-line")
        File(exportDir, "1.count").createNewFile()

        val zipFile = File(tempDir, "output.zip")

        // This mirrors compressExportFile: zipDir closes the stream internally,
        // then we should NOT call flush() on the already-closed sink.
        val sink = zipFile.outputStream().sink().buffer()
        try {
            val result = compressUtils.zipDir(exportDir.toOkioPath(), sink)
            assertTrue(result.isSuccess, "zipDir should succeed")
            // After zipDir's .use{}, the underlying stream is closed.
            // The bug was calling sink.flush() here which throws IllegalStateException: closed
            // The fix removes flush(), so this test passes without error.
        } finally {
            runCatching { sink.close() }
        }

        // Verify the zip is valid by unzipping
        val unzipDir = File(tempDir, "restored")
        unzipDir.mkdirs()
        zipFile.inputStream().source().buffer().use { source ->
            val unzipResult = compressUtils.unzip(source, unzipDir.toOkioPath())
            assertTrue(unzipResult.isSuccess)
        }
        assertTrue(File(unzipDir, "paste.data").exists())
        assertEquals("test-base64-line", File(unzipDir, "paste.data").readText())
    }

    // --- Full export + import round-trip with mocked DAO ---

    @Test
    fun `full export and import round-trip for multiple paste types`() =
        runTest {
            val tempDir = Files.createTempDirectory("export-import-full-test").toFile()
            tempDir.deleteOnExit()

            val pasteDataList =
                listOf(
                    createPasteDataForType(
                        PasteType.TEXT_TYPE,
                        createTextPasteItem(text = "exported text"),
                    ),
                    createPasteDataForType(
                        PasteType.URL_TYPE,
                        createUrlPasteItem(url = "https://crosspaste.com"),
                    ),
                    createPasteDataForType(
                        PasteType.COLOR_TYPE,
                        createColorPasteItem(color = 0xFFAABBCC.toInt()),
                    ),
                    createPasteDataForType(
                        PasteType.HTML_TYPE,
                        createHtmlPasteItem(html = "<b>bold</b>"),
                        listOf(createTextPasteItem(text = "bold")),
                    ),
                    createPasteDataForType(
                        PasteType.RTF_TYPE,
                        createRtfPasteItem(rtf = "{\\rtf1 test}"),
                        listOf(createTextPasteItem(text = "test")),
                    ),
                )

            // Step 1: Write paste.data file (simulating export)
            val exportDir = File(tempDir, "export")
            exportDir.mkdirs()
            val pasteDataFile = File(exportDir, "paste.data")
            pasteDataFile.bufferedWriter().use { writer ->
                for (pd in pasteDataList) {
                    val json = pd.toStoredJson()
                    val base64 = codecsUtils.base64Encode(json.encodeToByteArray())
                    writer.write(base64)
                    writer.newLine()
                }
            }
            val countFile = File(exportDir, "${pasteDataList.size}.count")
            countFile.createNewFile()

            // Step 2: Compress
            val zipFile = File(tempDir, "export.data")
            zipFile.outputStream().sink().buffer().let { sink ->
                try {
                    compressUtils.zipDir(exportDir.toOkioPath(), sink)
                } finally {
                    runCatching { sink.close() }
                }
            }

            // Step 3: Decompress (simulating import)
            val importDir = File(tempDir, "import")
            importDir.mkdirs()
            zipFile.inputStream().source().buffer().use { source ->
                val result = compressUtils.unzip(source, importDir.toOkioPath())
                assertTrue(result.isSuccess)
            }

            // Step 4: Read and verify paste.data
            val restoredPasteDataFile = File(importDir, "paste.data")
            assertTrue(restoredPasteDataFile.exists())

            val restoredList = mutableListOf<PasteData>()
            restoredPasteDataFile.readLines().forEach { line ->
                if (line.isNotBlank()) {
                    val json = codecsUtils.base64Decode(line).decodeToString()
                    PasteData.fromStoredJson(json)?.let { restoredList.add(it) }
                }
            }

            assertEquals(pasteDataList.size, restoredList.size)

            // Verify each type was preserved
            for (i in pasteDataList.indices) {
                val original = pasteDataList[i]
                val restored = restoredList[i]
                assertEquals(original.pasteType, restored.pasteType)
                assertEquals(original.hash, restored.hash)
                assertEquals(original.size, restored.size)
                assertEquals(original.appInstanceId, restored.appInstanceId)
                assertNotNull(restored.pasteAppearItem)
                assertEquals(
                    original.pasteAppearItem!!::class,
                    restored.pasteAppearItem!!::class,
                )
                assertEquals(
                    original.pasteCollection.pasteItems.size,
                    restored.pasteCollection.pasteItems.size,
                )
            }
        }

    // --- PasteExportService.compressExportFile integration test ---

    @Test
    fun `PasteExportService export produces valid zip with paste data`() =
        // Real IO dispatcher: virtual-time withTimeout would expire before the work finishes
        runBlocking<Unit> {
            val tempDir = Files.createTempDirectory("export-service-test").toFile()
            tempDir.deleteOnExit()

            val textItem = createTextPasteItem(text = "service export test")
            val pasteData = createPasteDataForType(PasteType.TEXT_TYPE, textItem)

            // Mock dependencies
            val pasteDao = mockk<PasteDao>()
            coEvery { pasteDao.getExportNum(any()) } returns 1L
            coEvery { pasteDao.batchReadPasteData(any(), any(), any()) } coAnswers {
                val dealPasteData = thirdArg<(PasteData) -> Unit>()
                dealPasteData(pasteData)
                1L
            }

            val notificationManager = mockk<NotificationManager>(relaxed = true)

            val userDataPathProvider = mockk<UserDataPathProvider>(relaxed = true)
            val tempPath = tempDir.resolve("temp").toOkioPath()
            every { userDataPathProvider.resolve(appFileType = AppFileType.TEMP) } returns tempPath

            every { userDataPathProvider.autoCreateDir(any()) } answers {
                val path = firstArg<okio.Path>()
                path.toFile().mkdirs()
            }

            val exportService = PasteExportService(notificationManager, pasteDao, userDataPathProvider)

            val exportDir = File(tempDir, "output")
            exportDir.mkdirs()

            val exportParam =
                DesktopPasteExportParam(
                    types = PasteType.TYPES.map { it.type.toLong() }.toSet(),
                    onlyTagged = false,
                    maxFileSize = null,
                    exportPath = exportDir.toOkioPath(),
                )

            val done = CompletableDeferred<Float>()
            exportService.export(exportParam) { progress ->
                if (progress == 1f || progress < 0f) done.complete(progress)
            }
            assertEquals(1f, withTimeout(10.seconds) { done.await() })

            // Verify export produced a file
            val exportedFiles = exportDir.listFiles { _, name -> name.endsWith(".data") }
            assertNotNull(exportedFiles)
            assertTrue(exportedFiles.isNotEmpty(), "Expected at least one exported .data file")

            // Verify the exported file can be decompressed and contains valid data
            val exportedFile = exportedFiles.first()
            val verifyDir = File(tempDir, "verify")
            verifyDir.mkdirs()
            exportedFile.inputStream().source().buffer().use { source ->
                val result = compressUtils.unzip(source, verifyDir.toOkioPath())
                assertTrue(result.isSuccess, "Should be able to unzip exported file")
            }

            val pasteDataFileInZip = File(verifyDir, "paste.data")
            assertTrue(pasteDataFileInZip.exists(), "Exported zip should contain paste.data")

            val lines = pasteDataFileInZip.readLines().filter { it.isNotBlank() }
            assertEquals(1, lines.size, "Should have exactly one paste data line")

            val json = codecsUtils.base64Decode(lines[0]).decodeToString()
            val restored = PasteData.fromStoredJson(json)
            assertNotNull(restored)
            assertEquals(PasteType.TEXT_TYPE.type, restored.pasteType)
            assertEquals("service export test", (restored.pasteAppearItem as? PasteText)?.text)
        }

    @Test
    fun `PasteImportService import reports Failed for non-existent file`() =
        // Real IO dispatcher: virtual-time withTimeout would expire before the work finishes
        runBlocking<Unit> {
            val notificationManager = mockk<NotificationManager>(relaxed = true)
            val pasteDao = mockk<PasteDao>(relaxed = true)
            val pasteItemReader = mockk<PasteItemReader>(relaxed = true)
            val searchContentService = mockk<SearchContentService>(relaxed = true)
            val userDataPathProvider =
                mockk<UserDataPathProvider> {
                    every { resolve(appFileType = AppFileType.TEMP) } returns File("/tmp").toOkioPath()
                    every { autoCreateDir(any()) } answers {}
                }

            val importService =
                PasteImportService(
                    notificationManager = notificationManager,
                    pasteDao = pasteDao,
                    pasteItemReader = pasteItemReader,
                    searchContentService = searchContentService,
                    userDataPathProvider = userDataPathProvider,
                )

            val fakeParam = DesktopPasteImportParam(File("/tmp/non-existent-archive.data").toOkioPath())

            val result = CompletableDeferred<PasteImportResult>()
            importService.import(
                pasteImportParam = fakeParam,
                updateProgress = {},
                onResult = { r -> result.complete(r) },
            )

            assertEquals(PasteImportResult.Failed, withTimeout(10.seconds) { result.await() })
        }

    // --- Import must keep archive-controlled paths inside managed storage ---

    @Test
    fun `import rejects records whose appInstanceId escapes storage`() {
        val tempDir = Files.createTempDirectory("import-escape-test").toFile()
        tempDir.deleteOnExit()
        val escapingIds =
            listOf(
                "../../escaped-relative",
                File(tempDir, "escaped-absolute").absolutePath,
                "C:escaped-drive",
            )

        for (appInstanceId in escapingIds) {
            val pasteDao = mockk<PasteDao>(relaxed = true)
            val result =
                importArchive(
                    tempDir = tempDir,
                    pasteDao = pasteDao,
                    pasteData = createFilesPasteData(appInstanceId, "a.sh"),
                )

            assertEquals(PasteImportResult.Completed(0, 1), result, appInstanceId)
            coVerify(exactly = 0) { pasteDao.createPasteData(any()) }
        }
        assertFalse(File(tempDir, "escaped-relative").exists())
        assertFalse(File(tempDir, "escaped-absolute").exists())
    }

    @Test
    fun `import rejects records whose file name escapes the paste directory`() {
        val tempDir = Files.createTempDirectory("import-escape-name-test").toFile()
        tempDir.deleteOnExit()
        val pasteDao = mockk<PasteDao>(relaxed = true)

        val result =
            importArchive(
                tempDir = tempDir,
                pasteDao = pasteDao,
                pasteData = createFilesPasteData("remote-app", "sub/.."),
            )

        assertEquals(PasteImportResult.Completed(0, 1), result)
        coVerify(exactly = 0) { pasteDao.createPasteData(any()) }
    }

    @Test
    fun `import moves file resources into managed storage`() {
        val tempDir = Files.createTempDirectory("import-files-test").toFile()
        tempDir.deleteOnExit()
        val pasteDao = mockk<PasteDao>(relaxed = true)
        coEvery { pasteDao.createPasteData(any()) } returns 42L

        val result =
            importArchive(
                tempDir = tempDir,
                pasteDao = pasteDao,
                pasteData = createFilesPasteData("remote-app", "a.txt"),
                archiveFiles = mapOf("remote-app/1/a.txt" to "hello"),
            )

        assertEquals(PasteImportResult.Completed(1, 1), result)
        val imported =
            File(tempDir, "storage/files/remote-app")
                .walkTopDown()
                .single { it.name == "a.txt" }
        assertEquals("42", imported.parentFile.name)
        assertEquals("hello", imported.readText())
    }

    @Test
    fun `import of a record whose file is missing fails it and reclaims the row`() {
        val tempDir = Files.createTempDirectory("import-missing-file-test").toFile()
        tempDir.deleteOnExit()
        val pasteDao = mockk<PasteDao>(relaxed = true)
        coEvery { pasteDao.createPasteData(any()) } returns 42L

        val result =
            importArchive(
                tempDir = tempDir,
                pasteDao = pasteDao,
                pasteData = createFilesPasteData("remote-app", "a.txt"),
            )

        assertEquals(PasteImportResult.Completed(0, 1), result)
        // Through a delete task, not a bare state change that nothing ever reclaims
        coVerify(exactly = 1) { pasteDao.markDeletePasteData(42L) }
        coVerify(exactly = 0) { pasteDao.updatePasteState(42L, PasteState.DELETED) }
        coVerify(exactly = 0) { pasteDao.updatePasteState(42L, PasteState.LOADED) }
    }

    private fun runExport(
        tempDir: File,
        pastes: List<PasteData>,
        exportParam: PasteExportParam,
        onProgress: (Float) -> Unit = {},
    ): Float {
        val pasteDao = mockk<PasteDao>()
        coEvery { pasteDao.getExportNum(any()) } returns pastes.size.toLong()
        coEvery { pasteDao.batchReadPasteData(any(), any(), any()) } coAnswers {
            val dealPasteData = thirdArg<(PasteData) -> Unit>()
            pastes.forEach(dealPasteData)
            pastes.size.toLong()
        }
        val userDataPathProvider = mockk<UserDataPathProvider>(relaxed = true)
        every { userDataPathProvider.resolve(appFileType = AppFileType.TEMP) } returns
            File(tempDir, "temp").toOkioPath()
        every { userDataPathProvider.autoCreateDir(any()) } answers { firstArg<okio.Path>().toFile().mkdirs() }
        every { userDataPathProvider.resolve(any<okio.Path>(), any<String>(), any(), any()) } answers {
            firstArg<okio.Path>().resolve(secondArg<String>())
        }

        val done = CompletableDeferred<Float>()
        PasteExportService(mockk(relaxed = true), pasteDao, userDataPathProvider).export(exportParam) { progress ->
            onProgress(progress)
            if (progress == 1f || progress < 0f) done.complete(progress)
        }
        return runBlocking { withTimeout(10.seconds) { done.await() } }
    }

    @Test
    fun `export leaves out a paste whose files are over the size limit`() {
        val tempDir = Files.createTempDirectory("export-oversized-test").toFile()
        tempDir.deleteOnExit()
        val exportDir = File(tempDir, "output").also { it.mkdirs() }
        val text = createPasteDataForType(PasteType.TEXT_TYPE, createTextPasteItem(text = "kept"))
        val bigFile =
            createFilesPasteData("remote-app", "big.bin").let { pasteData ->
                pasteData.copy(
                    pasteAppearItem =
                        createFilesPasteItem(
                            relativePathList = listOf("big.bin"),
                            fileInfoTreeMap = mapOf("big.bin" to SingleFileInfoTree(size = 100, hash = "h")),
                        ),
                )
            }

        val progress =
            runExport(
                tempDir,
                listOf(text, bigFile),
                DesktopPasteExportParam(
                    types = PasteType.TYPES.map { it.type.toLong() }.toSet(),
                    onlyTagged = false,
                    maxFileSize = 10,
                    exportPath = exportDir.toOkioPath(),
                ),
            )

        assertEquals(1f, progress)
        val verifyDir = File(tempDir, "verify").also { it.mkdirs() }
        exportDir.listFiles()!!.single().inputStream().source().buffer().use { source ->
            assertTrue(compressUtils.unzip(source, verifyDir.toOkioPath()).isSuccess)
        }
        assertEquals(1, File(verifyDir, "paste.data").readLines().count { it.isNotBlank() })
        assertTrue(File(verifyDir, "1.count").exists())
    }

    private fun filesPasteData(
        sourceDir: File,
        vararg names: String,
    ): PasteData {
        val item =
            createFilesPasteItem(
                basePath = sourceDir.absolutePath,
                relativePathList = names.toList(),
                fileInfoTreeMap = names.associateWith { SingleFileInfoTree(size = 5, hash = it) },
            )
        return createFilesPasteData("remote-app", names.first()).copy(
            pasteAppearItem = item,
            size = item.size,
            hash = item.hash,
        )
    }

    private fun exportParam(exportDir: File) =
        DesktopPasteExportParam(
            types = PasteType.TYPES.map { it.type.toLong() }.toSet(),
            onlyTagged = false,
            maxFileSize = null,
            exportPath = exportDir.toOkioPath(),
        )

    private fun unzipSingleExport(
        exportDir: File,
        verifyDir: File,
    ) {
        verifyDir.mkdirs()
        exportDir.listFiles()!!.single().inputStream().source().buffer().use { source ->
            assertTrue(compressUtils.unzip(source, verifyDir.toOkioPath()).isSuccess)
        }
    }

    @Test
    fun `export streams files into the package without staging copies`() {
        val tempDir = Files.createTempDirectory("export-stream-test").toFile()
        tempDir.deleteOnExit()
        val sourceDir = File(tempDir, "source").also { it.mkdirs() }
        File(sourceDir, "a.txt").writeText("hello")
        File(sourceDir, "folder/sub").also { it.mkdirs() }.resolve("b.txt").writeText("nested")
        val exportDir = File(tempDir, "output").also { it.mkdirs() }
        val stagedFiles = mutableSetOf<String>()

        val progress =
            runExport(
                tempDir,
                listOf(filesPasteData(sourceDir, "a.txt", "folder")),
                exportParam(exportDir),
            ) {
                File(tempDir, "temp").walkTopDown().filter { it.isFile }.forEach { stagedFiles += it.name }
            }

        assertEquals(1f, progress)
        // Only the metadata is staged; the file content goes straight into the zip
        assertEquals(setOf("paste.data", "1.count"), stagedFiles)
        val verifyDir = File(tempDir, "verify")
        unzipSingleExport(exportDir, verifyDir)
        assertEquals("hello", File(verifyDir, "remote-app/1/a.txt").readText())
        assertEquals("nested", File(verifyDir, "remote-app/1/folder/sub/b.txt").readText())
        assertEquals(1, File(verifyDir, "paste.data").readLines().count { it.isNotBlank() })
        assertTrue(File(verifyDir, "1.count").exists())
    }

    @Test
    fun `export leaves out a paste whose file is missing and keeps the indices consecutive`() {
        val tempDir = Files.createTempDirectory("export-missing-test").toFile()
        tempDir.deleteOnExit()
        val sourceDir = File(tempDir, "source").also { it.mkdirs() }
        File(sourceDir, "kept.txt").writeText("kept")
        val exportDir = File(tempDir, "output").also { it.mkdirs() }

        val progress =
            runExport(
                tempDir,
                listOf(filesPasteData(sourceDir, "gone.txt"), filesPasteData(sourceDir, "kept.txt")),
                exportParam(exportDir),
            )

        assertEquals(1f, progress)
        val verifyDir = File(tempDir, "verify")
        unzipSingleExport(exportDir, verifyDir)
        assertEquals("kept", File(verifyDir, "remote-app/1/kept.txt").readText())
        assertEquals(1, File(verifyDir, "paste.data").readLines().count { it.isNotBlank() })
        assertTrue(File(verifyDir, "1.count").exists())
    }

    @Test
    fun `export with nothing exported leaves no package behind`() {
        val tempDir = Files.createTempDirectory("export-empty-test").toFile()
        tempDir.deleteOnExit()
        val exportDir = File(tempDir, "output").also { it.mkdirs() }

        val progress = runExport(tempDir, listOf(), exportParam(exportDir))

        assertEquals(1f, progress)
        assertEquals(0, exportDir.listFiles()!!.size)
    }

    @Test
    fun `failed compression removes the half written package`() {
        val tempDir = Files.createTempDirectory("export-truncated-test").toFile()
        tempDir.deleteOnExit()
        val discarded = mutableListOf<String>()
        val exportParam =
            object : PasteExportParam(PasteType.TYPES.map { it.type.toLong() }.toSet(), false, null) {
                override fun exportBufferedSink(fileName: String): okio.BufferedSink =
                    object : okio.Sink by okio.blackholeSink() {
                        override fun write(
                            source: okio.Buffer,
                            byteCount: Long,
                        ): Unit = throw java.io.IOException("disk full")
                    }.buffer()

                override fun discardExport(fileName: String) {
                    discarded += fileName
                }
            }

        val progress =
            runExport(
                tempDir,
                listOf(createPasteDataForType(PasteType.TEXT_TYPE, createTextPasteItem(text = "x"))),
                exportParam,
            )

        assertEquals(-1f, progress)
        assertEquals(1, discarded.size)
        assertTrue(discarded.single().startsWith("crosspaste-export-"))
    }

    private fun createFilesPasteData(
        appInstanceId: String,
        fileName: String,
    ): PasteData {
        val item =
            createFilesPasteItem(
                relativePathList = listOf(fileName),
                fileInfoTreeMap = mapOf(fileName to SingleFileInfoTree(size = 5, hash = "h")),
            )
        return PasteData(
            appInstanceId = appInstanceId,
            pasteAppearItem = item,
            pasteCollection = PasteCollection(listOf()),
            pasteType = PasteType.FILE_TYPE.type,
            size = item.size,
            hash = item.hash,
            pasteState = PasteState.LOADED,
            createTime = DateUtils.nowEpochMilliseconds(),
        )
    }

    private fun importArchive(
        tempDir: File,
        pasteDao: PasteDao,
        pasteData: PasteData,
        archiveFiles: Map<String, String> = mapOf(),
    ): PasteImportResult {
        val exportDir = File(tempDir, "export-${System.nanoTime()}").also { it.mkdirs() }
        File(exportDir, "paste.data").writeText(
            codecsUtils.base64Encode(pasteData.toStoredJson().encodeToByteArray()) + "\n",
        )
        File(exportDir, "1.count").createNewFile()
        archiveFiles.forEach { (path, content) ->
            File(exportDir, path).also { it.parentFile.mkdirs() }.writeText(content)
        }
        val archive = File(tempDir, "${exportDir.name}.data")
        val sink = archive.outputStream().sink().buffer()
        try {
            assertTrue(compressUtils.zipDir(exportDir.toOkioPath(), sink).isSuccess)
        } finally {
            runCatching { sink.close() }
        }

        val appConfig = mockk<AppConfig>()
        every { appConfig.useDefaultStoragePath } returns true
        val configManager = mockk<CommonConfigManager>()
        every { configManager.getCurrentConfig() } returns appConfig
        val platformProvider = mockk<PlatformUserDataPathProvider>()
        every { platformProvider.getUserDefaultStoragePath() } returns File(tempDir, "storage").toOkioPath()

        val importService =
            PasteImportService(
                notificationManager = mockk(relaxed = true),
                pasteDao = pasteDao,
                pasteItemReader = mockk(relaxed = true),
                searchContentService = mockk(relaxed = true),
                userDataPathProvider = UserDataPathProvider(configManager, platformProvider),
            )

        val result = CompletableDeferred<PasteImportResult>()
        importService.import(
            pasteImportParam = DesktopPasteImportParam(archive.toOkioPath()),
            updateProgress = {},
            onResult = { result.complete(it) },
        )
        return runBlocking { withTimeout(10.seconds) { result.await() } }
    }
}
