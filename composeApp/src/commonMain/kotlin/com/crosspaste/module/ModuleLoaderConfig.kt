package com.crosspaste.module

import okio.Path

data class ModuleLoaderConfig(
    val url: String,
    val installPath: Path,
    val sha256: String,
    val retryNumber: Int = 2,
)
