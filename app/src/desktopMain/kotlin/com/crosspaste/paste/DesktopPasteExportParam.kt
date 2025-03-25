package com.crosspaste.paste

import com.crosspaste.utils.getFileUtils
import okio.BufferedSink
import okio.Path
import okio.buffer

class DesktopPasteExportParam(
    types: Set<Long>,
    onlyFavorite: Boolean,
    maxFileSize: Long?,
    private val exportPath: Path,
) : PasteExportParam(types, onlyFavorite, maxFileSize) {

    private val fileUtils = getFileUtils()

    override fun exportBufferedSink(fileName: String): BufferedSink? {
        val targetZipFile = exportPath.resolve(fileName)
        return fileUtils.fileSystem.sink(targetZipFile).buffer()
    }
}
