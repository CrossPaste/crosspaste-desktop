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

fun HtmlPasteItem.getParsedText(): String = htmlUtils.getHtmlText(html) ?: ""

val HtmlPasteItem.truncatedPreviewHtml: String
    get() = htmlUtils.truncateForPreview(html)

fun RtfPasteItem.getParsedText(): String = rtfUtils.getText(rtf) ?: ""

fun RtfPasteItem.getRtfHtml(): String = rtfUtils.rtfToHtml(rtf) ?: ""

val RtfPasteItem.truncatedPreviewHtml: String
    get() = rtfUtils.rtfToHtml(rtf)?.let { htmlUtils.truncateForPreview(it) } ?: ""

/**
 * Returns parsed search content for items that need text extraction.
 * For HtmlPasteItem: returns parsed text from HTML
 * For RtfPasteItem: returns parsed text from RTF
 * For all others: delegates to getSearchContent()
 */
fun PasteItem.getParsedSearchContent(): String? =
    when (this) {
        is HtmlPasteItem -> getParsedText().lowercase().ifEmpty { null }
        is RtfPasteItem -> getParsedText().lowercase().ifEmpty { null }
        else -> getSearchContent()
    }

/**
 * Validates the paste item with file system checks for file-based items.
 */
fun PasteItem.isValidWithFileCheck(): Boolean =
    when (this) {
        is FilesPasteItem -> isValid() && hasExistingFiles()
        is ImagesPasteItem -> isValid() && hasExistingFiles()
        is HtmlPasteItem -> isValid() && getParsedText().isNotEmpty()
        is RtfPasteItem -> {
            isValid() && getParsedText().isNotEmpty() && getRtfHtml().isNotEmpty()
        }
        else -> isValid()
    }
