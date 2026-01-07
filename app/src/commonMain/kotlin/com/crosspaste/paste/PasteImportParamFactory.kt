package com.crosspaste.paste

interface PasteImportParamFactory<T> {

    fun createPasteImportParam(importPath: T): PasteImportParam
}
