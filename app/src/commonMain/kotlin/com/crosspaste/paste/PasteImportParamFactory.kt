package com.crosspaste.paste

interface PasteImportParamFactory {

    fun createPasteImportParam(importPath: Any): PasteImportParam
}
