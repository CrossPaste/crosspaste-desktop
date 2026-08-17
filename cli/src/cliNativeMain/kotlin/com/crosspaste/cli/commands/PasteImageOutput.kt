package com.crosspaste.cli.commands

import com.crosspaste.cli.platform.TerminalImageProtocol
import com.crosspaste.cli.platform.flushStdout
import com.crosspaste.cli.platform.isPng
import com.crosspaste.cli.platform.prepareStdoutForBinary
import com.crosspaste.cli.platform.writeBytesToStdout
import com.crosspaste.cli.platform.writeItermInlineImage
import com.crosspaste.cli.platform.writeKittyInlineImage
import okio.FileSystem
import okio.IOException
import okio.Path.Companion.toPath
import okio.buffer
import okio.use

/** What `paste --raw` should do for an image paste. */
internal sealed interface RawImageAction {
    /** The running app predates the filePaths capability. */
    data object MissingPaths : RawImageAction

    /** Concatenated image bytes are useless; refuse and show the paths. */
    data class TooManyImages(
        val paths: List<String>,
    ) : RawImageAction

    data class StreamSingle(
        val path: String,
    ) : RawImageAction
}

internal fun resolveRawImageAction(filePaths: List<String>): RawImageAction =
    when {
        filePaths.isEmpty() -> RawImageAction.MissingPaths
        filePaths.size > 1 -> RawImageAction.TooManyImages(filePaths)
        else -> RawImageAction.StreamSingle(filePaths.single())
    }

/**
 * Streams a stored image file to stdout in 64 KiB chunks. All effects are
 * injectable so tests can pin the byte fidelity and the failure paths;
 * production uses the binary-safe stdout primitives (on Windows the CRT is
 * switched to binary mode first). Write or flush failures throw and become
 * exit 1 in runCli — a partial pipe must never look like success.
 */
internal class ImageByteStreamer(
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    private val prepareBinary: () -> Unit = ::prepareStdoutForBinary,
    private val writeChunk: (ByteArray, Int) -> Unit = ::writeBytesToStdout,
    private val flush: () -> Unit = ::flushStdout,
) {
    /** @throws IOException when the stored file cannot be read */
    fun stream(path: String) {
        prepareBinary()
        fileSystem.source(path.toPath()).buffer().use { source ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = source.read(buffer, 0, buffer.size)
                if (read == -1) break
                writeChunk(buffer, read)
            }
        }
        flush()
    }
}

/**
 * Inline previews with hard resource ceilings: at most [maxImages] images and
 * [maxTotalBytes] bytes read in total per invocation — a file paste may
 * legitimately reference thousands of entries, and previewing must never turn
 * one `paste` into a multi-gigabyte read. Skipped images are summarized in a
 * single note; their paths are already listed in the detail view.
 */
internal class InlineImageRenderer(
    private val protocol: TerminalImageProtocol?,
    private val emit: (String) -> Unit,
    private val note: (String) -> Unit,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    private val maxImages: Int = MAX_PREVIEW_IMAGES,
    private val maxTotalBytes: Long = MAX_PREVIEW_TOTAL_BYTES,
) {
    companion object {
        internal const val MAX_PREVIEW_IMAGES = 4
        internal const val MAX_PREVIEW_TOTAL_BYTES = 20L * 1024 * 1024
    }

    fun render(paths: List<String>) {
        val protocol = protocol ?: return
        var rendered = 0
        var remainingBudget = maxTotalBytes
        var skipped = 0
        for (path in paths) {
            if (rendered >= maxImages) {
                skipped++
                continue
            }
            val bytes = readWithinBudget(path, remainingBudget)
            if (bytes == null) {
                skipped++
                continue
            }
            // Kitty's f=100 transfer is PNG-only; other formats keep the path fallback
            if (protocol == TerminalImageProtocol.KITTY && !isPng(bytes)) {
                skipped++
                continue
            }
            when (protocol) {
                TerminalImageProtocol.ITERM -> writeItermInlineImage(path.toPath().name, bytes, emit)
                TerminalImageProtocol.KITTY -> writeKittyInlineImage(bytes, emit)
            }
            emit("\n")
            rendered++
            remainingBudget -= bytes.size
        }
        if (skipped > 0) {
            note("($skipped image(s) not previewed; file paths listed above)")
        }
    }

    private fun readWithinBudget(
        path: String,
        budget: Long,
    ): ByteArray? {
        val okioPath = path.toPath()
        return try {
            val size = fileSystem.metadata(okioPath).size ?: return null
            if (size > budget) return null
            fileSystem.read(okioPath) { readByteArray() }
        } catch (_: IOException) {
            null
        }
    }
}
