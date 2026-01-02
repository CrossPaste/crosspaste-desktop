package com.crosspaste.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.ioDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class StorageStatisticsScope(
    val pasteDao: PasteDao,
    val scope: CoroutineScope = CoroutineScope(ioDispatcher + SupervisorJob()),
) {

    val fileUtils = getFileUtils()

    var pasteCount: Long? by mutableStateOf(null)
    var pasteFormatSize: String? by mutableStateOf(null)

    var textCount: Long? by mutableStateOf(null)
    var textFormatSize: String? by mutableStateOf(null)

    var colorCount: Long? by mutableStateOf(null)
    var colorFormatSize: String? by mutableStateOf(null)

    var urlCount: Long? by mutableStateOf(null)
    var urlFormatSize: String? by mutableStateOf(null)

    var htmlCount: Long? by mutableStateOf(null)
    var htmlFormatSize: String? by mutableStateOf(null)

    var rtfCount: Long? by mutableStateOf(null)
    var rtfFormatSize: String? by mutableStateOf(null)

    var imageCount: Long? by mutableStateOf(null)
    var imageFormatSize: String? by mutableStateOf(null)

    var fileCount: Long? by mutableStateOf(null)
    var fileFormatSize: String? by mutableStateOf(null)

    var allOrFavorite by mutableStateOf(true)

    var cleaning by mutableStateOf(false)

    suspend fun refresh() {
        runCatching {
            val pasteResourceInfo =
                pasteDao.getPasteResourceInfo(
                    if (allOrFavorite) {
                        null
                    } else {
                        true
                    },
                )
            pasteCount = pasteResourceInfo.pasteCount
            pasteFormatSize = fileUtils.formatBytes(pasteResourceInfo.pasteSize)

            textCount = pasteResourceInfo.textCount
            textFormatSize = fileUtils.formatBytes(pasteResourceInfo.textSize)

            colorCount = pasteResourceInfo.colorCount
            colorFormatSize = fileUtils.formatBytes(pasteResourceInfo.colorSize)

            urlCount = pasteResourceInfo.urlCount
            urlFormatSize = fileUtils.formatBytes(pasteResourceInfo.urlSize)

            htmlCount = pasteResourceInfo.htmlCount
            htmlFormatSize = fileUtils.formatBytes(pasteResourceInfo.htmlSize)

            rtfCount = pasteResourceInfo.rtfCount
            rtfFormatSize = fileUtils.formatBytes(pasteResourceInfo.rtfSize)

            imageCount = pasteResourceInfo.imageCount
            imageFormatSize = fileUtils.formatBytes(pasteResourceInfo.imageSize)

            fileCount = pasteResourceInfo.fileCount
            fileFormatSize = fileUtils.formatBytes(pasteResourceInfo.fileSize)
        }
    }
}
