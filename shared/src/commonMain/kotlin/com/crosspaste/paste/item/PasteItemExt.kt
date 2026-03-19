package com.crosspaste.paste.item

import com.crosspaste.app.AppFileType
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.getHtmlUtils
import com.crosspaste.utils.getRtfUtils
import okio.Path
import okio.Path.Companion.toPath

private val fileUtils = getFileUtils()

fun UrlPasteItem.getRenderingFilePath(
    pasteCoordinate: PasteCoordinate,
    userDataPathProvider: UserDataPathProvider,
): Path =
    getMarketingPath()?.toPath() ?: run {
        val basePath = userDataPathProvider.resolve(appFileType = AppFileType.OPEN_GRAPH)
        val relativePath =
            fileUtils.createPasteRelativePath(
                pasteCoordinate = pasteCoordinate,
                fileName = UrlPasteItem.OPEN_GRAPH_IMAGE,
            )
        userDataPathProvider.resolve(basePath, relativePath, autoCreate = false, isFile = true)
    }

fun PasteItem.clear(
    clearResource: Boolean = true,
    userDataPathProvider: UserDataPathProvider,
) {
    when (this) {
        is FilesPasteItem, is ImagesPasteItem -> {
            val pasteFiles = this as PasteFiles
            if (clearResource && pasteFiles.basePath == null) {
                for (path in pasteFiles.getFilePaths(userDataPathProvider)) {
                    fileUtils.deleteFile(path)
                }
            }
        }
        else -> {}
    }
}

fun PasteItem.bindItem(
    pasteCoordinate: PasteCoordinate,
    syncToDownload: Boolean = false,
): PasteItem =
    when (this) {
        is FilesPasteItem -> {
            val (newBasePath, newRelativePathList) = bindFilePaths(pasteCoordinate, syncToDownload)
            FilesPasteItem(
                identifiers = identifiers,
                count = count,
                hash = hash,
                size = size,
                basePath = newBasePath,
                relativePathList = newRelativePathList,
                fileInfoTreeMap = fileInfoTreeMap,
                extraInfo = extraInfo,
            )
        }
        is ImagesPasteItem -> {
            val (newBasePath, newRelativePathList) = bindFilePaths(pasteCoordinate, syncToDownload)
            ImagesPasteItem(
                identifiers = identifiers,
                count = count,
                hash = hash,
                size = size,
                basePath = newBasePath,
                relativePathList = newRelativePathList,
                fileInfoTreeMap = fileInfoTreeMap,
                extraInfo = extraInfo,
            )
        }
        else -> this.bind(pasteCoordinate, syncToDownload)
    }

// --- HTML/RTF text extraction extensions ---

private val htmlUtils by lazy { getHtmlUtils() }
private val rtfUtils by lazy { getRtfUtils() }

val HtmlPasteItem.truncatedPreviewHtml: String
    get() = htmlUtils.truncateForPreview(html)

val RtfPasteItem.truncatedPreviewHtml: String
    get() = rtfUtils.rtfToHtml(rtf)?.let { htmlUtils.truncateForPreview(it) } ?: ""

/**
 * Extract plain-text search content from a PasteItem.
 * For HtmlPasteItem/RtfPasteItem, parses the markup to get readable text.
 * For all others, delegates to [PasteItem.getSearchContent].
 */
fun PasteItem.extractSearchContent(): String? =
    when (this) {
        is HtmlPasteItem -> htmlUtils.getHtmlText(html)?.lowercase()
        is RtfPasteItem -> rtfUtils.getText(rtf)?.lowercase()
        else -> getSearchContent()
    }
