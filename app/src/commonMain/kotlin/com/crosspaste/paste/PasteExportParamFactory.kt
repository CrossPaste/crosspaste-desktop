package com.crosspaste.paste

interface PasteExportParamFactory {

    fun createPasteExportParam(
        types: Set<Long>,
        onlyFavorite: Boolean,
        maxFileSize: Long?,
        exportPath: Any,
    ): PasteExportParam
}
