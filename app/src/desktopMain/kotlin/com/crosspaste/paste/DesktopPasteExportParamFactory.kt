package com.crosspaste.paste

import okio.Path

class DesktopPasteExportParamFactory : PasteExportParamFactory {
    override fun createPasteExportParam(
        types: Set<Long>,
        onlyFavorite: Boolean,
        maxFileSize: Long?,
        exportPath: Any,
    ): PasteExportParam = DesktopPasteExportParam(types, onlyFavorite, maxFileSize, exportPath as Path)
}
