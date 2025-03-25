package com.crosspaste.paste

import com.crosspaste.utils.getFileUtils
import okio.BufferedSource
import okio.Path
import okio.buffer

class DesktopPasteImportParam(
    private val importPath: Path,
) : PasteImportParam() {

    private val fileUtils = getFileUtils()

    override fun importBufferedSource(): BufferedSource? {
        return fileUtils.fileSystem.source(importPath).buffer()
    }
}
