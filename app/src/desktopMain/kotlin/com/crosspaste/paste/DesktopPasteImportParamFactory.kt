package com.crosspaste.paste

import okio.Path

class DesktopPasteImportParamFactory : PasteImportParamFactory {
    override fun createPasteImportParam(importPath: Any): PasteImportParam = DesktopPasteImportParam(importPath as Path)
}
