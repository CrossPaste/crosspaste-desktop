package com.crosspaste.paste

interface PasteExportParamFactory<T> {

    fun createPasteExportParam(
        types: Set<Long>,
        onlyTagged: Boolean,
        maxFileSize: Long?,
        exportPath: T,
    ): PasteExportParam
}
