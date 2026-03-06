package com.crosspaste.paste

import com.crosspaste.app.AppFileType
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.notification.NotificationManager
import com.crosspaste.paste.item.CreatePasteItemHelper.createColorPasteItem
import com.crosspaste.paste.item.CreatePasteItemHelper.createHtmlPasteItem
import com.crosspaste.paste.item.CreatePasteItemHelper.createRtfPasteItem
import com.crosspaste.paste.item.CreatePasteItemHelper.createTextPasteItem
import com.crosspaste.paste.item.CreatePasteItemHelper.createUrlPasteItem
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.utils.DateUtils
import com.crosspaste.utils.getCodecsUtils
import com.crosspaste.utils.getCompressUtils
import com.crosspaste.utils.getJsonUtils
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toOkioPath
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
        val json = pasteData.toJson()
        val base64 = codecsUtils.base64Encode(json.encodeToByteArray())
        val decodedJson = codecsUtils.base64Decode(base64).decodeToString()
        val restored = PasteData.fromJson(decodedJson)

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
                    val json = pd.toJson()
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
                    PasteData.fromJson(json)?.let { restoredList.add(it) }
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
        runTest {
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

            var finalProgress = 0f
            exportService.export(exportParam) { progress -> finalProgress = progress }

            // Wait for async export to complete
            Thread.sleep(3000)

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
            val restored = PasteData.fromJson(json)
            assertNotNull(restored)
            assertEquals(PasteType.TEXT_TYPE.type, restored.pasteType)
            assertEquals("service export test", restored.pasteAppearItem?.getSummary())
        }
}
