package com.crosspaste.paste

import okio.Path

class DesktopPasteImportParamFactory : PasteImportParamFactory {
    override fun createPasteImportParam(importPath: Path): PasteImportParam = DesktopPasteImportParam(importPath)
}
