package com.crosspaste.mcp

import com.crosspaste.db.paste.PasteDao
import com.crosspaste.paste.PasteType
import com.crosspaste.paste.SearchContentService
import com.crosspaste.paste.item.PasteColor
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.paste.item.PasteText
import com.crosspaste.paste.item.PasteUrl
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class McpToolProvider(
    private val pasteDao: PasteDao,
    private val searchContentService: SearchContentService,
) {

    fun registerTools(server: Server) {
        registerSearchClipboard(server)
        registerGetPasteItem(server)
        registerGetClipboardStats(server)
        registerListTags(server)
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
                    ?.toString()
                    ?.removeSurrounding("\"") ?: ""
            val typeStr =
                request.arguments
                    ?.get("type")
                    ?.toString()
                    ?.removeSurrounding("\"")
            val favorite =
                request.arguments
                    ?.get("favorite")
                    ?.toString()
                    ?.toBooleanStrictOrNull()
            val limit =
                request.arguments
                    ?.get("limit")
                    ?.toString()
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
                    ?.toString()
                    ?.removeSurrounding("\"")
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
                    appendLine("Total: ${stats.pasteCount} items, ${formatSize(stats.pasteSize)}")
                    appendLine()
                    appendLine("By type:")
                    appendLine("  Text:   ${stats.textCount} items, ${formatSize(stats.textSize)}")
                    appendLine("  URL:    ${stats.urlCount} items, ${formatSize(stats.urlSize)}")
                    appendLine("  HTML:   ${stats.htmlCount} items, ${formatSize(stats.htmlSize)}")
                    appendLine("  RTF:    ${stats.rtfCount} items, ${formatSize(stats.rtfSize)}")
                    appendLine("  Image:  ${stats.imageCount} items, ${formatSize(stats.imageSize)}")
                    appendLine("  File:   ${stats.fileCount} items, ${formatSize(stats.fileSize)}")
                    appendLine("  Color:  ${stats.colorCount} items, ${formatSize(stats.colorSize)}")
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

    private fun extractContent(
        pasteData: com.crosspaste.paste.PasteData,
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

    private fun formatSize(bytes: Long): String =
        when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
}
