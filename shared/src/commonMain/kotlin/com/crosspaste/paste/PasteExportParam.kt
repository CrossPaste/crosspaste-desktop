package com.crosspaste.paste

import okio.BufferedSink

abstract class PasteExportParam(
    val types: Set<Long>,
    val onlyFavorite: Boolean,
    val maxFileSize: Long?,
) {

    abstract fun exportBufferedSink(fileName: String): BufferedSink?
}
