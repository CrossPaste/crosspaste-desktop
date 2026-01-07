package com.crosspaste.paste

import okio.Path

class DesktopPasteImportParamFactory : PasteImportParamFactory<Path> {
    override fun createPasteImportParam(importPath: Path): PasteImportParam = DesktopPasteImportParam(importPath)
}
