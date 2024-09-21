package com.crosspaste.paste

import okio.Path

interface PasteExporter {

    fun export(path: Path)
}
