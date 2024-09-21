package com.crosspaste.paste

import okio.Path

interface PasteImporter {

    fun import(path: Path)
}
