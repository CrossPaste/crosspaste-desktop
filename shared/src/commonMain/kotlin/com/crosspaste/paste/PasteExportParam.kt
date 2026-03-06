package com.crosspaste.paste

import okio.BufferedSink

abstract class PasteExportParam(
    val types: Set<Long>,
    val onlyTagged: Boolean,
    val maxFileSize: Long?,
) {

    abstract fun exportBufferedSink(fileName: String): BufferedSink?
}
