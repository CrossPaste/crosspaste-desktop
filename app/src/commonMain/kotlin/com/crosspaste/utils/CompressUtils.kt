package com.crosspaste.utils

import okio.BufferedSink
import okio.BufferedSource
import okio.Path

expect fun getCompressUtils(): CompressUtils

interface CompressUtils {

    /**
     * Opens a zip written to [targetBufferedSink] that entries are streamed into, so files
     * go straight into the archive without being staged first. Closing it closes the sink.
     */
    fun openZip(targetBufferedSink: BufferedSink): ZipWriter

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

interface ZipWriter : AutoCloseable {

    /** Adds a file at [entryName], or every file under a directory beneath [entryName]/. */
    fun addPath(
        entryName: String,
        path: Path,
    )
}
