package com.crosspaste.paste.plugin.process

import com.crosspaste.paste.item.CreatePasteItemHelper.createUrlPasteItem
import com.crosspaste.paste.item.FilesPasteItem
import com.crosspaste.paste.item.PasteCoordinate
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.paste.item.UrlPasteItem
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.utils.DesktopFileUtils.fileSystem
import com.crosspaste.utils.extension
import okio.Path
import okio.buffer

class FileToUrlPlugin(
    private val userDataPathProvider: UserDataPathProvider,
) : PasteProcessPlugin {

    companion object {
        const val INTERNET_SHORTCUT = "[InternetShortcut]"
        const val MAX_FILE_SIZE = 64 * 1024 // 64KB
        const val URL_PREFIX = "URL="
    }

    override fun process(
        pasteCoordinate: PasteCoordinate,
        pasteItems: List<PasteItem>,
        source: String?,
    ): List<PasteItem> =
        pasteItems.mapNotNull { pasteAppearItem ->
            if (pasteAppearItem is FilesPasteItem) {
                val filePaths = pasteAppearItem.getFilePaths(userDataPathProvider)
                if (filePaths.size == 1 && filePaths[0].extension == "url") {
                    val url = extractUrlFromFile(filePaths[0])
                    if (url != null) {
                        if (pasteItems.any { it is UrlPasteItem && it.url == url }) {
                            null
                        } else {
                            createUrlPasteItem(
                                identifiers = pasteAppearItem.identifiers,
                                url = url,
                            )
                        }
                    } else {
                        pasteAppearItem
                    }
                } else {
                    pasteAppearItem
                }
            } else {
                pasteAppearItem
            }
        }

    private fun extractUrlFromFile(path: Path): String? {
        return try {
            // Check file size
            val metadata = fileSystem.metadata(path)
            val fileSize = metadata.size ?: return null

            if (fileSize > MAX_FILE_SIZE) {
                return null
            }

            // Read file content
            val content =
                fileSystem.source(path).buffer().use { source ->
                    source.readUtf8()
                }

            // Parse the content
            parseUrlFromContent(content)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseUrlFromContent(content: String): String? {
        val lines = content.lines()
        var foundInternetShortcut = false

        for (line in lines) {
            val trimmedLine = line.trim()

            if (trimmedLine == INTERNET_SHORTCUT) {
                foundInternetShortcut = true
                continue
            }

            if (foundInternetShortcut && trimmedLine.startsWith(URL_PREFIX, ignoreCase = true)) {
                // Extract URL after "URL="
                return trimmedLine.substring(URL_PREFIX.length).trim()
            }
        }

        return null
    }
}
