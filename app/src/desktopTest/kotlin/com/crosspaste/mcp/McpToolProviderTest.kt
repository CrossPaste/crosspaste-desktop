package com.crosspaste.mcp

import com.crosspaste.app.AppInfo
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.db.TestDriverFactory
import com.crosspaste.db.createDatabase
import com.crosspaste.db.paste.SqlPasteDao
import com.crosspaste.db.paste.SqlPasteTagDao
import com.crosspaste.paste.PasteCollection
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteDataHelper
import com.crosspaste.paste.PasteState
import com.crosspaste.paste.PasteType
import com.crosspaste.paste.SearchContentService
import com.crosspaste.paste.item.CreatePasteItemHelper.createImagesPasteItem
import com.crosspaste.paste.item.CreatePasteItemHelper.createTextPasteItem
import com.crosspaste.paste.item.DefaultPasteItemReader
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.paste.item.hasExistingFiles
import com.crosspaste.paste.plugin.type.DesktopImageTypePlugin
import com.crosspaste.paste.plugin.type.DesktopTextTypePlugin
import com.crosspaste.path.PlatformUserDataPathProvider
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.task.TaskSubmitter
import com.crosspaste.utils.DateUtils
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.getJsonUtils
import io.mockk.mockk
import io.modelcontextprotocol.kotlin.sdk.server.ClientConnection
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okio.Path.Companion.toOkioPath
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
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

    private val taskSubmitter: TaskSubmitter = mockk(relaxed = true)
    private val userDataPathProvider: UserDataPathProvider = mockk(relaxed = true)

    private val database = createDatabase(TestDriverFactory())

    private val pasteItemReader = DefaultPasteItemReader()

    private val pasteDao =
        SqlPasteDao(
            appInfo = appInfo,
            database = database,
            pasteItemReader = pasteItemReader,
            searchContentService = searchContentService,
            taskSubmitter = taskSubmitter,
            userDataPathProvider = userDataPathProvider,
        )

    private val pasteTagDao = SqlPasteTagDao(database)

    private val pasteDataHelper = PasteDataHelper(pasteItemReader)

    private val pasteItemFormatter =
        McpPasteItemFormatter(
            pasteDao = pasteDao,
            pasteItemReader = pasteItemReader,
            userDataPathProvider =
                UserDataPathProvider(
                    mockk<CommonConfigManager>(relaxed = true),
                    mockk<PlatformUserDataPathProvider>(relaxed = true),
                ),
        )

    private val provider =
        McpToolProvider(
            appInfo = appInfo,
            pasteDao = pasteDao,
            pasteDataHelper = pasteDataHelper,
            pasteItemFormatter = pasteItemFormatter,
            pasteTagDao = pasteTagDao,
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
    ): String = (callToolResult(server, name, args).content.first() as TextContent).text

    private suspend fun callToolResult(
        server: Server,
        name: String,
        args: Map<String, Any?> = emptyMap(),
    ): CallToolResult =
        callToolWithJson(
            server,
            name,
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
            },
        )

    private suspend fun callToolWithJson(
        server: Server,
        name: String,
        jsonArgs: JsonObject,
    ): CallToolResult {
        val handler = server.tools[name]?.handler ?: error("Tool '$name' not found")
        val request = CallToolRequest(CallToolRequestParams(name = name, arguments = jsonArgs))
        val mockConnection = mockk<ClientConnection>()
        return handler(mockConnection, request)
    }

    private suspend fun insertTestPasteData(
        text: String = "hello world",
        source: String? = null,
    ): Long {
        val textItem =
            createTextPasteItem(
                identifiers = listOf(DesktopTextTypePlugin.TEXT),
                text = text,
            )
        val pasteData =
            PasteData(
                appInstanceId = "test-instance",
                favorite = false,
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

    private suspend fun insertLoadingPlaceholder(): Long =
        pasteDao.createPasteData(
            PasteData(
                appInstanceId = "test-instance",
                pasteCollection = PasteCollection(listOf()),
                pasteType = PasteType.INVALID_TYPE.type,
                size = 0L,
                hash = "",
                pasteState = PasteState.LOADING,
            ),
        )

    /** An image item referencing [files] in place, like a clipboard image too large to copy. */
    private suspend fun insertImagesPasteData(files: List<java.nio.file.Path>): Long {
        val dir = files.first().parent.toOkioPath()
        val names = files.map { it.fileName.toString() }
        val item =
            createImagesPasteItem(
                identifiers = listOf(DesktopImageTypePlugin.IMAGE),
                basePath = dir.toString(),
                relativePathList = names,
                fileInfoTreeMap = names.associateWith { getFileUtils().getFileInfoTree(dir / it) },
            )
        return pasteDao.createPasteData(
            PasteData(
                appInstanceId = "test-instance",
                pasteAppearItem = item,
                pasteCollection = PasteCollection(listOf()),
                pasteType = PasteType.IMAGE_TYPE.type,
                size = item.size,
                hash = item.hash,
                pasteState = PasteState.LOADED,
            ),
        )
    }

    private fun writePng(
        dir: java.nio.file.Path,
        name: String,
        type: Int = BufferedImage.TYPE_INT_RGB,
    ): java.nio.file.Path {
        val file = dir.resolve(name)
        ImageIO.write(BufferedImage(40, 20, type), "png", file.toFile())
        return file
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
    fun `search_clipboard filters by tag`() =
        runTest {
            val server = createServer()
            insertTestPasteData("normal item")
            val taggedId = insertTestPasteData("tagged item")
            val tagId = pasteTagDao.createPasteTag("Favorite", 0xFF007AFF)
            pasteTagDao.switchPinPasteTagBlock(taggedId, tagId)
            val result = callTool(server, "search_clipboard", mapOf("tag" to "Favorite"))
            assertTrue(result.contains("Found 1 clipboard item"))
        }

    @Test
    fun `search_clipboard returns error for unknown tag`() =
        runTest {
            val server = createServer()
            insertTestPasteData("some item")
            val result = callTool(server, "search_clipboard", mapOf("tag" to "Nope"))
            assertTrue(result.contains("Error: no tag named 'Nope'"))
        }

    @Test
    fun `search_clipboard returns error for unknown type`() =
        runTest {
            val server = createServer()
            insertTestPasteData("some item")
            val result = callTool(server, "search_clipboard", mapOf("type" to "video"))
            assertTrue(result.contains("Error: unknown type 'video'"))
            assertTrue(result.contains("link"))
        }

    // ========== get_paste_item ==========

    @Test
    fun `get_paste_item without id returns the most recent item`() =
        runTest {
            val server = createServer()
            insertTestPasteData("older copy")
            val latestId = insertTestPasteData("just copied")
            val result = callTool(server, "get_paste_item")
            assertTrue(result.contains("Paste Item #$latestId"), result)
            assertTrue(result.contains("just copied"), result)
            assertFalse(result.contains("older copy"), result)
        }

    @Test
    fun `get_paste_item without id on empty history is not an error`() =
        runTest {
            val server = createServer()
            val result = callToolResult(server, "get_paste_item")
            assertFalse(result.isError == true)
            assertEquals("No clipboard items found.", (result.content.first() as TextContent).text)
        }

    @Test
    fun `get_paste_item returns error for non-numeric id`() =
        runTest {
            val server = createServer()
            insertTestPasteData("some item")
            val result = callToolResult(server, "get_paste_item", mapOf("id" to "abc"))
            assertTrue(result.isError == true)
        }

    @Test
    fun `get_paste_item returns an opaque image item as downscaled jpeg content with its absolute path`() =
        runTest {
            val file = Files.createTempFile("mcp-get", ".png")
            ImageIO.write(BufferedImage(2000, 1000, BufferedImage.TYPE_INT_RGB), "png", file.toFile())
            val path = file.toOkioPath()
            val server = createServer()
            val added = callTool(server, "add_to_clipboard", mapOf("content" to path.toString(), "type" to "image"))
            val id = addedId(added)

            val result = callToolResult(server, "get_paste_item", mapOf("id" to id))

            assertTrue((result.content[0] as TextContent).text.contains("  - $path"))
            val image = assertIs<ImageContent>(result.content[1])
            assertEquals(McpImageEncoder.JPEG_MIME_TYPE, image.mimeType)
            val decoded = ImageIO.read(ByteArrayInputStream(Base64.getDecoder().decode(image.data)))
            assertEquals(McpImageEncoder.MAX_EDGE, decoded.width)
            assertEquals(McpImageEncoder.MAX_EDGE / 2, decoded.height)
            Files.delete(file)
        }

    @Test
    fun `get_paste_item returns the absolute path of a file item without image content`() =
        runTest {
            val file = Files.createTempFile("mcp-get", ".txt")
            val path = file.toOkioPath()
            val server = createServer()
            val added = callTool(server, "add_to_clipboard", mapOf("content" to path.toString(), "type" to "file"))
            val id = addedId(added)

            val result = callToolResult(server, "get_paste_item", mapOf("id" to id))

            assertEquals(1, result.content.size)
            assertTrue((result.content[0] as TextContent).text.contains("  - $path"))
            Files.delete(file)
        }

    @Test
    fun `get_paste_item keeps an image with alpha as png`() =
        runTest {
            val dir = Files.createTempDirectory("mcp-alpha")
            val id = insertImagesPasteData(listOf(writePng(dir, "a.png", BufferedImage.TYPE_INT_ARGB)))
            val server = createServer()

            val result = callToolResult(server, "get_paste_item", mapOf("id" to id))

            assertEquals(McpImageEncoder.PNG_MIME_TYPE, assertIs<ImageContent>(result.content[1]).mimeType)
            dir.toFile().deleteRecursively()
        }

    @Test
    fun `get_paste_item returns at most three images but lists every path`() =
        runTest {
            val dir = Files.createTempDirectory("mcp-many")
            val files = (1..4).map { writePng(dir, "img$it.png") }
            val id = insertImagesPasteData(files)
            val server = createServer()

            val result = callToolResult(server, "get_paste_item", mapOf("id" to id))

            assertEquals(McpPasteItemFormatter.MAX_IMAGES, result.content.count { it is ImageContent })
            val text = (result.content[0] as TextContent).text
            assertTrue(text.contains("Files (4):"), text)
            files.forEach { assertTrue(text.contains("  - ${it.toOkioPath()}"), text) }
            dir.toFile().deleteRecursively()
        }

    @Test
    fun `get_paste_item falls back to the path for an undecodable image`() =
        runTest {
            val dir = Files.createTempDirectory("mcp-broken")
            val file = dir.resolve("broken.png")
            Files.write(file, "not an image".toByteArray())
            val id = insertImagesPasteData(listOf(file))
            val server = createServer()

            val result = callToolResult(server, "get_paste_item", mapOf("id" to id))

            assertFalse(result.isError == true)
            assertEquals(1, result.content.size)
            assertTrue((result.content[0] as TextContent).text.contains("  - ${file.toOkioPath()}"))
            dir.toFile().deleteRecursively()
        }

    @Test
    fun `get_paste_item without id reports a still-loading latest item instead of invalid content`() =
        runTest {
            val server = createServer()
            insertTestPasteData("previous copy")
            val loadingId = insertLoadingPlaceholder()

            val result = callToolResult(server, "get_paste_item")

            assertTrue(result.isError == true)
            val text = (result.content.first() as TextContent).text
            assertTrue(text.contains("#$loadingId is still being saved"), text)
        }

    @Test
    fun `get_paste_item with the id of a loading item reports it is still being saved`() =
        runTest {
            val server = createServer()
            val loadingId = insertLoadingPlaceholder()

            val result = callToolResult(server, "get_paste_item", mapOf("id" to loadingId))

            assertTrue(result.isError == true)
            assertTrue((result.content.first() as TextContent).text.contains("still being saved"))
        }

    @Test
    fun `get_paste_item returns a parameter error for an object id`() =
        runTest {
            val server = createServer()
            insertTestPasteData("some item")

            val result =
                callToolWithJson(
                    server,
                    "get_paste_item",
                    buildJsonObject { putJsonObject("id") { put("value", 1) } },
                )

            assertTrue(result.isError == true)
            assertEquals("Error: 'id' must be a number.", (result.content.first() as TextContent).text)
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
    fun `add_to_clipboard rejects a relative file path`() =
        runTest {
            val server = createServer()
            val result =
                callTool(server, "add_to_clipboard", mapOf("content" to "build.gradle.kts", "type" to "file"))
            assertTrue(result.contains("Error"))
            assertTrue(result.contains("not an absolute path"))
        }

    @Test
    fun `add_to_clipboard file item references the original absolute path`() =
        runTest {
            val file = Files.createTempFile("mcp-add", ".txt")
            Files.writeString(file, "referenced, not copied")
            val path = file.toOkioPath()
            val server = createServer()

            val result = callTool(server, "add_to_clipboard", mapOf("content" to path.toString(), "type" to "file"))

            assertTrue(result.contains("type: file"), result)
            val item = addedPasteFiles(result)
            assertEquals(path.parent.toString(), item.basePath)
            assertEquals(listOf(path.name), item.relativePathList)
            assertTrue(item.hasExistingFiles())

            Files.delete(file)
            assertFalse(item.hasExistingFiles())
        }

    @Test
    fun `add_to_clipboard file item with directory references the original absolute path`() =
        runTest {
            val dir = Files.createTempDirectory("mcp-add-dir")
            val child = Files.createFile(dir.resolve("child.txt"))
            Files.writeString(child, "content in dir")
            val path = dir.toOkioPath()
            val server = createServer()

            val result = callTool(server, "add_to_clipboard", mapOf("content" to path.toString(), "type" to "file"))

            assertTrue(result.contains("type: file"), result)
            val item = addedPasteFiles(result)
            assertEquals(path.parent.toString(), item.basePath)
            assertEquals(listOf(path.name), item.relativePathList)
            assertTrue(item.hasExistingFiles())

            Files.delete(child)
            Files.delete(dir)
            assertFalse(item.hasExistingFiles())
        }

    @Test
    fun `add_to_clipboard image item references the original absolute path`() =
        runTest {
            val file = Files.createTempFile("mcp-add", ".png")
            Files.write(file, byteArrayOf(1, 2, 3))
            val path = file.toOkioPath()
            val server = createServer()

            val result = callTool(server, "add_to_clipboard", mapOf("content" to path.toString(), "type" to "image"))

            assertTrue(result.contains("type: image"), result)
            val item = addedPasteFiles(result)
            assertEquals(path.parent.toString(), item.basePath)
            assertTrue(item.hasExistingFiles())
            Files.delete(file)
            assertFalse(item.hasExistingFiles())
        }

    @Test
    fun `add_to_clipboard rejects a relative image path`() =
        runTest {
            val server = createServer()
            val result =
                callTool(server, "add_to_clipboard", mapOf("content" to "photo.png", "type" to "image"))
            assertTrue(result.contains("Error"))
            assertTrue(result.contains("not an absolute path"))
        }

    @Test
    fun `add_to_clipboard rejects a directory for image type`() =
        runTest {
            val parent = Files.createTempDirectory("mcp-image-test")
            val dir = Files.createDirectory(parent.resolve("dir.png"))
            val path = dir.toOkioPath()
            val server = createServer()
            try {
                val result =
                    callTool(server, "add_to_clipboard", mapOf("content" to path.toString(), "type" to "image"))
                assertTrue(result.contains("Error"))
                assertTrue(result.contains("is a directory, not an image file"))
            } finally {
                Files.delete(dir)
                Files.delete(parent)
            }
        }

    private fun addedId(result: String): Long = Regex("ID: (\\d+)").find(result)!!.groupValues[1].toLong()

    private suspend fun addedPasteFiles(result: String): PasteFiles {
        val pasteData = assertNotNull(pasteDao.getNoDeletePasteData(addedId(result)))
        return assertNotNull(pasteData.pasteAppearItem as? PasteFiles)
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
