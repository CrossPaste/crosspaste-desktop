package com.crosspaste.module

import okio.Path

data class ModuleLoaderConfig(
    val url: String,
    val fileName: String = url.substringAfterLast("/"),
    val installPath: Path,
    val sha256: String,
    val retryNumber: Int = 2,
)
