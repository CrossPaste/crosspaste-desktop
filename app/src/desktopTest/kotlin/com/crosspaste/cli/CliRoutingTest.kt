package com.crosspaste.cli

import com.crosspaste.app.AppInfo
import com.crosspaste.config.DesktopAppConfig
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.db.paste.PasteTagDao
import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.db.sync.SyncRuntimeInfoDao
import com.crosspaste.paste.PasteCollection
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteDataHelper
import com.crosspaste.paste.PasteReleaseService
import com.crosspaste.paste.PasteState
import com.crosspaste.paste.PasteTag
import com.crosspaste.paste.PasteType
import com.crosspaste.paste.PasteboardService
import com.crosspaste.paste.SearchContentService
import com.crosspaste.paste.item.CreatePasteItemHelper.createTextPasteItem
import com.crosspaste.paste.item.DefaultPasteItemReader
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.platform.Platform
import com.crosspaste.utils.getJsonUtils
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
        val configManager = mockk<DesktopConfigManager>()
        val pasteboardService = mockk<PasteboardService>()
        val pasteDao = mockk<PasteDao>()
        val pasteReleaseService = mockk<PasteReleaseService>()
        val pasteTagDao = mockk<PasteTagDao>()
        val searchContentService = mockk<SearchContentService>()
        val syncRuntimeInfoDao = mockk<SyncRuntimeInfoDao>()
        val pasteDataHelper = PasteDataHelper(DefaultPasteItemReader())

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
            coEvery { pasteDao.getActiveCount() } returns 0L
            every { pasteTagDao.getAllTagsBlock() } returns listOf()
            every { pasteTagDao.getPasteTagsBlock(any()) } returns listOf()
        }
    }

    private fun withCliRouting(
        fixture: Fixture,
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        application {
            cliTestModule(fixture)
        }
        block()
    }

    private fun Application.cliTestModule(fixture: Fixture) {
        install(ContentNegotiation) {
            json(Json { encodeDefaults = true })
        }
        routing {
            cliRouting(
                appInfo = appInfo,
                configManager = fixture.configManager,
                pasteboardService = fixture.pasteboardService,
                pasteDao = fixture.pasteDao,
                pasteDataHelper = fixture.pasteDataHelper,
                pasteItemReader = DefaultPasteItemReader(),
                pasteReleaseService = fixture.pasteReleaseService,
                pasteTagDao = fixture.pasteTagDao,
                searchContentService = fixture.searchContentService,
                syncRuntimeInfoDao = fixture.syncRuntimeInfoDao,
            )
        }
    }

    private fun textPasteData(
        id: Long,
        text: String,
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
            createTime = 123L,
            pasteState = PasteState.LOADED,
        )
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
        }

        coEvery { fixture.pasteDao.getLatestLoadedPasteData() } returns null
        withCliRouting(fixture) {
            assertEquals(HttpStatusCode.NotFound, client.get("/cli/paste/latest").status)
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
    fun `history resolves type and tag filters`() {
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
            val response = client.get("/cli/history?limit=5&type=text&tag=work")
            assertEquals(HttpStatusCode.OK, response.status)
            val list = json.decodeFromString<CliPasteListDto>(response.bodyAsText())
            assertEquals(1, list.items.size)
            assertEquals(10L, list.total)
            assertEquals(listOf(PasteType.TEXT_TYPE.type), typeSlot.captured)
            assertEquals(5L, tagSlot.captured)
        }
    }

    @Test
    fun `history rejects unknown type and tag`() {
        val fixture = Fixture()
        withCliRouting(fixture) {
            assertEquals(HttpStatusCode.BadRequest, client.get("/cli/history?type=nope").status)
            assertEquals(HttpStatusCode.BadRequest, client.get("/cli/history?tag=nope").status)
        }
    }

    @Test
    fun `search uses search terms from the query`() {
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
        } returns listOf()

        withCliRouting(fixture) {
            val response = client.get("/cli/search?q=hello%20world")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(listOf("hello", "world"), termsSlot.captured)
            val list = json.decodeFromString<CliPasteListDto>(response.bodyAsText())
            assertEquals(0L, list.total)
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
            assertEquals(HttpStatusCode.BadRequest, putConfig("""{"key":"port","value":"13130"}"""))
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
}
