package com.crosspaste.mcp

import androidx.compose.ui.graphics.toArgb
import com.crosspaste.app.AppInfo
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.paste.PasteCollection
import com.crosspaste.paste.PasteData
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
import com.crosspaste.paste.item.PasteText
import com.crosspaste.paste.item.PasteUrl
import com.crosspaste.paste.plugin.type.DesktopFilesTypePlugin
import com.crosspaste.paste.plugin.type.DesktopHtmlTypePlugin
import com.crosspaste.paste.plugin.type.DesktopImageTypePlugin
import com.crosspaste.paste.plugin.type.DesktopRtfTypePlugin
import com.crosspaste.paste.plugin.type.DesktopTextTypePlugin
import com.crosspaste.paste.plugin.type.DesktopUrlTypePlugin
import com.crosspaste.presist.SingleFileInfoTree
import com.crosspaste.utils.ColorUtils
import com.crosspaste.utils.DateUtils
import com.crosspaste.utils.HtmlUtils
import com.crosspaste.utils.getCodecsUtils
import com.crosspaste.utils.getFileUtils
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
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
            description = "Search clipboard history. Returns a list of paste items matching the query.",
            inputSchema =
                ToolSchema(
                    properties =
                        buildJsonObject {
                            putJsonObject("query") {
                                put("type", "string")
                                put("description", "Search keywords (optional, empty returns recent items)")
                            }
                            putJsonObject("type") {
                                put("type", "string")
                                put("description", "Filter by paste type: text, link, image, rtf, html, color, file")
                            }
                            putJsonObject("favorite") {
                                put("type", "boolean")
                                put("description", "Filter by favorite status")
                            }
                            putJsonObject("limit") {
                                put("type", "integer")
                                put("description", "Maximum number of results (default: 20, max: 100)")
                            }
                        },
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
            val favorite =
                request.arguments
                    ?.get("favorite")
                    ?.jsonPrimitive
                    ?.content
                    ?.toBooleanStrictOrNull()
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

            val results =
                pasteDao.searchPasteData(
                    searchTerms = searchTerms,
                    favorite = favorite,
                    pasteType = pasteType?.type,
                    sort = true,
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
                            appendLine("Summary: ${item.getSummary("Loading...", "Unknown")}")
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
            description = "Get the full content of a specific clipboard item by its ID.",
            inputSchema =
                ToolSchema(
                    properties =
                        buildJsonObject {
                            putJsonObject("id") {
                                put("type", "integer")
                                put("description", "The paste item ID")
                            }
                        },
                    required = listOf("id"),
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
            description = "Get clipboard statistics: total count and size by type.",
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
            description = "List all clipboard tags for organizing paste items.",
        ) { _ ->
            val tags = pasteDao.getAllTagsFlow().first()
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
                "Add content to the clipboard history. " +
                    "Supported types: text, url, html, rtf, color, file, image. " +
                    "For file/image, content should be the absolute file path. " +
                    "For color, content can be hex (#FF0000), rgb(), rgba(), hsl(), hsla(), or CSS color name.",
            inputSchema =
                ToolSchema(
                    properties =
                        buildJsonObject {
                            putJsonObject("content") {
                                put("type", "string")
                                put(
                                    "description",
                                    "The content to add. Text/URL/HTML/RTF as string, " +
                                        "color as color value (e.g. #FF0000), file/image as absolute file path",
                                )
                            }
                            putJsonObject("type") {
                                put("type", "string")
                                put(
                                    "description",
                                    "Content type: 'text' (default), 'url', 'html', 'rtf', 'color', 'file', 'image'",
                                )
                            }
                        },
                    required = listOf("content"),
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
                        ColorUtils.toColor(content)
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
                    sb.appendLine(pasteData.pasteAppearItem?.getSummary() ?: "(no text representation)")
                }
            }
            else -> {
                sb.appendLine(pasteData.pasteAppearItem?.getSummary() ?: "(unknown content)")
            }
        }
    }

    private fun findPasteType(name: String): PasteType? =
        PasteType.TYPES.firstOrNull { it.name.equals(name, ignoreCase = true) }
}
