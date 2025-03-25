package com.crosspaste.paste

import okio.Path

class DesktopPasteImportParamFactory : PasteImportParamFactory {
    override fun createPasteImportParam(importPath: Any): PasteImportParam {
        return DesktopPasteImportParam(importPath as Path)
    }
}
