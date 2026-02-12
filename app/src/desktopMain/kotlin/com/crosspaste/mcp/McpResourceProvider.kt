package com.crosspaste.mcp

import com.crosspaste.db.paste.PasteDao
import com.crosspaste.paste.PasteType
import com.crosspaste.paste.item.PasteColor
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.paste.item.PasteText
import com.crosspaste.paste.item.PasteUrl
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents

class McpResourceProvider(
    private val pasteDao: PasteDao,
) {

    fun registerResources(server: Server) {
        registerLatestClipboard(server)
        registerClipboardStats(server)
    }

    private fun registerLatestClipboard(server: Server) {
        server.addResource(
            uri = "clipboard://latest",
            name = "Latest Clipboard",
            description = "The most recent clipboard item content",
            mimeType = "text/plain",
        ) { request ->
            val results =
                pasteDao.searchPasteData(
                    searchTerms = emptyList(),
                    sort = true,
                    limit = 1,
                )
            val latest = results.firstOrNull()
            val text =
                if (latest == null) {
                    "No clipboard items available."
                } else {
                    buildString {
                        appendLine("Type: ${latest.getType().name}")
                        appendLine("Source: ${latest.source ?: "unknown"}")
                        appendLine("Created: ${latest.createTime}")
                        appendLine()
                        appendContent(latest)
                    }
                }
            ReadResourceResult(
                contents =
                    listOf(
                        TextResourceContents(
                            text = text,
                            uri = request.uri,
                            mimeType = "text/plain",
                        ),
                    ),
            )
        }
    }

    private fun registerClipboardStats(server: Server) {
        server.addResource(
            uri = "clipboard://stats",
            name = "Clipboard Statistics",
            description = "Overview of clipboard history statistics",
            mimeType = "application/json",
        ) { request ->
            val stats = pasteDao.getPasteResourceInfo()
            val json =
                buildString {
                    appendLine("{")
                    appendLine("  \"totalCount\": ${stats.pasteCount},")
                    appendLine("  \"totalSize\": ${stats.pasteSize},")
                    appendLine("  \"types\": {")
                    appendLine("    \"text\": { \"count\": ${stats.textCount}, \"size\": ${stats.textSize} },")
                    appendLine("    \"url\": { \"count\": ${stats.urlCount}, \"size\": ${stats.urlSize} },")
                    appendLine("    \"html\": { \"count\": ${stats.htmlCount}, \"size\": ${stats.htmlSize} },")
                    appendLine("    \"rtf\": { \"count\": ${stats.rtfCount}, \"size\": ${stats.rtfSize} },")
                    appendLine("    \"image\": { \"count\": ${stats.imageCount}, \"size\": ${stats.imageSize} },")
                    appendLine("    \"file\": { \"count\": ${stats.fileCount}, \"size\": ${stats.fileSize} },")
                    appendLine("    \"color\": { \"count\": ${stats.colorCount}, \"size\": ${stats.colorSize} }")
                    appendLine("  }")
                    append("}")
                }
            ReadResourceResult(
                contents =
                    listOf(
                        TextResourceContents(
                            text = json,
                            uri = request.uri,
                            mimeType = "application/json",
                        ),
                    ),
            )
        }
    }

    private fun StringBuilder.appendContent(pasteData: com.crosspaste.paste.PasteData) {
        when (pasteData.getType()) {
            PasteType.TEXT_TYPE -> {
                val text = pasteData.getPasteItem(PasteText::class)
                appendLine(text?.text ?: "(empty)")
            }
            PasteType.URL_TYPE -> {
                val url = pasteData.getPasteItem(PasteUrl::class)
                appendLine(url?.url ?: "(empty)")
            }
            PasteType.COLOR_TYPE -> {
                val color = pasteData.getPasteItem(PasteColor::class)
                if (color != null) appendLine(color.toHexString())
            }
            PasteType.FILE_TYPE, PasteType.IMAGE_TYPE -> {
                val files = pasteData.getPasteItem(PasteFiles::class)
                if (files != null) {
                    for (path in files.relativePathList) {
                        appendLine(path)
                    }
                }
            }
            else -> {
                appendLine(pasteData.getSummary("Loading...", "Unknown"))
            }
        }
    }
}
