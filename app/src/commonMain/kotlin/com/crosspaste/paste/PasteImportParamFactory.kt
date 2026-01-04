package com.crosspaste.paste

import okio.Path

interface PasteImportParamFactory {

    fun createPasteImportParam(importPath: Path): PasteImportParam
}
