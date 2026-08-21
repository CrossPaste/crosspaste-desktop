package com.crosspaste.cli

import com.crosspaste.app.AppInfo
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.config.DesktopAppConfig
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.db.paste.PasteTagDao
import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.db.sync.SyncRuntimeInfoDao
import com.crosspaste.paste.PasteCollection
import com.crosspaste.paste.PasteContentEditor
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteDataHelper
import com.crosspaste.paste.PasteReleaseService
import com.crosspaste.paste.PasteState
import com.crosspaste.paste.PasteTag
import com.crosspaste.paste.PasteType
import com.crosspaste.paste.PasteboardService
import com.crosspaste.paste.SearchContentService
import com.crosspaste.paste.item.ColorPasteItem
import com.crosspaste.paste.item.CreatePasteItemHelper.createColorPasteItem
import com.crosspaste.paste.item.CreatePasteItemHelper.createHtmlPasteItem
import com.crosspaste.paste.item.CreatePasteItemHelper.createImagesPasteItem
import com.crosspaste.paste.item.CreatePasteItemHelper.createTextPasteItem
import com.crosspaste.paste.item.CreatePasteItemHelper.createUrlPasteItem
import com.crosspaste.paste.item.DefaultPasteItemReader
import com.crosspaste.paste.item.HtmlPasteItem
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.paste.item.PasteItemProperties
import com.crosspaste.paste.item.TextPasteItem
import com.crosspaste.paste.item.UrlPasteItem
import com.crosspaste.path.PlatformUserDataPathProvider
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.platform.Platform
import com.crosspaste.presist.SingleFileInfoTree
import com.crosspaste.task.TaskSubmitter
import com.crosspaste.utils.getJsonUtils
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okio.Path.Companion.toPath
import org.jetbrains.skia.Surface
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class CliRoutingTest {

    // Guard against PasteItem/JsonUtils circular class initialization
    private val jsonUtils = getJsonUtils()

    private val json = Json { ignoreUnknownKeys = true }

    private val appInfo =
        AppInfo(
            appInstanceId = "test-instance",
            appVersion = "2.1.7.9999",
            appRevision = "Unknown",
            userName = "tester",
        )

    private class Fixture {
        val cliPairingService = mockk<CliPairingService>()
        val configManager = mockk<DesktopConfigManager>()

        // Real provider over a stub platform path: paste items in these tests
        // carry an explicit basePath, so only relative resolution is exercised
        val userDataPathProvider =
            UserDataPathProvider(
                mockk<CommonConfigManager> {
                    every { getCurrentConfig() } returns DesktopAppConfig(language = "en")
                },
                object : PlatformUserDataPathProvider {
                    override fun getUserDefaultStoragePath() = "/tmp/cli-routing-test".toPath()
                },
            )
        val pasteboardService = mockk<PasteboardService>()
        val pasteDao = mockk<PasteDao>()
        val pasteReleaseService = mockk<PasteReleaseService>()
        val pasteTagDao = mockk<PasteTagDao>()
        val searchContentService = mockk<SearchContentService>()
        val syncRuntimeInfoDao = mockk<SyncRuntimeInfoDao>()
        val pasteDataHelper = PasteDataHelper(DefaultPasteItemReader())

        val taskSubmitter = mockk<TaskSubmitter>(relaxed = true)

        // Real editor over the mocked DAO: update tests capture the item and
        // collection the route hands to updatePasteContent instead of
        // re-mocking the type dispatch
        val pasteContentEditor =
            PasteContentEditor(pasteDao, DefaultPasteItemReader(), searchContentService)

        var currentConfig = DesktopAppConfig(language = "en")

        init {
            // Mirror the real manager: getCurrentConfig reflects updateConfig,
            // so the route's write-then-verify step observes applied values
            every { configManager.getCurrentConfig() } answers { currentConfig }
            every { configManager.updateConfig(any<String>(), any()) } answers {
                // Named arguments select the key/value copy override, not the
                // generated data-class copy(language, font, ...)
                currentConfig = currentConfig.copy(key = firstArg<String>(), value = secondArg<Any>())
            }
            coEvery { syncRuntimeInfoDao.getAllSyncRuntimeInfos() } returns listOf()
            // The PasteData overload of tryWritePasteboard defaults `primary`
            // from configManager, which resolves through this property
            every { pasteboardService.configManager } returns
                mockk<CommonConfigManager> {
                    every { getCurrentConfig() } answers { currentConfig }
                }
            coEvery { pasteDao.getActiveCount() } returns 0L
            every { pasteTagDao.getAllTagsBlock() } returns listOf()
            every { pasteTagDao.getPasteTagsBlock(any()) } returns listOf()
            every { searchContentService.createSearchContent(any(), any<String>()) } returns ""
            every { searchContentService.createSearchContent(any(), any<List<String>>()) } returns ""
            coEvery {
                pasteDao.updatePasteContent(any(), any(), any(), any(), any(), any())
            } returns true
        }
    }

    private fun withCliRouting(
        fixture: Fixture,
        supportsPasteCopy: Boolean = true,
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        application {
            cliTestModule(fixture, supportsPasteCopy)
        }
        block()
    }

    private fun Application.cliTestModule(
        fixture: Fixture,
        supportsPasteCopy: Boolean,
    ) {
        install(ContentNegotiation) {
            json(Json { encodeDefaults = true })
        }
        routing {
            cliRouting(
                appInfo = appInfo,
                cliPairingService = fixture.cliPairingService,
                configManager = fixture.configManager,
                pasteContentEditor = fixture.pasteContentEditor,
                taskSubmitter = fixture.taskSubmitter,
                supportsPasteCopy = supportsPasteCopy,
                pasteboardService = fixture.pasteboardService,
                pasteDao = fixture.pasteDao,
                pasteDataHelper = fixture.pasteDataHelper,
                pasteItemReader = DefaultPasteItemReader(),
                pasteReleaseService = fixture.pasteReleaseService,
                pasteTagDao = fixture.pasteTagDao,
                searchContentService = fixture.searchContentService,
                syncRuntimeInfoDao = fixture.syncRuntimeInfoDao,
                userDataPathProvider = fixture.userDataPathProvider,
            )
        }
    }

    private fun textPasteData(
        id: Long,
        text: String,
        createTime: Long = 123L,
        pasteState: Int = PasteState.LOADED,
    ): PasteData {
        val item = createTextPasteItem(text = text)
        return PasteData(
            id = id,
            appInstanceId = appInfo.appInstanceId,
            pasteAppearItem = item,
            pasteCollection = PasteCollection(listOf()),
            pasteType = PasteType.TEXT_TYPE.type,
            source = "Test",
            size = item.size,
            hash = item.hash,
            createTime = createTime,
            pasteState = pasteState,
        )
    }

    @Test
    fun `image paste detail carries absolute file paths`() {
        val fixture = Fixture()
        val item =
            createImagesPasteItem(
                basePath = "/tmp/images-store",
                relativePathList = listOf("shot.png"),
                fileInfoTreeMap = mapOf("shot.png" to SingleFileInfoTree(size = 10L, hash = "img")),
            )
        val pasteData =
            PasteData(
                id = 7L,
                appInstanceId = appInfo.appInstanceId,
                pasteAppearItem = item,
                pasteCollection = PasteCollection(listOf()),
                pasteType = PasteType.IMAGE_TYPE.type,
                source = "Test",
                size = item.size,
                hash = item.hash,
                createTime = 123L,
                pasteState = PasteState.LOADED,
            )
        coEvery { fixture.pasteDao.getLatestLoadedPasteData() } returns pasteData
        withCliRouting(fixture) {
            val detail =
                json.decodeFromString<CliPasteDetailDto>(
                    client.get("/cli/paste/latest").bodyAsText(),
                )
            assertEquals("image", detail.typeName)
            assertEquals(listOf("/tmp/images-store/shot.png"), detail.filePaths)
        }
    }

    @Test
    fun `text paste detail has no file paths`() {
        val fixture = Fixture()
        coEvery { fixture.pasteDao.getLatestLoadedPasteData() } returns textPasteData(42L, "hello")
        withCliRouting(fixture) {
            val detail =
                json.decodeFromString<CliPasteDetailDto>(
                    client.get("/cli/paste/latest").bodyAsText(),
                )
            assertEquals(emptyList(), detail.filePaths)
        }
    }

    /** Builds an image paste whose stored file holds [bytes] on real disk. */
    private fun imagePasteDataOnDisk(
        id: Long,
        bytes: ByteArray,
        fileName: String = "shot.png",
    ): PasteData {
        val dir = Files.createTempDirectory("cli-image-route").toFile()
        File(dir, fileName).writeBytes(bytes)
        val item =
            createImagesPasteItem(
                basePath = dir.absolutePath,
                relativePathList = listOf(fileName),
                fileInfoTreeMap =
                    mapOf(fileName to SingleFileInfoTree(size = bytes.size.toLong(), hash = "img")),
            )
        return PasteData(
            id = id,
            appInstanceId = appInfo.appInstanceId,
            pasteAppearItem = item,
            pasteCollection = PasteCollection(listOf()),
            pasteType = PasteType.IMAGE_TYPE.type,
            source = "Test",
            size = item.size,
            hash = item.hash,
            createTime = 123L,
            pasteState = PasteState.LOADED,
        )
    }

    /** Encodes a solid-[argb] PNG of the given size via Skia. */
    private fun pngBytes(
        width: Int,
        height: Int,
        argb: Int,
    ): ByteArray =
        Surface.makeRasterN32Premul(width, height).use { surface ->
            surface.canvas.clear(argb)
            surface.makeImageSnapshot().encodeToData()!!.bytes
        }

    @Test
    fun `image endpoint serves raw rgba with dimension headers`() {
        val fixture = Fixture()
        coEvery { fixture.pasteDao.getNoDeletePasteData(7L) } returns
            imagePasteDataOnDisk(7L, pngBytes(4, 2, 0xFFFF0000.toInt()))
        withCliRouting(fixture) {
            val response = client.get("/cli/paste/7/image?maxWidth=100&maxHeight=100")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("4", response.headers[CLI_IMAGE_WIDTH_HEADER])
            assertEquals("2", response.headers[CLI_IMAGE_HEIGHT_HEADER])
            val body = response.readRawBytes()
            assertEquals(4 * 2 * 4, body.size, "payload must be width * height * 4")
            // Straight-alpha RGBA byte order: opaque red
            assertEquals(0xFF.toByte(), body[0])
            assertEquals(0x00.toByte(), body[1])
            assertEquals(0x00.toByte(), body[2])
            assertEquals(0xFF.toByte(), body[3])
        }
    }

    @Test
    fun `image endpoint downscales into the requested box`() {
        val fixture = Fixture()
        coEvery { fixture.pasteDao.getNoDeletePasteData(8L) } returns
            imagePasteDataOnDisk(8L, pngBytes(100, 50, 0xFF00FF00.toInt()))
        withCliRouting(fixture) {
            val response = client.get("/cli/paste/8/image?maxWidth=10&maxHeight=10")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("10", response.headers[CLI_IMAGE_WIDTH_HEADER])
            assertEquals("5", response.headers[CLI_IMAGE_HEIGHT_HEADER])
            assertEquals(10 * 5 * 4, response.readRawBytes().size)
        }
    }

    @Test
    fun `image endpoint clamps oversized box parameters server side`() {
        val fixture = Fixture()
        coEvery { fixture.pasteDao.getNoDeletePasteData(9L) } returns
            imagePasteDataOnDisk(9L, pngBytes(2000, 100, 0xFF00FF00.toInt()))
        withCliRouting(fixture) {
            val response = client.get("/cli/paste/9/image?maxWidth=999999&maxHeight=999999")
            assertEquals(HttpStatusCode.OK, response.status)
            // Without the 1000 px clamp this would come back at 2000x100
            assertEquals("1000", response.headers[CLI_IMAGE_WIDTH_HEADER])
            assertEquals("50", response.headers[CLI_IMAGE_HEIGHT_HEADER])
        }
    }

    @Test
    fun `image endpoint maps native graphics load failure to actionable 503`() {
        val fixture = Fixture()
        coEvery { fixture.pasteDao.getNoDeletePasteData(11L) } returns
            imagePasteDataOnDisk(11L, pngBytes(4, 2, 0xFFFF0000.toInt()))
        // A missing libGL surfaces as an Error (skiko class-init failure),
        // which the transcoder's Exception handling cannot catch (#4854)
        mockkObject(CliImageTranscoder)
        try {
            every { CliImageTranscoder.transcode(any(), any(), any()) } throws
                NoClassDefFoundError("Could not initialize class org.jetbrains.skia.Image")
            withCliRouting(fixture) {
                val response = client.get("/cli/paste/11/image?maxWidth=100&maxHeight=100")
                assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
                val message = response.bodyAsText()
                assertContains(message, "libgl1")
                assertContains(message, "restart")
            }
        } finally {
            unmockkObject(CliImageTranscoder)
        }
    }

    @Test
    fun `image endpoint refuses a file over the source byte budget`() {
        val fixture = Fixture()
        val pasteData = imagePasteDataOnDisk(11L, pngBytes(4, 2, 0xFFFF0000.toInt()))
        // Grow the stored file past 64 MiB AFTER creation — the bounded read
        // must reject it at read time, size checks alone are racy
        val storedFile =
            File(
                (pasteData.pasteAppearItem as com.crosspaste.paste.item.PasteFiles).basePath!!,
                "shot.png",
            )
        RandomAccessFile(storedFile, "rw").use { it.setLength(64L * 1024 * 1024 + 1) }
        coEvery { fixture.pasteDao.getNoDeletePasteData(11L) } returns pasteData
        withCliRouting(fixture) {
            assertEquals(HttpStatusCode.NotFound, client.get("/cli/paste/11/image").status)
        }
    }

    @Test
    fun `image endpoint returns 404 for an unknown paste`() {
        val fixture = Fixture()
        coEvery { fixture.pasteDao.getNoDeletePasteData(99L) } returns null
        withCliRouting(fixture) {
            val response = client.get("/cli/paste/99/image")
            assertEquals(HttpStatusCode.NotFound, response.status)
        }
    }

    @Test
    fun `image endpoint returns 404 when the index has no file`() {
        val fixture = Fixture()
        coEvery { fixture.pasteDao.getNoDeletePasteData(7L) } returns
            imagePasteDataOnDisk(7L, pngBytes(4, 2, 0xFFFF0000.toInt()))
        coEvery { fixture.pasteDao.getNoDeletePasteData(42L) } returns textPasteData(42L, "hello")
        withCliRouting(fixture) {
            assertEquals(
                HttpStatusCode.NotFound,
                client.get("/cli/paste/7/image?index=5").status,
            )
            // A text paste has no stored files at all
            assertEquals(
                HttpStatusCode.NotFound,
                client.get("/cli/paste/42/image").status,
            )
        }
    }

    @Test
    fun `image endpoint returns 404 for an undecodable stored file`() {
        val fixture = Fixture()
        coEvery { fixture.pasteDao.getNoDeletePasteData(7L) } returns
            imagePasteDataOnDisk(7L, "definitely not an image".encodeToByteArray())
        withCliRouting(fixture) {
            val response = client.get("/cli/paste/7/image")
            assertEquals(HttpStatusCode.NotFound, response.status)
            assertContains(response.bodyAsText(), "not a decodable image")
        }
    }

    @Test
    fun `status reports app and api metadata`() {
        val fixture = Fixture()
        coEvery { fixture.pasteDao.getActiveCount() } returns 7L
        withCliRouting(fixture) {
            val response = client.get("/cli/status")
            assertEquals(HttpStatusCode.OK, response.status)
            val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals(appInfo.appVersion, body["appVersion"]?.jsonPrimitive?.content)
            assertEquals(appInfo.appInstanceId, body["appInstanceId"]?.jsonPrimitive?.content)
            assertEquals(CLI_API_VERSION.toLong(), body["apiVersion"]?.jsonPrimitive?.longOrNull)
            assertEquals(13129L, body["port"]?.jsonPrimitive?.longOrNull)
            assertEquals(7L, body["pasteCount"]?.jsonPrimitive?.longOrNull)
        }
    }

    @Test
    fun `paste latest returns detail or not found`() {
        val fixture = Fixture()
        coEvery { fixture.pasteDao.getLatestLoadedPasteData() } returns textPasteData(42L, "hello")
        withCliRouting(fixture) {
            val response = client.get("/cli/paste/latest")
            assertEquals(HttpStatusCode.OK, response.status)
            val detail = json.decodeFromString<CliPasteDetailDto>(response.bodyAsText())
            assertEquals(42L, detail.id)
            assertEquals("hello", detail.content)
            // Raw is opt-in: without ?includeRaw=true it must not be shipped
            // (for plain text it would duplicate the whole content)
            assertEquals(null, detail.rawContent)
        }

        coEvery { fixture.pasteDao.getLatestLoadedPasteData() } returns null
        withCliRouting(fixture) {
            assertEquals(HttpStatusCode.NotFound, client.get("/cli/paste/latest").status)
        }
    }

    @Test
    fun `html paste detail carries the parsed summary and the raw markup`() {
        val fixture = Fixture()
        val html = "<html><body><b>bold text</b></body></html>"
        val item = createHtmlPasteItem(html = html)
        val pasteData =
            PasteData(
                id = 43L,
                appInstanceId = appInfo.appInstanceId,
                pasteAppearItem = item,
                pasteCollection = PasteCollection(listOf()),
                pasteType = PasteType.HTML_TYPE.type,
                source = "Test",
                size = item.size,
                hash = item.hash,
                createTime = 123L,
                pasteState = PasteState.LOADED,
            )
        coEvery { fixture.pasteDao.getLatestLoadedPasteData() } returns pasteData
        withCliRouting(fixture) {
            val withRaw =
                json.decodeFromString<CliPasteDetailDto>(
                    client.get("/cli/paste/latest?includeRaw=true").bodyAsText(),
                )
            assertEquals("bold text", withRaw.content)
            assertEquals(html, withRaw.rawContent)

            val withoutRaw =
                json.decodeFromString<CliPasteDetailDto>(
                    client.get("/cli/paste/latest").bodyAsText(),
                )
            assertEquals("bold text", withoutRaw.content)
            assertEquals(null, withoutRaw.rawContent)
        }
    }

    @Test
    fun `paste by id validates the id`() {
        val fixture = Fixture()
        coEvery { fixture.pasteDao.getNoDeletePasteData(42L) } returns textPasteData(42L, "hello")
        withCliRouting(fixture) {
            assertEquals(HttpStatusCode.OK, client.get("/cli/paste/42").status)
            assertEquals(HttpStatusCode.BadRequest, client.get("/cli/paste/abc").status)
        }
    }

    @Test
    fun `history resolves repeated types and the tag filter`() {
        val fixture = Fixture()
        every { fixture.pasteTagDao.getAllTagsBlock() } returns
            listOf(PasteTag(id = 5L, name = "Work", color = 1L, sortOrder = 0L))
        val typeSlot = slot<List<Int>>()
        val tagSlot = slot<Long>()
        coEvery {
            fixture.pasteDao.searchPasteData(
                searchTerms = listOf(),
                pasteTypeList = capture(typeSlot),
                tag = capture(tagSlot),
                limit = 5,
            )
        } returns listOf(textPasteData(1L, "hello"))
        coEvery { fixture.pasteDao.getActiveCount() } returns 10L

        withCliRouting(fixture) {
            val response = client.get("/cli/history?limit=5&type=text&type=link&tag=work")
            assertEquals(HttpStatusCode.OK, response.status)
            val list = json.decodeFromString<CliPasteListDto>(response.bodyAsText())
            assertEquals(1, list.items.size)
            assertEquals(10L, list.total)
            assertEquals(listOf(PasteType.TEXT_TYPE.type, PasteType.URL_TYPE.type), typeSlot.captured)
            assertEquals(5L, tagSlot.captured)
        }
    }

    @Test
    fun `history rejects unknown type tag and sort`() {
        val fixture = Fixture()
        withCliRouting(fixture) {
            assertEquals(HttpStatusCode.BadRequest, client.get("/cli/history?type=nope").status)
            assertEquals(HttpStatusCode.BadRequest, client.get("/cli/history?type=text&type=nope").status)
            assertEquals(HttpStatusCode.BadRequest, client.get("/cli/history?tag=nope").status)
            assertEquals(HttpStatusCode.BadRequest, client.get("/cli/history?sort=sideways").status)
        }
    }

    @Test
    fun `history maps sort to creation time order`() {
        val fixture = Fixture()
        val sortSlot = mutableListOf<Boolean>()
        coEvery {
            fixture.pasteDao.searchPasteData(
                searchTerms = listOf(),
                pasteTypeList = listOf(),
                sort = capture(sortSlot),
                tag = null,
                limit = 20,
            )
        } returns listOf()

        withCliRouting(fixture) {
            assertEquals(HttpStatusCode.OK, client.get("/cli/history?sort=oldest").status)
            assertEquals(HttpStatusCode.OK, client.get("/cli/history?sort=Newest").status)
            assertEquals(HttpStatusCode.OK, client.get("/cli/history").status)
            assertEquals(listOf(false, true, true), sortSlot)
        }
    }

    @Test
    fun `history searches with q and reports a result-sized total`() {
        val fixture = Fixture()
        every { fixture.searchContentService.createSearchTerms("hello world") } returns listOf("hello", "world")
        val termsSlot = slot<List<String>>()
        coEvery {
            fixture.pasteDao.searchPasteData(
                searchTerms = capture(termsSlot),
                pasteTypeList = listOf(),
                tag = null,
                limit = 20,
            )
        } returns listOf(textPasteData(1L, "hello world"))
        // getActiveCount (fixture default 0) must not be consulted for a search
        withCliRouting(fixture) {
            val response = client.get("/cli/history?q=hello%20world")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(listOf("hello", "world"), termsSlot.captured)
            val list = json.decodeFromString<CliPasteListDto>(response.bodyAsText())
            assertEquals(1L, list.total)
        }
    }

    @Test
    fun `devices maps sync runtime info`() {
        val fixture = Fixture()
        coEvery { fixture.syncRuntimeInfoDao.getAllSyncRuntimeInfos() } returns
            listOf(
                SyncRuntimeInfo(
                    appInstanceId = "other",
                    appVersion = "2.1.7",
                    userName = "tester",
                    deviceId = "device-id",
                    deviceName = "MacBook",
                    platform = Platform(name = "Macos", arch = "arm64", bitMode = 64, version = "15"),
                    port = 13129,
                ),
            )
        withCliRouting(fixture) {
            val response = client.get("/cli/devices")
            assertEquals(HttpStatusCode.OK, response.status)
            val devices = json.decodeFromString<List<CliDeviceDto>>(response.bodyAsText())
            assertEquals(1, devices.size)
            assertEquals("other", devices[0].appInstanceId)
            assertEquals("Macos", devices[0].platform)
        }
    }

    @Test
    fun `tags lists creates and deletes`() {
        val fixture = Fixture()
        every { fixture.pasteTagDao.getAllTagsBlock() } returns
            listOf(PasteTag(id = 1L, name = "Favorite", color = 2L, sortOrder = 0L))
        coEvery { fixture.pasteTagDao.getMaxSortOrder() } returns 3L
        coEvery { fixture.pasteTagDao.createPasteTag("Work", any()) } returns 9L
        every { fixture.pasteTagDao.deletePasteTagBlock(9L) } just Runs

        withCliRouting(fixture) {
            val listResponse = client.get("/cli/tags")
            assertEquals(HttpStatusCode.OK, listResponse.status)
            val tags = json.decodeFromString<List<CliTagDto>>(listResponse.bodyAsText())
            assertEquals("Favorite", tags.single().name)

            val createResponse =
                client.post("/cli/tags") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"name":"Work"}""")
                }
            assertEquals(HttpStatusCode.OK, createResponse.status)
            val created = json.decodeFromString<CliTagDto>(createResponse.bodyAsText())
            assertEquals(9L, created.id)
            assertEquals(PasteTag.getColor(4L), created.color)

            val blankResponse =
                client.post("/cli/tags") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"name":"  "}""")
                }
            assertEquals(HttpStatusCode.BadRequest, blankResponse.status)

            assertEquals(HttpStatusCode.OK, client.delete("/cli/tags/9").status)
            verify { fixture.pasteTagDao.deletePasteTagBlock(9L) }
            assertEquals(HttpStatusCode.BadRequest, client.delete("/cli/tags/abc").status)
        }
    }

    @Test
    fun `config get returns the full config`() {
        val fixture = Fixture()
        withCliRouting(fixture) {
            val response = client.get("/cli/config")
            assertEquals(HttpStatusCode.OK, response.status)
            val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals(13129L, body["port"]?.jsonPrimitive?.longOrNull)
            assertTrue(body.containsKey("enableSoundEffect"))
        }
    }

    @Test
    fun `config put validates key and value`() {
        val fixture = Fixture()
        withCliRouting(fixture) {
            suspend fun putConfig(body: String): HttpStatusCode =
                client
                    .put("/cli/config") {
                        contentType(ContentType.Application.Json)
                        setBody(body)
                    }.status

            assertEquals(HttpStatusCode.BadRequest, putConfig("""{"key":"nope","value":"1"}"""))
            assertEquals(HttpStatusCode.BadRequest, putConfig("""{"key":"enableSoundEffect","value":"maybe"}"""))
            assertEquals(HttpStatusCode.BadRequest, putConfig("""{"key":"searchWindowHeight","value":"abc"}"""))

            // Settings with migration or service-lifecycle side effects must use
            // their dedicated workflows in the app.
            assertEquals(HttpStatusCode.BadRequest, putConfig("""{"key":"storagePath","value":"/tmp/x"}"""))
            assertEquals(HttpStatusCode.BadRequest, putConfig("""{"key":"useDefaultStoragePath","value":"false"}"""))
            // The sync port has no app setting to point at; the hint must say
            // the app manages it rather than send users hunting for one
            val portResponse =
                client.put("/cli/config") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"key":"port","value":"13130"}""")
                }
            assertEquals(HttpStatusCode.BadRequest, portResponse.status)
            assertTrue(portResponse.bodyAsText().contains("managed automatically"))
            assertEquals(
                HttpStatusCode.BadRequest,
                putConfig("""{"key":"enablePasteboardListening","value":"false"}"""),
            )
            assertEquals(HttpStatusCode.BadRequest, putConfig("""{"key":"enableMcpServer","value":"true"}"""))
            assertEquals(HttpStatusCode.BadRequest, putConfig("""{"key":"mcpServerPort","value":"13131"}"""))

            assertEquals(HttpStatusCode.OK, putConfig("""{"key":"enableSoundEffect","value":"false"}"""))
            verify { fixture.configManager.updateConfig("enableSoundEffect", false) }

            assertEquals(HttpStatusCode.OK, putConfig("""{"key":"searchWindowHeight","value":"400"}"""))
            verify { fixture.configManager.updateConfig("searchWindowHeight", 400L) }

            assertEquals(HttpStatusCode.OK, putConfig("""{"key":"language","value":"zh"}"""))
            verify { fixture.configManager.updateConfig("language", "zh") }

            // Regression: DesktopAppConfig.copy used to silently drop this key
            assertEquals(HttpStatusCode.OK, putConfig("""{"key":"enableDiscovery","value":"false"}"""))
            assertEquals(false, fixture.currentConfig.enableDiscovery)
        }
    }

    @Test
    fun `config put reports when the update was not applied`() {
        val fixture = Fixture()
        // Simulate a silently ignored update (unmapped key or rolled-back save)
        every { fixture.configManager.updateConfig(any<String>(), any()) } just Runs

        withCliRouting(fixture) {
            val response =
                client.put("/cli/config") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"key":"enableSoundEffect","value":"false"}""")
                }
            assertEquals(HttpStatusCode.InternalServerError, response.status)
        }
    }

    @Test
    fun `config put rejects int overflow before updating config`() {
        val fixture = Fixture()

        withCliRouting(fixture) {
            val response =
                client.put("/cli/config") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"key":"searchWindowHeight","value":"2147483648"}""")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals(332, fixture.currentConfig.searchWindowHeight)
            verify(exactly = 0) { fixture.configManager.updateConfig(any<String>(), any()) }
        }
    }

    @Test
    fun `copy goes through the standard local release lifecycle`() {
        val fixture = Fixture()
        val pasteDataSlot = slot<PasteData>()
        coEvery { fixture.pasteDao.createPasteData(capture(pasteDataSlot)) } returns 77L
        val releasedItemsSlot = slot<List<PasteItem>>()
        coEvery {
            fixture.pasteReleaseService.releaseLocalPasteData(
                id = 77L,
                pasteItems = capture(releasedItemsSlot),
                targetAppInstanceIds = null,
            )
        } returns Unit
        coEvery {
            fixture.pasteboardService.tryWritePasteboard(
                id = any(),
                pasteItem = any<PasteItem>(),
                localOnly = any(),
                updateCreateTime = any(),
            )
        } returns Result.success(null)

        withCliRouting(fixture) {
            val response =
                client.post("/cli/copy") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"text":"hello from cli"}""")
                }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(77L, json.decodeFromString<CliCopyResponse>(response.bodyAsText()).id)
            // The row is created LOADING and finished by releaseLocalPasteData,
            // which owns plugins, dedup and the sync/rendering tasks
            assertEquals("CLI", pasteDataSlot.captured.source)
            assertEquals(PasteState.LOADING, pasteDataSlot.captured.pasteState)
            assertEquals(1, releasedItemsSlot.captured.size)
            coVerify {
                fixture.pasteboardService.tryWritePasteboard(
                    id = 77L,
                    pasteItem = any<PasteItem>(),
                    localOnly = true,
                    updateCreateTime = any(),
                )
            }

            val emptyResponse =
                client.post("/cli/copy") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"text":""}""")
                }
            assertEquals(HttpStatusCode.BadRequest, emptyResponse.status)
        }
    }

    @Test
    fun `copy reports a failed pasteboard write`() {
        val fixture = Fixture()
        coEvery { fixture.pasteDao.createPasteData(any()) } returns 77L
        coEvery {
            fixture.pasteReleaseService.releaseLocalPasteData(any(), any(), any())
        } returns Unit
        coEvery {
            fixture.pasteboardService.tryWritePasteboard(
                id = any(),
                pasteItem = any<PasteItem>(),
                localOnly = any(),
                updateCreateTime = any(),
            )
        } returns Result.failure(IllegalStateException("clipboard busy"))

        withCliRouting(fixture) {
            val response =
                client.post("/cli/copy") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"text":"hello"}""")
                }
            assertEquals(HttpStatusCode.InternalServerError, response.status)
        }
    }

    @Test
    fun `copy rejects text over the non-file paste size limit before creating a row`() {
        val fixture = Fixture()
        // Zero limit makes any non-empty text oversized without a huge body.
        // Past this check, DiscardOversizedNonFilePlugin would empty the item
        // list during release and the reported id would be a deleted row
        fixture.currentConfig = fixture.currentConfig.copy(key = "maxNonFilePasteSize", value = 0L)

        withCliRouting(fixture) {
            val response =
                client.post("/cli/copy") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"text":"hello from cli"}""")
                }
            assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
            assertContains(response.bodyAsText(), "maxNonFilePasteSize")
            coVerify(exactly = 0) { fixture.pasteDao.createPasteData(any()) }
            coVerify(exactly = 0) {
                fixture.pasteboardService.tryWritePasteboard(
                    id = any(),
                    pasteItem = any<PasteItem>(),
                    localOnly = any(),
                    updateCreateTime = any(),
                )
            }
        }
    }

    @Test
    fun `paste copy rewrites the item to the pasteboard like the search window`() {
        val fixture = Fixture()
        val pasteData = textPasteData(7L, "hello")
        coEvery { fixture.pasteDao.getNoDeletePasteData(7L) } returns pasteData
        coEvery {
            fixture.pasteboardService.tryWritePasteboard(
                pasteData = pasteData,
                localOnly = true,
                primary = any(),
                updateCreateTime = true,
            )
        } returns Result.success(null)

        withCliRouting(fixture) {
            val response = client.post("/cli/paste/7/copy")
            assertEquals(HttpStatusCode.OK, response.status)
            assertContains(response.bodyAsText(), "Paste #7 copied.")
        }
        coVerify(exactly = 1) {
            fixture.pasteboardService.tryWritePasteboard(
                pasteData = pasteData,
                localOnly = true,
                primary = any(),
                updateCreateTime = true,
            )
        }
    }

    @Test
    fun `paste copy maps missing paste and write failure to errors`() {
        val fixture = Fixture()
        coEvery { fixture.pasteDao.getNoDeletePasteData(404L) } returns null
        val failing = textPasteData(8L, "boom")
        coEvery { fixture.pasteDao.getNoDeletePasteData(8L) } returns failing
        coEvery {
            fixture.pasteboardService.tryWritePasteboard(
                pasteData = failing,
                localOnly = true,
                primary = any(),
                updateCreateTime = true,
            )
        } returns Result.failure(RuntimeException("no clipboard"))

        withCliRouting(fixture) {
            assertEquals(HttpStatusCode.BadRequest, client.post("/cli/paste/abc/copy").status)
            assertEquals(HttpStatusCode.NotFound, client.post("/cli/paste/404/copy").status)
            assertEquals(HttpStatusCode.InternalServerError, client.post("/cli/paste/8/copy").status)
        }
    }

    @Test
    fun `paste copy refuses on a headless peer instead of reporting a no-op success`() {
        val fixture = Fixture()
        withCliRouting(fixture, supportsPasteCopy = false) {
            val response = client.post("/cli/paste/7/copy")
            assertEquals(HttpStatusCode.NotImplemented, response.status)
            assertContains(response.bodyAsText(), "headless")
        }
    }

    @Test
    fun `delete paste maps result to status`() {
        val fixture = Fixture()
        coEvery { fixture.pasteDao.markDeletePasteData(42L) } returns Result.success(Unit)
        coEvery { fixture.pasteDao.markDeletePasteData(43L) } returns
            Result.failure(IllegalArgumentException("missing"))

        withCliRouting(fixture) {
            assertEquals(HttpStatusCode.OK, client.delete("/cli/paste/42").status)
            assertEquals(HttpStatusCode.NotFound, client.delete("/cli/paste/43").status)
            assertEquals(HttpStatusCode.BadRequest, client.delete("/cli/paste/abc").status)
        }
    }

    @Test
    fun `pair nearby forwards the refresh flag and returns the device list`() {
        val fixture = Fixture()
        val device =
            CliNearbyDeviceDto(
                appInstanceId = "peer-1",
                deviceName = "Laptop",
                platform = "Macos",
                appVersion = "2.1.7",
                pairingVersion = 2,
                credentialType = "SAS_CODE",
            )
        coEvery { fixture.cliPairingService.nearbyDevices(refresh = true) } returns listOf(device)
        coEvery { fixture.cliPairingService.nearbyDevices(refresh = false) } returns listOf()

        withCliRouting(fixture) {
            val refreshed = client.get("/cli/pair/nearby?refresh=true")
            assertEquals(HttpStatusCode.OK, refreshed.status)
            assertContains(refreshed.bodyAsText(), "\"appInstanceId\":\"peer-1\"")

            val cached = client.get("/cli/pair/nearby")
            assertEquals(HttpStatusCode.OK, cached.status)
            coVerify(exactly = 1) { fixture.cliPairingService.nearbyDevices(refresh = false) }
        }
    }

    @Test
    fun `pair initiate maps outcomes to status codes`() {
        val fixture = Fixture()
        val session =
            CliPairSessionDto(
                sessionId = "session-1",
                appInstanceId = "peer-1",
                deviceName = "Laptop",
                credentialType = "SAS_CODE",
                peerFingerprint = null,
                pinExpiresAt = null,
            )
        coEvery { fixture.cliPairingService.initiate("peer-1") } returns
            CliPairingService.InitiateOutcome.Started(session)
        coEvery { fixture.cliPairingService.initiate("missing") } returns
            CliPairingService.InitiateOutcome.DeviceNotFound("not found")
        coEvery { fixture.cliPairingService.initiate("paired") } returns
            CliPairingService.InitiateOutcome.AlreadyPaired("already paired")
        coEvery { fixture.cliPairingService.initiate("legacy") } returns
            CliPairingService.InitiateOutcome.UnsupportedPeer("too old")
        coEvery { fixture.cliPairingService.initiate("busy") } returns
            CliPairingService.InitiateOutcome.Unavailable("not accepting")

        withCliRouting(fixture) {
            suspend fun initiate(appInstanceId: String) =
                client.post("/cli/pair/initiate") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"appInstanceId":"$appInstanceId"}""")
                }

            val started = initiate("peer-1")
            assertEquals(HttpStatusCode.OK, started.status)
            assertContains(started.bodyAsText(), "\"sessionId\":\"session-1\"")

            assertEquals(HttpStatusCode.NotFound, initiate("missing").status)
            assertEquals(HttpStatusCode.Conflict, initiate("paired").status)
            assertEquals(HttpStatusCode.BadRequest, initiate("legacy").status)
            assertEquals(HttpStatusCode.BadGateway, initiate("busy").status)

            val blank = initiate("")
            assertEquals(HttpStatusCode.BadRequest, blank.status)
            coVerify(exactly = 0) { fixture.cliPairingService.initiate("") }
        }
    }

    @Test
    fun `pair submit maps outcomes to status codes`() {
        val fixture = Fixture()
        coEvery { fixture.cliPairingService.submit("session-1", "123456") } returns
            CliPairingService.SubmitOutcome.Completed(
                CliPairSubmitResultDto(paired = true, retryable = false, message = "Paired with Laptop."),
            )
        coEvery { fixture.cliPairingService.submit("gone", any()) } returns
            CliPairingService.SubmitOutcome.SessionNotFound
        coEvery { fixture.cliPairingService.submit("session-1", "12") } returns
            CliPairingService.SubmitOutcome.InvalidCode

        withCliRouting(fixture) {
            suspend fun submit(
                sessionId: String,
                code: String,
            ) = client.post("/cli/pair/submit") {
                contentType(ContentType.Application.Json)
                setBody("""{"sessionId":"$sessionId","code":"$code"}""")
            }

            val paired = submit("session-1", "123456")
            assertEquals(HttpStatusCode.OK, paired.status)
            assertContains(paired.bodyAsText(), "\"paired\":true")

            assertEquals(HttpStatusCode.NotFound, submit("gone", "123456").status)
            assertEquals(HttpStatusCode.BadRequest, submit("session-1", "12").status)
        }
    }

    @Test
    fun `pair cancel maps the session lookup to status`() {
        val fixture = Fixture()
        every { fixture.cliPairingService.cancel("session-1") } returns true
        every { fixture.cliPairingService.cancel("gone") } returns false

        withCliRouting(fixture) {
            suspend fun cancel(sessionId: String) =
                client.post("/cli/pair/cancel") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"sessionId":"$sessionId"}""")
                }

            assertEquals(HttpStatusCode.OK, cancel("session-1").status)
            assertEquals(HttpStatusCode.NotFound, cancel("gone").status)
        }
    }

    @Test
    fun `paste update rewrites the text item in place`() {
        val fixture = Fixture()
        val pasteData = textPasteData(42L, "old text")
        coEvery { fixture.pasteDao.getNoDeletePasteData(42L) } returns pasteData
        val itemSlot = slot<PasteItem>()
        val hashSlot = slot<String>()
        coEvery {
            fixture.pasteDao.updatePasteContent(
                pasteData,
                capture(itemSlot),
                any(),
                any(),
                any(),
                capture(hashSlot),
            )
        } returns true

        withCliRouting(fixture) {
            val response =
                client.put("/cli/paste/42") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"content":"new text","expectedHash":"${pasteData.hash}"}""")
                }
            assertEquals(HttpStatusCode.OK, response.status)
            assertContains(response.bodyAsText(), "Paste #42 updated.")
            val updated = itemSlot.captured
            assertTrue(updated is TextPasteItem)
            assertEquals("new text", updated.text)
            // The CAS guard receives the hash the editor saw, verbatim
            assertEquals(pasteData.hash, hashSlot.captured)
        }
    }

    @Test
    fun `paste update refuses with 409 when the CAS guard misses`() {
        val fixture = Fixture()
        coEvery { fixture.pasteDao.getNoDeletePasteData(42L) } returns textPasteData(42L, "old text")
        coEvery {
            fixture.pasteDao.updatePasteContent(any(), any(), any(), any(), any(), any())
        } returns false

        withCliRouting(fixture) {
            val response =
                client.put("/cli/paste/42") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"content":"new text","expectedHash":"stale-hash"}""")
                }
            assertEquals(HttpStatusCode.Conflict, response.status)
            assertContains(response.bodyAsText(), "changed while it was being edited")
        }
    }

    @Test
    fun `paste update keeps html pastes html and re-derives the text flavor`() {
        val fixture = Fixture()
        val item = createHtmlPasteItem(html = "<b>old</b>")
        val textCompanion = createTextPasteItem(text = "old")
        val pasteData =
            PasteData(
                id = 7L,
                appInstanceId = appInfo.appInstanceId,
                pasteAppearItem = item,
                pasteCollection = PasteCollection(listOf(textCompanion)),
                pasteType = PasteType.HTML_TYPE.type,
                source = "Test",
                size = item.size,
                hash = item.hash,
                createTime = 123L,
                pasteState = PasteState.LOADED,
            )
        coEvery { fixture.pasteDao.getNoDeletePasteData(7L) } returns pasteData
        val itemSlot = slot<PasteItem>()
        val collectionSlot = slot<PasteCollection>()
        coEvery {
            fixture.pasteDao.updatePasteContent(
                pasteData,
                capture(itemSlot),
                capture(collectionSlot),
                any(),
                any(),
                any(),
            )
        } returns true

        withCliRouting(fixture) {
            val response =
                client.put("/cli/paste/7") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"content":"<i>new</i>","expectedHash":"${pasteData.hash}"}""")
                }
            assertEquals(HttpStatusCode.OK, response.status)
            val updated = itemSlot.captured
            assertTrue(updated is HtmlPasteItem)
            assertEquals("<i>new</i>", updated.html)
            // The plain-text clipboard flavor must match the edited markup —
            // a stale companion would paste the OLD content into text targets
            val companions = collectionSlot.captured.pasteItems
            assertEquals(1, companions.size)
            val text = companions[0]
            assertTrue(text is TextPasteItem)
            assertEquals("new", text.text)
        }
    }

    @Test
    fun `paste update drops an empty derived text flavor`() {
        val fixture = Fixture()
        val item = createHtmlPasteItem(html = "<b>old</b>")
        val pasteData =
            PasteData(
                id = 8L,
                appInstanceId = appInfo.appInstanceId,
                pasteAppearItem = item,
                pasteCollection = PasteCollection(listOf(createTextPasteItem(text = "old"))),
                pasteType = PasteType.HTML_TYPE.type,
                source = "Test",
                size = item.size,
                hash = item.hash,
                createTime = 123L,
                pasteState = PasteState.LOADED,
            )
        coEvery { fixture.pasteDao.getNoDeletePasteData(8L) } returns pasteData
        val collectionSlot = slot<PasteCollection>()
        coEvery {
            fixture.pasteDao.updatePasteContent(pasteData, any(), capture(collectionSlot), any(), any(), any())
        } returns true

        withCliRouting(fixture) {
            val response =
                client.put("/cli/paste/8") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"content":"<img>","expectedHash":"${pasteData.hash}"}""")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(collectionSlot.captured.pasteItems.isEmpty())
        }
    }

    @Test
    fun `paste update round-trips a color and rejects bad hex`() {
        val fixture = Fixture()
        val item = createColorPasteItem(color = 0x11223344)
        coEvery { fixture.pasteDao.getNoDeletePasteData(5L) } returns
            PasteData(
                id = 5L,
                appInstanceId = appInfo.appInstanceId,
                pasteAppearItem = item,
                pasteCollection = PasteCollection(listOf()),
                pasteType = PasteType.COLOR_TYPE.type,
                source = "Test",
                size = item.size,
                hash = item.hash,
                createTime = 123L,
                pasteState = PasteState.LOADED,
            )
        val itemSlot = slot<PasteItem>()
        coEvery {
            fixture.pasteDao.updatePasteContent(any(), capture(itemSlot), any(), any(), any(), any())
        } returns true

        withCliRouting(fixture) {
            suspend fun update(content: String) =
                client.put("/cli/paste/5") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"content":"$content","expectedHash":"${item.hash}"}""")
                }

            // The CLI shows PasteColor.toHexString(): #RRGGBBAA, alpha last
            assertEquals(HttpStatusCode.OK, update("#0080FFFF").status)
            val updated = itemSlot.captured
            assertTrue(updated is ColorPasteItem)
            assertEquals(0xFF0080FF.toInt(), updated.color)

            val invalid = update("not-a-color")
            assertEquals(HttpStatusCode.BadRequest, invalid.status)
            assertContains(invalid.bodyAsText(), "Invalid color")
        }
    }

    @Test
    fun `paste update on a changed url drops the stale title`() {
        val fixture = Fixture()
        val item =
            createUrlPasteItem(
                url = "https://old.example.com",
                extraInfo = buildJsonObject { put(PasteItemProperties.TITLE, "Old Page Title") },
            )
        coEvery { fixture.pasteDao.getNoDeletePasteData(6L) } returns
            PasteData(
                id = 6L,
                appInstanceId = appInfo.appInstanceId,
                pasteAppearItem = item,
                pasteCollection = PasteCollection(listOf()),
                pasteType = PasteType.URL_TYPE.type,
                source = "Test",
                size = item.size,
                hash = item.hash,
                createTime = 123L,
                pasteState = PasteState.LOADED,
            )
        val itemSlot = slot<PasteItem>()
        coEvery {
            fixture.pasteDao.updatePasteContent(any(), capture(itemSlot), any(), any(), any(), any())
        } returns true

        withCliRouting(fixture) {
            val response =
                client.put("/cli/paste/6") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"content":"https://new.example.com","expectedHash":"${item.hash}"}""")
                }
            assertEquals(HttpStatusCode.OK, response.status)
            val updated = itemSlot.captured
            assertTrue(updated is UrlPasteItem)
            assertEquals("https://new.example.com", updated.url)
            assertEquals(null, updated.getTitle())
            // A changed link re-renders its Open Graph preview
            coVerify { fixture.taskSubmitter.submit(any()) }
        }
    }

    @Test
    fun `paste update stays successful when url preview refresh fails`() {
        val fixture = Fixture()
        val item = createUrlPasteItem(url = "https://old.example.com")
        val pasteData =
            PasteData(
                id = 16L,
                appInstanceId = appInfo.appInstanceId,
                pasteAppearItem = item,
                pasteCollection = PasteCollection(listOf()),
                pasteType = PasteType.URL_TYPE.type,
                source = "Test",
                size = item.size,
                hash = item.hash,
                createTime = 123L,
                pasteState = PasteState.LOADED,
            )
        coEvery { fixture.pasteDao.getNoDeletePasteData(16L) } returns pasteData
        coEvery { fixture.taskSubmitter.submit(any()) } throws IllegalStateException("task database unavailable")

        withCliRouting(fixture) {
            val response =
                client.put("/cli/paste/16") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """{"content":"https://new.example.com","expectedHash":"${pasteData.hash}"}""",
                    )
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertContains(response.bodyAsText(), "Paste #16 updated.")
        }
    }

    @Test
    fun `paste update rejects an invalid url`() {
        val fixture = Fixture()
        val item = createUrlPasteItem(url = "https://old.example.com")
        coEvery { fixture.pasteDao.getNoDeletePasteData(6L) } returns
            PasteData(
                id = 6L,
                appInstanceId = appInfo.appInstanceId,
                pasteAppearItem = item,
                pasteCollection = PasteCollection(listOf()),
                pasteType = PasteType.URL_TYPE.type,
                source = "Test",
                size = item.size,
                hash = item.hash,
                createTime = 123L,
                pasteState = PasteState.LOADED,
            )

        withCliRouting(fixture) {
            val response =
                client.put("/cli/paste/6") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"content":"not a url at all","expectedHash":"${item.hash}"}""")
                }
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertContains(response.bodyAsText(), "not a valid URL")
        }
    }

    @Test
    fun `paste update enforces the non-file size cap`() {
        val fixture = Fixture()
        fixture.currentConfig = DesktopAppConfig(language = "en", maxNonFilePasteSize = 0L)
        coEvery { fixture.pasteDao.getNoDeletePasteData(42L) } returns textPasteData(42L, "old")

        withCliRouting(fixture) {
            val response =
                client.put("/cli/paste/42") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"content":"too big for a zero cap","expectedHash":"x"}""")
                }
            assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
            assertContains(response.bodyAsText(), "maxNonFilePasteSize")
        }
    }

    @Test
    fun `paste update validates content, id and type`() {
        val fixture = Fixture()
        coEvery { fixture.pasteDao.getNoDeletePasteData(404L) } returns null
        val imagesItem =
            createImagesPasteItem(
                basePath = "/tmp/images-store",
                relativePathList = listOf("shot.png"),
                fileInfoTreeMap = mapOf("shot.png" to SingleFileInfoTree(size = 10L, hash = "img")),
            )
        coEvery { fixture.pasteDao.getNoDeletePasteData(9L) } returns
            PasteData(
                id = 9L,
                appInstanceId = appInfo.appInstanceId,
                pasteAppearItem = imagesItem,
                pasteCollection = PasteCollection(listOf()),
                pasteType = PasteType.IMAGE_TYPE.type,
                source = "Test",
                size = imagesItem.size,
                hash = imagesItem.hash,
                createTime = 123L,
                pasteState = PasteState.LOADED,
            )
        coEvery { fixture.pasteDao.getNoDeletePasteData(11L) } returns
            textPasteData(11L, "loading", pasteState = PasteState.LOADING)

        withCliRouting(fixture) {
            suspend fun update(
                id: Long,
                content: String,
            ) = client.put("/cli/paste/$id") {
                contentType(ContentType.Application.Json)
                setBody("""{"content":"$content","expectedHash":"x"}""")
            }

            assertEquals(HttpStatusCode.BadRequest, update(42L, "").status)
            assertEquals(HttpStatusCode.NotFound, update(404L, "x").status)
            val notEditable = update(9L, "x")
            assertEquals(HttpStatusCode.BadRequest, notEditable.status)
            assertContains(notEditable.bodyAsText(), "cannot be edited as text")
            assertEquals(HttpStatusCode.Conflict, update(11L, "x").status)
        }
    }

    /**
     * Reads the next non-blank (non-heartbeat) event line, or null when
     * [timeout] elapses first. The watch response never completes on its own
     * (the handler resubscribes to the DAO flow), so tests read the body
     * incrementally instead of waiting for it to end.
     */
    private suspend fun readEventLine(
        channel: ByteReadChannel,
        timeout: Duration = 2.seconds,
    ): String? =
        withTimeoutOrNull(timeout) {
            var line = channel.readLine()
            while (line != null && line.isEmpty()) {
                line = channel.readLine()
            }
            line
        }

    @Test
    fun `watch streams arrivals after the baseline as ndjson lines`() {
        val fixture = Fixture()
        val baseline = listOf(textPasteData(1L, "old", createTime = 100L))
        val arrival = textPasteData(2L, "fresh", createTime = 200L)
        every { fixture.pasteDao.getPasteDataFlow(any()) } returns
            flowOf(baseline, listOf(arrival) + baseline)

        withCliRouting(fixture) {
            client.prepareGet("/cli/watch").execute { response ->
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals(
                    "application/x-ndjson",
                    response.contentType()?.let { "${it.contentType}/${it.contentSubtype}" },
                )
                val channel = response.bodyAsChannel()
                val line = readEventLine(channel)
                assertTrue(line != null, "expected an arrival event line")
                val dto = json.decodeFromString<CliPasteSummaryDto>(line)
                assertEquals(2L, dto.id)
                assertEquals("fresh", dto.preview)
                // The tracker survives DAO flow resubscription, so replaying
                // the same snapshots must not produce duplicate events
                assertEquals(null, readEventLine(channel, timeout = 300.milliseconds))
            }
        }
    }

    @Test
    fun `watch never builds its baseline from a masked query failure`() {
        val fixture = Fixture()
        // The DAO flow masks a failed query as an empty snapshot and completes;
        // the history itself has rows, so that snapshot must not seed the
        // baseline — otherwise the recovered window would replay as arrivals
        coEvery { fixture.pasteDao.getActiveCount() } returns 2L
        val history =
            listOf(
                textPasteData(2L, "history-b", createTime = 200L),
                textPasteData(1L, "history-a", createTime = 100L),
            )
        val arrival = textPasteData(3L, "fresh", createTime = 300L)
        every { fixture.pasteDao.getPasteDataFlow(any()) } returnsMany
            listOf(
                flowOf(listOf()),
                flowOf(history, listOf(arrival) + history),
            )

        withCliRouting(fixture) {
            client.prepareGet("/cli/watch").execute { response ->
                val channel = response.bodyAsChannel()
                // Only the post-recovery arrival may appear, never the history
                val line = readEventLine(channel, timeout = 5.seconds)
                assertTrue(line != null, "expected the post-recovery arrival")
                assertEquals(3L, json.decodeFromString<CliPasteSummaryDto>(line).id)
                assertEquals(null, readEventLine(channel, timeout = 300.milliseconds))
            }
        }
    }

    @Test
    fun `watch accepts an empty baseline when the history really is empty`() {
        val fixture = Fixture()
        val arrival = textPasteData(1L, "first-ever", createTime = 100L)
        every { fixture.pasteDao.getPasteDataFlow(any()) } returns
            flowOf(listOf(), listOf(arrival))

        withCliRouting(fixture) {
            client.prepareGet("/cli/watch").execute { response ->
                val line = readEventLine(response.bodyAsChannel())
                assertTrue(line != null, "expected the first-ever paste as an arrival")
                assertEquals(1L, json.decodeFromString<CliPasteSummaryDto>(line).id)
            }
        }
        // The count must be sampled BEFORE the snapshot subscription: sampled
        // inside the collect it would race with a paste created right after an
        // honestly empty first snapshot (count > 0 → snapshot misread as a
        // masked failure → that first paste swallowed as the baseline)
        coVerifyOrder {
            fixture.pasteDao.getActiveCount()
            fixture.pasteDao.getPasteDataFlow(any())
        }
    }

    @Test
    fun `watch type filter drops non-matching arrivals`() {
        val fixture = Fixture()
        val baseline = listOf(textPasteData(1L, "old", createTime = 100L))
        val arrival = textPasteData(2L, "fresh", createTime = 200L)
        every { fixture.pasteDao.getPasteDataFlow(any()) } returns
            flowOf(baseline, listOf(arrival) + baseline)

        withCliRouting(fixture) {
            client.prepareGet("/cli/watch?type=link").execute { response ->
                assertEquals(null, readEventLine(response.bodyAsChannel(), timeout = 300.milliseconds))
            }
        }
    }

    @Test
    fun `watch rejects unknown type and tag before streaming`() {
        val fixture = Fixture()

        withCliRouting(fixture) {
            val badType = client.get("/cli/watch?type=bogus")
            assertEquals(HttpStatusCode.BadRequest, badType.status)
            assertContains(badType.bodyAsText(), "Unknown paste type")

            val badTag = client.get("/cli/watch?tag=missing")
            assertEquals(HttpStatusCode.BadRequest, badTag.status)
            assertContains(badTag.bodyAsText(), "Unknown tag")
        }
    }

    @Test
    fun `watch tag filter only passes tagged arrivals`() {
        val fixture = Fixture()
        val baseline = listOf(textPasteData(1L, "old", createTime = 100L))
        val tagged = textPasteData(2L, "tagged", createTime = 200L)
        val untagged = textPasteData(3L, "untagged", createTime = 300L)
        every { fixture.pasteDao.getPasteDataFlow(any()) } returns
            flowOf(baseline, listOf(untagged, tagged) + baseline)
        every { fixture.pasteTagDao.getAllTagsBlock() } returns
            listOf(PasteTag(id = 9L, name = "work", color = 0L, sortOrder = 0L))
        every { fixture.pasteTagDao.getPasteTagsBlock(2L) } returns listOf(9L)
        every { fixture.pasteTagDao.getPasteTagsBlock(3L) } returns listOf()

        withCliRouting(fixture) {
            client.prepareGet("/cli/watch?tag=work").execute { response ->
                val channel = response.bodyAsChannel()
                val line = readEventLine(channel)
                assertTrue(line != null, "expected the tagged arrival")
                assertEquals(2L, json.decodeFromString<CliPasteSummaryDto>(line).id)
                // The untagged arrival must have been filtered out
                assertEquals(null, readEventLine(channel, timeout = 300.milliseconds))
            }
        }
    }
}
