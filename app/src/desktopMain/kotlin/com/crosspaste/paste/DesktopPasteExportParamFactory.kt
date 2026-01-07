package com.crosspaste.paste

import okio.Path

class DesktopPasteExportParamFactory : PasteExportParamFactory<Path> {
    override fun createPasteExportParam(
        types: Set<Long>,
        onlyFavorite: Boolean,
        maxFileSize: Long?,
        exportPath: Path,
    ): PasteExportParam = DesktopPasteExportParam(types, onlyFavorite, maxFileSize, exportPath)
}
