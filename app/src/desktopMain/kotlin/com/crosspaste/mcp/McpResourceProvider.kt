package com.crosspaste.mcp

import com.crosspaste.db.paste.PasteDao
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents

class McpResourceProvider(
    private val pasteDao: PasteDao,
    private val pasteItemFormatter: McpPasteItemFormatter,
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
            val latest = pasteItemFormatter.latestPasteData()
            val text =
                when {
                    latest == null -> "No clipboard items available."
                    pasteItemFormatter.isLoading(latest) -> pasteItemFormatter.loadingMessage(latest)
                    else -> pasteItemFormatter.formatText(latest)
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
}
