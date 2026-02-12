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
import com.crosspaste.paste.plugin.type.DesktopTextTypePlugin
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.task.TaskSubmitter
import com.crosspaste.utils.DateUtils
import com.crosspaste.utils.getJsonUtils
import io.mockk.mockk
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertTrue

class McpToolProviderTest {

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

    private val provider =
        McpToolProvider(
            appInfo = appInfo,
            pasteDao = pasteDao,
            searchContentService = searchContentService,
        )

    private fun createServer(): Server {
        val server =
            Server(
                serverInfo = Implementation(name = "test-server", version = "1.0.0"),
                options =
                    ServerOptions(
                        capabilities =
                            ServerCapabilities(
                                tools = ServerCapabilities.Tools(listChanged = false),
                            ),
                    ),
            )
        provider.registerTools(server)
        return server
    }

    private suspend fun callTool(
        server: Server,
        name: String,
        args: Map<String, Any?> = emptyMap(),
    ): String {
        val jsonArgs =
            buildJsonObject {
                args.forEach { (k, v) ->
                    when (v) {
                        is String -> put(k, v)
                        is Int -> put(k, v)
                        is Long -> put(k, v)
                        is Boolean -> put(k, v)
                        else -> put(k, v.toString())
                    }
                }
            }
        val handler = server.tools[name]?.handler ?: error("Tool '$name' not found")
        val request = CallToolRequest(CallToolRequestParams(name = name, arguments = jsonArgs))
        val result = handler(request)
        return (result.content.first() as TextContent).text
    }

    private suspend fun insertTestPasteData(
        text: String = "hello world",
        source: String? = null,
        favorite: Boolean = false,
    ): Long {
        val textItem =
            createTextPasteItem(
                identifiers = listOf(DesktopTextTypePlugin.TEXT),
                text = text,
            )
        val pasteData =
            PasteData(
                appInstanceId = "test-instance",
                favorite = favorite,
                pasteAppearItem = textItem,
                pasteCollection = PasteCollection(listOf()),
                pasteType = PasteType.TEXT_TYPE.type,
                source = source,
                size = textItem.size,
                hash = textItem.hash,
                pasteState = PasteState.LOADED,
                createTime = DateUtils.nowEpochMilliseconds(),
            )
        return pasteDao.createPasteData(pasteData)
    }

    // ========== search_clipboard ==========

    @Test
    fun `search_clipboard returns no items when empty`() =
        runTest {
            val server = createServer()
            val result = callTool(server, "search_clipboard")
            assertTrue(result.contains("No clipboard items found"))
        }

    @Test
    fun `search_clipboard returns items after insertion`() =
        runTest {
            val server = createServer()
            insertTestPasteData("test content")
            val result = callTool(server, "search_clipboard")
            assertTrue(result.contains("Found 1 clipboard item"))
            assertTrue(result.contains("text"))
        }

    @Test
    fun `search_clipboard filters by query`() =
        runTest {
            val server = createServer()
            insertTestPasteData("apple pie")
            insertTestPasteData("banana split")
            val result = callTool(server, "search_clipboard", mapOf("query" to "apple"))
            assertTrue(result.contains("Found 1 clipboard item"))
            assertTrue(result.contains("apple"))
        }

    @Test
    fun `search_clipboard respects limit`() =
        runTest {
            val server = createServer()
            repeat(5) { insertTestPasteData("item $it") }
            val result = callTool(server, "search_clipboard", mapOf("limit" to 2))
            assertTrue(result.contains("Found 2 clipboard item"))
        }

    @Test
    fun `search_clipboard filters by favorite`() =
        runTest {
            val server = createServer()
            insertTestPasteData("normal item", favorite = false)
            insertTestPasteData("favorite item", favorite = true)
            val result = callTool(server, "search_clipboard", mapOf("favorite" to true))
            assertTrue(result.contains("Found 1 clipboard item"))
            assertTrue(result.contains("Favorite: true"))
        }

    // ========== get_paste_item ==========

    @Test
    fun `get_paste_item returns error for missing id`() =
        runTest {
            val server = createServer()
            val result = callTool(server, "get_paste_item")
            assertTrue(result.contains("Error"))
        }

    @Test
    fun `get_paste_item returns error for non-existent id`() =
        runTest {
            val server = createServer()
            val result = callTool(server, "get_paste_item", mapOf("id" to 99999))
            assertTrue(result.contains("No paste item found"))
        }

    @Test
    fun `get_paste_item returns content for valid id`() =
        runTest {
            val server = createServer()
            val id = insertTestPasteData("hello mcp")
            val result = callTool(server, "get_paste_item", mapOf("id" to id))
            assertTrue(result.contains("Paste Item #$id"))
            assertTrue(result.contains("hello mcp"))
            assertTrue(result.contains("text"))
        }

    // ========== get_clipboard_stats ==========

    @Test
    fun `get_clipboard_stats returns zero counts when empty`() =
        runTest {
            val server = createServer()
            val result = callTool(server, "get_clipboard_stats")
            assertTrue(result.contains("Clipboard Statistics"))
            assertTrue(result.contains("Total: 0 items"))
        }

    @Test
    fun `get_clipboard_stats reflects inserted items`() =
        runTest {
            val server = createServer()
            insertTestPasteData("first")
            insertTestPasteData("second")
            val result = callTool(server, "get_clipboard_stats")
            assertTrue(result.contains("Total: 2 items"))
        }

    // ========== list_tags ==========

    @Test
    fun `list_tags returns no tags when empty`() =
        runTest {
            val server = createServer()
            val result = callTool(server, "list_tags")
            assertTrue(result.contains("No tags found"))
        }

    // ========== add_to_clipboard ==========

    @Test
    fun `add_to_clipboard creates text item`() =
        runTest {
            val server = createServer()
            val result = callTool(server, "add_to_clipboard", mapOf("content" to "new text"))
            assertTrue(result.contains("Successfully added"))
            assertTrue(result.contains("type: text"))

            // Verify it's in the database
            val searchResult = callTool(server, "search_clipboard")
            assertTrue(searchResult.contains("new text"))
        }

    @Test
    fun `add_to_clipboard creates url item`() =
        runTest {
            val server = createServer()
            val result =
                callTool(
                    server,
                    "add_to_clipboard",
                    mapOf("content" to "https://example.com", "type" to "url"),
                )
            assertTrue(result.contains("Successfully added"))
            assertTrue(result.contains("type: url"))
        }

    @Test
    fun `add_to_clipboard returns error for blank content`() =
        runTest {
            val server = createServer()
            val result = callTool(server, "add_to_clipboard", mapOf("content" to ""))
            assertTrue(result.contains("Error"))
        }

    @Test
    fun `add_to_clipboard returns error for missing content`() =
        runTest {
            val server = createServer()
            val result = callTool(server, "add_to_clipboard")
            assertTrue(result.contains("Error"))
        }

    @Test
    fun `add_to_clipboard sets source as MCP`() =
        runTest {
            val server = createServer()
            callTool(server, "add_to_clipboard", mapOf("content" to "mcp content"))

            val searchResult = callTool(server, "search_clipboard")
            assertTrue(searchResult.contains("Source: MCP"))
        }

    @Test
    fun `add_to_clipboard text content can be retrieved by get_paste_item`() =
        runTest {
            val server = createServer()
            val addResult = callTool(server, "add_to_clipboard", mapOf("content" to "retrievable text"))
            val id = addResult.substringAfter("ID: ").substringBefore(" ").toLong()

            val getResult = callTool(server, "get_paste_item", mapOf("id" to id))
            assertTrue(getResult.contains("retrievable text"))
            assertTrue(getResult.contains("Source: MCP"))
        }

    @Test
    fun `add_to_clipboard defaults to text type`() =
        runTest {
            val server = createServer()
            val result = callTool(server, "add_to_clipboard", mapOf("content" to "no type specified"))
            assertTrue(result.contains("type: text"))
        }

    // ========== add_to_clipboard - html ==========

    @Test
    fun `add_to_clipboard creates html item`() =
        runTest {
            val server = createServer()
            val result =
                callTool(
                    server,
                    "add_to_clipboard",
                    mapOf("content" to "<html><body><p>Hello</p></body></html>", "type" to "html"),
                )
            assertTrue(result.contains("Successfully added"))
            assertTrue(result.contains("type: html"))
        }

    @Test
    fun `add_to_clipboard rejects invalid html`() =
        runTest {
            val server = createServer()
            val result = callTool(server, "add_to_clipboard", mapOf("content" to "   ", "type" to "html"))
            assertTrue(result.contains("Error"))
        }

    // ========== add_to_clipboard - rtf ==========

    @Test
    fun `add_to_clipboard creates rtf item`() =
        runTest {
            val server = createServer()
            val result =
                callTool(
                    server,
                    "add_to_clipboard",
                    mapOf("content" to "{\\rtf1 Hello RTF}", "type" to "rtf"),
                )
            assertTrue(result.contains("Successfully added"))
            assertTrue(result.contains("type: rtf"))
        }

    @Test
    fun `add_to_clipboard rejects invalid rtf`() =
        runTest {
            val server = createServer()
            val result = callTool(server, "add_to_clipboard", mapOf("content" to "not rtf content", "type" to "rtf"))
            assertTrue(result.contains("Error"))
            assertTrue(result.contains("must start with"))
        }

    // ========== add_to_clipboard - color ==========

    @Test
    fun `add_to_clipboard creates color item from hex`() =
        runTest {
            val server = createServer()
            val result = callTool(server, "add_to_clipboard", mapOf("content" to "#FF0000", "type" to "color"))
            assertTrue(result.contains("Successfully added"))
            assertTrue(result.contains("type: color"))
        }

    @Test
    fun `add_to_clipboard creates color item from rgb`() =
        runTest {
            val server = createServer()
            val result = callTool(server, "add_to_clipboard", mapOf("content" to "rgb(0,128,255)", "type" to "color"))
            assertTrue(result.contains("Successfully added"))
        }

    @Test
    fun `add_to_clipboard creates color item from css name`() =
        runTest {
            val server = createServer()
            val result = callTool(server, "add_to_clipboard", mapOf("content" to "red", "type" to "color"))
            assertTrue(result.contains("Successfully added"))
        }

    @Test
    fun `add_to_clipboard rejects invalid color`() =
        runTest {
            val server = createServer()
            val result = callTool(server, "add_to_clipboard", mapOf("content" to "not-a-color", "type" to "color"))
            assertTrue(result.contains("Error"))
            assertTrue(result.contains("Invalid color"))
        }

    // ========== add_to_clipboard - file ==========

    @Test
    fun `add_to_clipboard rejects non-existent file`() =
        runTest {
            val server = createServer()
            val result =
                callTool(server, "add_to_clipboard", mapOf("content" to "/nonexistent/path/file.txt", "type" to "file"))
            assertTrue(result.contains("Error"))
            assertTrue(result.contains("File not found"))
        }

    @Test
    fun `add_to_clipboard creates file item for existing file`() =
        runTest {
            val server = createServer()
            // Use build.gradle.kts as an existing file
            val result =
                callTool(
                    server,
                    "add_to_clipboard",
                    mapOf("content" to "build.gradle.kts", "type" to "file"),
                )
            assertTrue(result.contains("Successfully added"))
            assertTrue(result.contains("type: file"))
        }

    // ========== add_to_clipboard - image ==========

    @Test
    fun `add_to_clipboard rejects non-existent image file`() =
        runTest {
            val server = createServer()
            val result =
                callTool(
                    server,
                    "add_to_clipboard",
                    mapOf("content" to "/nonexistent/path/photo.png", "type" to "image"),
                )
            assertTrue(result.contains("Error"))
            assertTrue(result.contains("Image file not found"))
        }

    @Test
    fun `add_to_clipboard rejects unsupported image format`() =
        runTest {
            val server = createServer()
            val result =
                callTool(
                    server,
                    "add_to_clipboard",
                    mapOf("content" to "/some/path/file.txt", "type" to "image"),
                )
            assertTrue(result.contains("Error"))
            assertTrue(result.contains("Unsupported image format"))
        }
}
