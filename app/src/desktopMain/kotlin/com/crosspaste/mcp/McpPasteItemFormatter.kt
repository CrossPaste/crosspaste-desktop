package com.crosspaste.mcp

import com.crosspaste.db.paste.PasteDao
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteState
import com.crosspaste.paste.PasteType
import com.crosspaste.paste.item.PasteColor
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.paste.item.PasteItemReader
import com.crosspaste.paste.item.PasteText
import com.crosspaste.paste.item.PasteUrl
import com.crosspaste.paste.item.getFilePaths
import com.crosspaste.path.UserDataPathProvider
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent

/**
 * Renders one clipboard item for MCP clients, shared by the get_paste_item
 * tool and the clipboard://latest resource so both report the same content.
 */
class McpPasteItemFormatter(
    private val pasteDao: PasteDao,
    private val pasteItemReader: PasteItemReader,
    private val userDataPathProvider: UserDataPathProvider,
) {

    /** The newest non-deleted item, which may still be [isLoading]. */
    suspend fun latestPasteData(): PasteData? =
        pasteDao.searchPasteData(searchTerms = emptyList(), sort = true, limit = 1).firstOrNull()

    /**
     * A LOADING row is the placeholder created before a copy is collected (and
     * kept while a remote file sync runs); it has no content to report yet.
     */
    fun isLoading(pasteData: PasteData): Boolean = pasteData.pasteState == PasteState.LOADING

    fun loadingMessage(pasteData: PasteData): String =
        "Clipboard item #${pasteData.id} is still being saved; retry in a moment."

    fun formatText(pasteData: PasteData): String =
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
            appendContent(pasteData)
        }

    /** The first [MAX_IMAGES] decodable images of an image item; empty for any other type. */
    suspend fun encodeImages(pasteData: PasteData): List<ImageContent> {
        if (pasteData.getType() != PasteType.IMAGE_TYPE) return emptyList()
        val pasteFiles = pasteData.getPasteItem(PasteFiles::class) ?: return emptyList()
        return pasteFiles
            .getFilePaths(userDataPathProvider)
            .take(MAX_IMAGES)
            .mapNotNull { McpImageEncoder.encode(it) }
    }

    private fun StringBuilder.appendContent(pasteData: PasteData) {
        when (pasteData.getType()) {
            PasteType.TEXT_TYPE -> {
                val text = pasteData.getPasteItem(PasteText::class)
                appendLine(text?.text ?: "(empty)")
            }
            PasteType.URL_TYPE -> {
                val url = pasteData.getPasteItem(PasteUrl::class)
                appendLine("URL: ${url?.url ?: "(empty)"}")
                url?.getTitle()?.let { appendLine("Title: $it") }
            }
            PasteType.COLOR_TYPE -> {
                val color = pasteData.getPasteItem(PasteColor::class)
                if (color != null) {
                    appendLine("Hex: ${color.toHexString()}")
                    appendLine("RGBA: ${color.toRGBAString()}")
                }
            }
            PasteType.FILE_TYPE, PasteType.IMAGE_TYPE -> {
                val pasteFiles = pasteData.getPasteItem(PasteFiles::class)
                if (pasteFiles == null) {
                    appendLine("(no files)")
                } else {
                    appendLine("Files (${pasteFiles.count}):")
                    for (path in pasteFiles.getFilePaths(userDataPathProvider)) {
                        appendLine("  - $path")
                    }
                }
            }
            PasteType.HTML_TYPE, PasteType.RTF_TYPE -> {
                val text = pasteData.getPasteAppearItems().firstOrNull { it is PasteText }
                if (text != null) {
                    appendLine((text as PasteText).text)
                } else {
                    appendLine(summaryOf(pasteData) ?: "(no text representation)")
                }
            }
            else -> {
                appendLine(summaryOf(pasteData) ?: "(unknown content)")
            }
        }
    }

    private fun summaryOf(pasteData: PasteData): String? =
        pasteData.pasteAppearItem?.let { pasteItemReader.getSummary(it) }?.ifEmpty { null }

    companion object {
        const val MAX_IMAGES = 3
    }
}
