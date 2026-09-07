package com.crosspaste.mcp

import androidx.compose.ui.graphics.toArgb
import com.crosspaste.app.AppInfo
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.db.paste.PasteTagDao
import com.crosspaste.paste.PasteCollection
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteDataHelper
import com.crosspaste.paste.PasteState
import com.crosspaste.paste.PasteType
import com.crosspaste.paste.SearchContentService
import com.crosspaste.paste.item.CreatePasteItemHelper.createColorPasteItem
import com.crosspaste.paste.item.CreatePasteItemHelper.createFilesPasteItem
import com.crosspaste.paste.item.CreatePasteItemHelper.createHtmlPasteItem
import com.crosspaste.paste.item.CreatePasteItemHelper.createImagesPasteItem
import com.crosspaste.paste.item.CreatePasteItemHelper.createRtfPasteItem
import com.crosspaste.paste.item.CreatePasteItemHelper.createTextPasteItem
import com.crosspaste.paste.item.CreatePasteItemHelper.createUrlPasteItem
import com.crosspaste.paste.item.PasteColor
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.paste.item.PasteItemReader
import com.crosspaste.paste.item.PasteText
import com.crosspaste.paste.item.PasteUrl
import com.crosspaste.paste.plugin.type.DesktopFilesTypePlugin
import com.crosspaste.paste.plugin.type.DesktopHtmlTypePlugin
import com.crosspaste.paste.plugin.type.DesktopImageTypePlugin
import com.crosspaste.paste.plugin.type.DesktopRtfTypePlugin
import com.crosspaste.paste.plugin.type.DesktopTextTypePlugin
import com.crosspaste.paste.plugin.type.DesktopUrlTypePlugin
import com.crosspaste.presist.SingleFileInfoTree
import com.crosspaste.utils.ColorParser
import com.crosspaste.utils.DateUtils
import com.crosspaste.utils.HtmlUtils
import com.crosspaste.utils.getCodecsUtils
import com.crosspaste.utils.getFileUtils
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okio.FileSystem
import okio.Path.Companion.toPath

class McpToolProvider(
    private val appInfo: AppInfo,
    private val pasteDao: PasteDao,
    private val pasteDataHelper: PasteDataHelper,
    private val pasteItemReader: PasteItemReader,
    private val pasteTagDao: PasteTagDao,
    private val searchContentService: SearchContentService,
) {
    private val fileUtils = getFileUtils()

    fun registerTools(server: Server) {
        registerSearchClipboard(server)
        registerGetPasteItem(server)
        registerGetClipboardStats(server)
        registerListTags(server)
        registerAddToClipboard(server)
    }

    private fun registerSearchClipboard(server: Server) {
        server.addTool(
            name = "search_clipboard",
            description =
                "Search or browse the local clipboard history. Read-only. " +
                    "Returns up to 'limit' items (default 20, max 100), newest first, each with its numeric ID, " +
                    "type, source app, favorite flag, size, creation time and a one-line summary; " +
                    "pass an ID to get_paste_item for the full content. " +
                    "Omit 'query' to list the most recent items; otherwise every keyword must match " +
                    "as a prefix of a word in the item's searchable text. " +
                    "'type' and 'tag' narrow the results and can be combined; tag names come from list_tags. " +
                    "An unknown 'type' or 'tag' returns an error rather than an empty list. " +
                    "Use get_clipboard_stats for counts only and add_to_clipboard to create items.",
            inputSchema =
                ToolSchema(
                    properties =
                        buildJsonObject {
                            putJsonObject("query") {
                                put("type", "string")
                                put(
                                    "description",
                                    "Space-separated keywords, prefix-matched (e.g. 'invoice 2026'). " +
                                        "Omit or leave empty to list recent items.",
                                )
                            }
                            putJsonObject("type") {
                                put("type", "string")
                                put(
                                    "description",
                                    "Only return items of this type: text, link, image, rtf, html, color, file " +
                                        "(case-insensitive; note that URLs are type 'link').",
                                )
                            }
                            putJsonObject("tag") {
                                put("type", "string")
                                put(
                                    "description",
                                    "Only return items carrying this tag, by exact tag name as shown by list_tags " +
                                        "(e.g. 'Favorite').",
                                )
                            }
                            putJsonObject("limit") {
                                put("type", "integer")
                                put(
                                    "description",
                                    "Maximum number of results, 1-100 (default 20; out-of-range values are clamped)",
                                )
                            }
                        },
                ),
            toolAnnotations =
                ToolAnnotations(
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false,
                ),
        ) { request ->
            val query =
                request.arguments
                    ?.get("query")
                    ?.jsonPrimitive
                    ?.content ?: ""
            val typeStr =
                request.arguments
                    ?.get("type")
                    ?.jsonPrimitive
                    ?.content
            val tagName =
                request.arguments
                    ?.get("tag")
                    ?.jsonPrimitive
                    ?.content
            val tagId =
                tagName?.let { name ->
                    pasteTagDao.getAllTagsBlock().firstOrNull { it.name == name }?.id
                }
            if (tagName != null && tagId == null) {
                return@addTool CallToolResult(
                    content =
                        listOf(
                            TextContent("Error: no tag named '$tagName'. Use list_tags to see available tags."),
                        ),
                    isError = true,
                )
            }
            val limit =
                request.arguments
                    ?.get("limit")
                    ?.jsonPrimitive
                    ?.content
                    ?.toIntOrNull()
                    ?.coerceIn(1, 100) ?: 20

            val searchTerms =
                if (query.isNotBlank()) {
                    searchContentService.createSearchTerms(query)
                } else {
                    emptyList()
                }

            val pasteType = typeStr?.let { findPasteType(it) }
            if (typeStr != null && pasteType == null) {
                return@addTool CallToolResult(
                    content =
                        listOf(
                            TextContent(
                                "Error: unknown type '$typeStr'. " +
                                    "Supported types: ${PasteType.TYPES.joinToString(", ") { it.name }}.",
                            ),
                        ),
                    isError = true,
                )
            }

            val results =
                pasteDao.searchPasteData(
                    searchTerms = searchTerms,
                    pasteTypeList = listOfNotNull(pasteType?.type),
                    sort = true,
                    tag = tagId,
                    limit = limit,
                )

            val text =
                buildString {
                    if (results.isEmpty()) {
                        append("No clipboard items found.")
                    } else {
                        appendLine("Found ${results.size} clipboard item(s):")
                        appendLine()
                        for (item in results) {
                            appendLine("--- ID: ${item.id} ---")
                            appendLine("Type: ${item.getType().name}")
                            appendLine("Source: ${item.source ?: "unknown"}")
                            appendLine("Favorite: ${item.favorite}")
                            appendLine("Size: ${item.size} bytes")
                            appendLine("Created: ${item.createTime}")
                            appendLine("Summary: ${pasteDataHelper.getSummary(item, "Loading...", "Unknown")}")
                            appendLine()
                        }
                    }
                }

            CallToolResult(content = listOf(TextContent(text)))
        }
    }

    private fun registerGetPasteItem(server: Server) {
        server.addTool(
            name = "get_paste_item",
            description =
                "Return the full content and metadata of one clipboard history item by its numeric ID. " +
                    "Read-only. IDs come from search_clipboard results or from add_to_clipboard. " +
                    "Output is plain text: a header (ID, type, source app, favorite flag, size, creation time, " +
                    "whether it came from a remote device) followed by the content: the text of text/html/rtf items, " +
                    "the URL and page title of links, hex and RGBA values of colors, or the file names of " +
                    "file/image items (binary data is not returned). " +
                    "Returns an error if 'id' is missing, not a number, or matches no existing item. " +
                    "Use search_clipboard first when the ID is not known.",
            inputSchema =
                ToolSchema(
                    properties =
                        buildJsonObject {
                            putJsonObject("id") {
                                put("type", "integer")
                                put(
                                    "description",
                                    "Numeric ID of the clipboard item, as reported by search_clipboard or add_to_clipboard",
                                )
                            }
                        },
                    required = listOf("id"),
                ),
            toolAnnotations =
                ToolAnnotations(
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false,
                ),
        ) { request ->
            val id =
                request.arguments
                    ?.get("id")
                    ?.jsonPrimitive
                    ?.content
                    ?.toLongOrNull()
            if (id == null) {
                CallToolResult(
                    content = listOf(TextContent("Error: 'id' parameter is required and must be a number.")),
                    isError = true,
                )
            } else {
                val pasteData = pasteDao.getNoDeletePasteData(id)
                if (pasteData == null) {
                    CallToolResult(
                        content = listOf(TextContent("No paste item found with ID: $id")),
                        isError = true,
                    )
                } else {
                    val text =
                        buildString {
                            appendLine("Paste Item #${pasteData.id}")
                            appendLine("Type: ${pasteData.getType().name}")
                            appendLine("Source: ${pasteData.source ?: "unknown"}")
                            appendLine("Favorite: ${pasteData.favorite}")
                            appendLine("Size: ${pasteData.size} bytes")
                            appendLine("Created: ${pasteData.createTime}")
                            appendLine("Remote: ${pasteData.remote}")
                            appendLine()
                            appendLine("--- Content ---")
                            extractContent(pasteData, this)
                        }
                    CallToolResult(content = listOf(TextContent(text)))
                }
            }
        }
    }

    private fun registerGetClipboardStats(server: Server) {
        server.addTool(
            name = "get_clipboard_stats",
            description =
                "Report how much is stored in the local clipboard history. Read-only, no parameters. " +
                    "Returns plain text with the total item count and total size, then the count and size " +
                    "for each type (text, URL, HTML, RTF, image, file, color); " +
                    "sizes are human-readable (e.g. 1.2 MB). " +
                    "Use it for an overview only: use search_clipboard to see the items themselves " +
                    "and list_tags to see tags.",
            toolAnnotations =
                ToolAnnotations(
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false,
                ),
        ) { _ ->
            val stats = pasteDao.getPasteResourceInfo()
            val text =
                buildString {
                    appendLine("Clipboard Statistics:")
                    appendLine("Total: ${stats.pasteCount} items, ${fileUtils.formatBytes(stats.pasteSize)}")
                    appendLine()
                    appendLine("By type:")
                    appendLine("  Text:   ${stats.textCount} items, ${fileUtils.formatBytes(stats.textSize)}")
                    appendLine("  URL:    ${stats.urlCount} items, ${fileUtils.formatBytes(stats.urlSize)}")
                    appendLine("  HTML:   ${stats.htmlCount} items, ${fileUtils.formatBytes(stats.htmlSize)}")
                    appendLine("  RTF:    ${stats.rtfCount} items, ${fileUtils.formatBytes(stats.rtfSize)}")
                    appendLine("  Image:  ${stats.imageCount} items, ${fileUtils.formatBytes(stats.imageSize)}")
                    appendLine("  File:   ${stats.fileCount} items, ${fileUtils.formatBytes(stats.fileSize)}")
                    appendLine("  Color:  ${stats.colorCount} items, ${fileUtils.formatBytes(stats.colorSize)}")
                }
            CallToolResult(content = listOf(TextContent(text)))
        }
    }

    private fun registerListTags(server: Server) {
        server.addTool(
            name = "list_tags",
            description =
                "List every tag defined in the local clipboard history. Read-only, no parameters. " +
                    "Tags are user-created labels (e.g. 'Favorite') attached to clipboard items. " +
                    "Returns plain text with the tag count followed by one line per tag, in the user's " +
                    "configured order, showing its numeric ID and name, or 'No tags found.' when there are none. " +
                    "Pass a tag name to the 'tag' parameter of search_clipboard to find the items carrying it. " +
                    "This tool cannot create, rename or delete tags.",
            toolAnnotations =
                ToolAnnotations(
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false,
                ),
        ) { _ ->
            val tags = pasteTagDao.getAllTagsFlow().first()
            val text =
                if (tags.isEmpty()) {
                    "No tags found."
                } else {
                    buildString {
                        appendLine("Tags (${tags.size}):")
                        for (tag in tags) {
                            appendLine("  - [${tag.id}] ${tag.name}")
                        }
                    }
                }
            CallToolResult(content = listOf(TextContent(text)))
        }
    }

    @Suppress("CyclomaticComplexMethod")
    private fun registerAddToClipboard(server: Server) {
        server.addTool(
            name = "add_to_clipboard",
            description =
                "Create a new item in the local clipboard history. " +
                    "This only appends to CrossPaste's history: it does not change the system clipboard " +
                    "and the item is not sent to other devices. Every call creates a new item, even for " +
                    "duplicate content, recorded with source 'MCP'. " +
                    "'type' defaults to 'text'; supported types: text, url, html, rtf, color, file, image. " +
                    "For file/image, 'content' must be the absolute path of an existing file on this machine " +
                    "(images: png, jpg, jpeg, gif, bmp, webp, heic, heif, tiff, svg); the file is referenced " +
                    "by path, not copied. For color, 'content' may be #RGB, #RRGGBB, #RRGGBBAA, rgb(), rgba(), " +
                    "hsl(), hsla() or a CSS color name. html must contain extractable text and rtf must start " +
                    "with '{\\rtf'. On success returns the new item's ID (usable with get_paste_item); " +
                    "on invalid input returns an error and nothing is stored.",
            inputSchema =
                ToolSchema(
                    properties =
                        buildJsonObject {
                            putJsonObject("content") {
                                put("type", "string")
                                put(
                                    "description",
                                    "The content to store, interpreted according to 'type': plain text, a URL, " +
                                        "an HTML or RTF document, a color value (e.g. #FF0000), " +
                                        "or the absolute path of a file/image. Must not be blank.",
                                )
                            }
                            putJsonObject("type") {
                                put("type", "string")
                                put(
                                    "description",
                                    "Content type: 'text' (default), 'url', 'html', 'rtf', 'color', 'file', 'image'. " +
                                        "Unrecognized values are treated as 'text'.",
                                )
                            }
                        },
                    required = listOf("content"),
                ),
            toolAnnotations =
                ToolAnnotations(
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = false,
                    openWorldHint = false,
                ),
        ) { request ->
            val content =
                request.arguments
                    ?.get("content")
                    ?.jsonPrimitive
                    ?.content
            val typeStr =
                request.arguments
                    ?.get("type")
                    ?.jsonPrimitive
                    ?.content
                    ?.lowercase() ?: "text"

            if (content.isNullOrBlank()) {
                CallToolResult(
                    content = listOf(TextContent("Error: 'content' parameter is required and must not be blank.")),
                    isError = true,
                )
            } else {
                val result = createPasteData(content, typeStr)
                result.fold(
                    onSuccess = { pasteData ->
                        val id = pasteDao.createPasteData(pasteData)
                        CallToolResult(
                            content =
                                listOf(
                                    TextContent("Successfully added to clipboard with ID: $id (type: $typeStr)"),
                                ),
                        )
                    },
                    onFailure = { error ->
                        CallToolResult(
                            content = listOf(TextContent("Error: ${error.message}")),
                            isError = true,
                        )
                    },
                )
            }
        }
    }

    private val codecsUtils = getCodecsUtils()

    private fun createPasteData(
        content: String,
        type: String,
    ): Result<PasteData> {
        val (pasteItem, pasteType) =
            when (type) {
                "url" -> {
                    createUrlPasteItem(
                        identifiers = listOf(DesktopUrlTypePlugin.URL),
                        url = content,
                    ) to PasteType.URL_TYPE
                }
                "html" -> {
                    val extractedText = HtmlUtils.getHtmlText(content)
                    if (extractedText.isNullOrBlank()) {
                        return Result.failure(
                            IllegalArgumentException(
                                "Invalid HTML content: cannot extract text from the provided HTML.",
                            ),
                        )
                    }
                    createHtmlPasteItem(
                        identifiers = listOf(DesktopHtmlTypePlugin.HTML_ID),
                        html = content,
                    ) to PasteType.HTML_TYPE
                }
                "rtf" -> {
                    if (!content.trimStart().startsWith("{\\rtf")) {
                        return Result.failure(
                            IllegalArgumentException("Invalid RTF content: must start with '{\\rtf'."),
                        )
                    }
                    createRtfPasteItem(
                        identifiers = listOf(DesktopRtfTypePlugin.RTF_ID),
                        rtf = content,
                    ) to PasteType.RTF_TYPE
                }
                "color" -> {
                    val color =
                        ColorParser.toColor(content)
                            ?: return Result.failure(
                                IllegalArgumentException(
                                    "Invalid color value: '$content'. " +
                                        "Supported formats: #RGB, #RRGGBB, #RRGGBBAA, rgb(), rgba(), hsl(), hsla(), CSS color names.",
                                ),
                            )
                    createColorPasteItem(
                        color = color.toArgb(),
                    ) to PasteType.COLOR_TYPE
                }
                "file" -> {
                    createFilePasteItem(content, PasteType.FILE_TYPE)
                        ?: return Result.failure(
                            IllegalArgumentException("File not found: '$content'."),
                        )
                }
                "image" -> {
                    val path = content.toPath()
                    val ext = path.name.substringAfterLast('.', "").lowercase()
                    if (!fileUtils.canPreviewImage(ext)) {
                        return Result.failure(
                            IllegalArgumentException(
                                "Unsupported image format: '$ext'. " +
                                    "Supported: png, jpg, jpeg, gif, bmp, webp, heic, heif, tiff, svg.",
                            ),
                        )
                    }
                    createFilePasteItem(content, PasteType.IMAGE_TYPE)
                        ?: return Result.failure(
                            IllegalArgumentException("Image file not found: '$content'."),
                        )
                }
                else -> {
                    createTextPasteItem(
                        identifiers = listOf(DesktopTextTypePlugin.TEXT),
                        text = content,
                    ) to PasteType.TEXT_TYPE
                }
            }
        return Result.success(
            PasteData(
                appInstanceId = appInfo.appInstanceId,
                pasteAppearItem = pasteItem,
                pasteCollection = PasteCollection(listOf()),
                pasteType = pasteType.type,
                source = "MCP",
                size = pasteItem.size,
                hash = pasteItem.hash,
                pasteState = PasteState.LOADED,
                createTime = DateUtils.nowEpochMilliseconds(),
            ),
        )
    }

    private fun createFilePasteItem(
        filePath: String,
        pasteType: PasteType,
    ): Pair<PasteItem, PasteType>? {
        val path = filePath.toPath()
        if (!FileSystem.SYSTEM.exists(path)) {
            return null
        }
        val metadata = FileSystem.SYSTEM.metadata(path)
        val fileSize = metadata.size ?: 0L
        val fileBytes = FileSystem.SYSTEM.read(path) { readByteArray() }
        val fileHash = codecsUtils.hash(fileBytes)
        val fileName = path.name
        val fileInfoTree = SingleFileInfoTree(size = fileSize, hash = fileHash)
        val identifiers =
            if (pasteType == PasteType.IMAGE_TYPE) {
                listOf(DesktopImageTypePlugin.IMAGE)
            } else {
                listOf(DesktopFilesTypePlugin.FILE_LIST_ID)
            }
        val item =
            if (pasteType == PasteType.IMAGE_TYPE) {
                createImagesPasteItem(
                    identifiers = identifiers,
                    relativePathList = listOf(fileName),
                    fileInfoTreeMap = mapOf(fileName to fileInfoTree),
                )
            } else {
                createFilesPasteItem(
                    identifiers = identifiers,
                    relativePathList = listOf(fileName),
                    fileInfoTreeMap = mapOf(fileName to fileInfoTree),
                )
            }
        return item to pasteType
    }

    private fun extractContent(
        pasteData: PasteData,
        sb: StringBuilder,
    ) {
        val type = pasteData.getType()
        when (type) {
            PasteType.TEXT_TYPE -> {
                val text = pasteData.getPasteItem(PasteText::class)
                sb.appendLine(text?.text ?: "(empty)")
            }
            PasteType.URL_TYPE -> {
                val url = pasteData.getPasteItem(PasteUrl::class)
                sb.appendLine("URL: ${url?.url ?: "(empty)"}")
                url?.getTitle()?.let { sb.appendLine("Title: $it") }
            }
            PasteType.COLOR_TYPE -> {
                val color = pasteData.getPasteItem(PasteColor::class)
                if (color != null) {
                    sb.appendLine("Hex: ${color.toHexString()}")
                    sb.appendLine("RGBA: ${color.toRGBAString()}")
                }
            }
            PasteType.FILE_TYPE, PasteType.IMAGE_TYPE -> {
                val files = pasteData.getPasteItem(PasteFiles::class)
                if (files != null) {
                    sb.appendLine("Files (${files.count}):")
                    for (path in files.relativePathList) {
                        sb.appendLine("  - $path")
                    }
                }
            }
            PasteType.HTML_TYPE, PasteType.RTF_TYPE -> {
                val text = pasteData.getPasteAppearItems().firstOrNull { it is PasteText }
                if (text != null) {
                    sb.appendLine((text as PasteText).text)
                } else {
                    val summary = pasteData.pasteAppearItem?.let { pasteItemReader.getSummary(it) }
                    sb.appendLine(summary?.ifEmpty { null } ?: "(no text representation)")
                }
            }
            else -> {
                val summary = pasteData.pasteAppearItem?.let { pasteItemReader.getSummary(it) }
                sb.appendLine(summary?.ifEmpty { null } ?: "(unknown content)")
            }
        }
    }

    private fun findPasteType(name: String): PasteType? =
        PasteType.TYPES.firstOrNull { it.name.equals(name, ignoreCase = true) }
}
