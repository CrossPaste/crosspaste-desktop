package com.crosspaste.mcp

import com.crosspaste.app.AppInfo
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.db.TestDriverFactory
import com.crosspaste.db.createDatabase
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.paste.CurrentPaste
import com.crosspaste.paste.PasteCollection
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteState
import com.crosspaste.paste.PasteType
import com.crosspaste.paste.SearchContentService
import com.crosspaste.paste.item.CreatePasteItemHelper.createTextPasteItem
import com.crosspaste.paste.item.CreatePasteItemHelper.createUrlPasteItem
import com.crosspaste.paste.plugin.type.DesktopTextTypePlugin
import com.crosspaste.paste.plugin.type.DesktopUrlTypePlugin
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.task.TaskSubmitter
import com.crosspaste.utils.DateUtils
import com.crosspaste.utils.getJsonUtils
import io.mockk.mockk
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class McpResourceProviderTest {

    @Suppress("unused")
    private val jsonUtils = getJsonUtils()

    private val appInfo =
        AppInfo(
            appInstanceId = "test-instance",
            appVersion = "1.0.0",
            appRevision = "abc",
            userName = "testUser",
        )

    private val searchContentService =
        object : SearchContentService {
            override fun createSearchContent(
                source: String?,
                searchContentList: List<String>,
            ): String =
                (listOfNotNull(source?.lowercase()) + searchContentList.map { it.lowercase() }).joinToString(" ")

            override fun createSearchTerms(queryString: String): List<String> =
                queryString.trim().split("\\s+".toRegex()).filter {
                    it.isNotBlank()
                }
        }

    private val commonConfigManager: CommonConfigManager = mockk(relaxed = true)
    private val currentPaste: CurrentPaste = mockk(relaxed = true)
    private val taskSubmitter: TaskSubmitter = mockk(relaxed = true)
    private val userDataPathProvider: UserDataPathProvider = mockk(relaxed = true)

    private val database = createDatabase(TestDriverFactory())

    private val pasteDao =
        PasteDao(
            appInfo = appInfo,
            commonConfigManager = commonConfigManager,
            currentPaste = currentPaste,
            database = database,
            pasteProcessPlugins = listOf(),
            searchContentService = searchContentService,
            taskSubmitter = taskSubmitter,
            userDataPathProvider = userDataPathProvider,
        )

    private val provider = McpResourceProvider(pasteDao)

    private fun createServer(): Server {
        val server =
            Server(
                serverInfo = Implementation(name = "test-server", version = "1.0.0"),
                options =
                    ServerOptions(
                        capabilities =
                            ServerCapabilities(
                                resources =
                                    ServerCapabilities.Resources(
                                        subscribe = false,
                                        listChanged = false,
                                    ),
                            ),
                    ),
            )
        provider.registerResources(server)
        return server
    }

    private suspend fun readResource(
        server: Server,
        uri: String,
    ): String {
        val handler = server.resources[uri]?.readHandler ?: error("Resource '$uri' not found")
        val request = ReadResourceRequest(ReadResourceRequestParams(uri = uri))
        val result = handler(request)
        return (result.contents.first() as TextResourceContents).text
    }

    private suspend fun insertTestPasteData(
        text: String = "hello world",
        source: String? = null,
        createTime: Long = DateUtils.nowEpochMilliseconds(),
    ): Long {
        val textItem =
            createTextPasteItem(
                identifiers = listOf(DesktopTextTypePlugin.TEXT),
                text = text,
            )
        val pasteData =
            PasteData(
                appInstanceId = "test-instance",
                pasteAppearItem = textItem,
                pasteCollection = PasteCollection(listOf()),
                pasteType = PasteType.TEXT_TYPE.type,
                source = source,
                size = textItem.size,
                hash = textItem.hash,
                pasteState = PasteState.LOADED,
                createTime = createTime,
            )
        return pasteDao.createPasteData(pasteData)
    }

    private suspend fun insertUrlPasteData(url: String): Long {
        val urlItem =
            createUrlPasteItem(
                identifiers = listOf(DesktopUrlTypePlugin.URL),
                url = url,
            )
        val pasteData =
            PasteData(
                appInstanceId = "test-instance",
                pasteAppearItem = urlItem,
                pasteCollection = PasteCollection(listOf()),
                pasteType = PasteType.URL_TYPE.type,
                source = "test",
                size = urlItem.size,
                hash = urlItem.hash,
                pasteState = PasteState.LOADED,
                createTime = DateUtils.nowEpochMilliseconds(),
            )
        return pasteDao.createPasteData(pasteData)
    }

    // ========== clipboard://latest ==========

    @Test
    fun `latest resource returns no items message when empty`() =
        runTest {
            val server = createServer()
            val result = readResource(server, "clipboard://latest")
            assertTrue(result.contains("No clipboard items available"))
        }

    @Test
    fun `latest resource returns most recent item`() =
        runTest {
            val server = createServer()
            val now = DateUtils.nowEpochMilliseconds()
            insertTestPasteData("first item", createTime = now)
            insertTestPasteData("latest item", createTime = now + 1000)
            val result = readResource(server, "clipboard://latest")
            assertTrue(result.contains("latest item"))
        }

    @Test
    fun `latest resource shows type and source`() =
        runTest {
            val server = createServer()
            insertTestPasteData("typed content", source = "Chrome")
            val result = readResource(server, "clipboard://latest")
            assertTrue(result.contains("Type: text"))
            assertTrue(result.contains("Source: Chrome"))
        }

    @Test
    fun `latest resource shows url content`() =
        runTest {
            val server = createServer()
            insertUrlPasteData("https://example.com")
            val result = readResource(server, "clipboard://latest")
            assertTrue(result.contains("https://example.com"))
        }

    // ========== clipboard://stats ==========

    @Test
    fun `stats resource returns zero counts when empty`() =
        runTest {
            val server = createServer()
            val result = readResource(server, "clipboard://stats")
            assertTrue(result.contains("\"totalCount\": 0"))
        }

    @Test
    fun `stats resource reflects inserted items`() =
        runTest {
            val server = createServer()
            insertTestPasteData("item1")
            insertTestPasteData("item2")
            val result = readResource(server, "clipboard://stats")
            assertTrue(result.contains("\"totalCount\": 2"))
        }

    @Test
    fun `stats resource shows correct type counts`() =
        runTest {
            val server = createServer()
            insertTestPasteData("text1")
            insertUrlPasteData("https://example.com")
            val result = readResource(server, "clipboard://stats")
            assertTrue(result.contains("\"totalCount\": 2"))
        }
}
