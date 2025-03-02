package com.crosspaste.utils

import okio.Path

expect fun getCompressUtils(): CompressUtils

interface CompressUtils {

    fun zipDir(
        sourceDir: Path,
        targetZipPath: Path,
    ): Result<Unit>

    fun zipFile(
        sourceFile: Path,
        targetZipPath: Path,
    ): Result<Unit>

    fun unzip(
        zipFile: Path,
        targetDir: Path,
    ): Result<Unit>
}
