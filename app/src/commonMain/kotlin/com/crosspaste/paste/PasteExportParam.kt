package com.crosspaste.paste

import okio.Path

data class PasteExportParam(
    val types: Set<Long>,
    val onlyFavorite: Boolean,
    val maxFileSize: Long?,
    val exportPath: Path,
)
