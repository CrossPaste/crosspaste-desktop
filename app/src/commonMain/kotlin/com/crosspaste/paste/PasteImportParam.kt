package com.crosspaste.paste

import okio.BufferedSource

abstract class PasteImportParam {

    abstract fun importBufferedSource(): BufferedSource?
}
