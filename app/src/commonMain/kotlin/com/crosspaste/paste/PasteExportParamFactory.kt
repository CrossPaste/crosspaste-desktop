package com.crosspaste.paste

interface PasteExportParamFactory<T> {

    fun createPasteExportParam(
        types: Set<Long>,
        onlyFavorite: Boolean,
        maxFileSize: Long?,
        exportPath: T,
    ): PasteExportParam
}
