package com.crosspaste.utils

import okio.BufferedSink
import okio.BufferedSource
import okio.Path

expect fun getCompressUtils(): CompressUtils

interface CompressUtils {

    fun zipDir(
        sourceDir: Path,
        targetBufferedSink: BufferedSink,
    ): Result<Unit>

    fun zipFile(
        sourceFile: Path,
        targetBufferedSink: BufferedSink,
    ): Result<Unit>

    fun unzip(
        bufferSource: BufferedSource,
        targetDir: Path,
    ): Result<Unit>
}
