package com.crosspaste.cli.commands

import com.crosspaste.cli.api.CliRawImage
import com.crosspaste.cli.platform.PNG_SIGNATURE_SIZE
import com.crosspaste.cli.platform.TerminalImageProtocol
import com.crosspaste.cli.platform.flushStdout
import com.crosspaste.cli.platform.isPng
import com.crosspaste.cli.platform.prepareStdoutForBinary
import com.crosspaste.cli.platform.writeBytesToStdout
import com.crosspaste.cli.platform.writeItermInlineImage
import com.crosspaste.cli.platform.writeKittyInlineImage
import com.crosspaste.cli.platform.writeSixelInlineImage
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
 * Thrown by a sixelFetch when the app reports its image decode backend is
 * unavailable (HTTP 503, e.g. missing native graphics libraries on a headless
 * server, #4854). Unlike a null fetch result — a per-image skip — this is a
 * process-wide condition: the renderer stops fetching and surfaces the
 * server's actionable message once instead of failing image by image.
 */
internal class PreviewUnavailableException(
    message: String,
) : Exception(message)

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
 * [maxCandidates] inspected paths, with at most [maxTotalBytes] payload bytes
 * read in total per invocation. A file paste may legitimately reference
 * thousands of entries, and previewing must never turn one `paste` into a
 * multi-gigabyte read. Skipped images are summarized in a single note; their
 * paths are already listed in the detail view.
 *
 * ITERM/KITTY transmit the stored file as-is; SIXEL instead renders pixels
 * from [sixelFetch] — the app's transcode endpoint (the CLI never decodes
 * image files). A null fetch result (older app without the endpoint, or an
 * undecodable file) skips the image, same as an unreadable file.
 */
internal class InlineImageRenderer(
    private val protocol: TerminalImageProtocol?,
    private val emit: (String) -> Unit,
    private val note: (String) -> Unit,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    private val maxImages: Int = MAX_PREVIEW_IMAGES,
    private val maxCandidates: Int = MAX_PREVIEW_CANDIDATES,
    private val maxTotalBytes: Long = MAX_PREVIEW_TOTAL_BYTES,
    private val sixelFetch: suspend (index: Int) -> CliRawImage? = { null },
) {
    companion object {
        internal const val MAX_PREVIEW_IMAGES = 4
        internal const val MAX_PREVIEW_CANDIDATES = 16
        internal const val MAX_PREVIEW_TOTAL_BYTES = 20L * 1024 * 1024
    }

    suspend fun render(paths: List<String>) {
        val protocol = protocol ?: return
        var rendered = 0
        var inspected = 0
        var remainingBudget = maxTotalBytes
        var skipped = 0
        var unavailableMessage: String? = null
        for ((index, path) in paths.withIndex()) {
            if (rendered >= maxImages || inspected >= maxCandidates || remainingBudget <= 0) {
                skipped += paths.size - index
                break
            }
            inspected++
            val consumed =
                try {
                    when (protocol) {
                        TerminalImageProtocol.ITERM,
                        TerminalImageProtocol.KITTY,
                        -> emitFromFile(protocol, path, remainingBudget)
                        TerminalImageProtocol.SIXEL -> emitFromFetch(index, remainingBudget)
                    }
                } catch (e: PreviewUnavailableException) {
                    // Process-wide backend failure: every further fetch would
                    // fail the same way, so stop and report once
                    unavailableMessage = e.message
                    skipped += paths.size - index
                    break
                }
            if (consumed == null) {
                skipped++
                continue
            }
            remainingBudget -= consumed
            emit("\n")
            rendered++
        }
        if (skipped > 0) {
            note("($skipped image(s) not previewed; file paths listed above)")
        }
        unavailableMessage?.let { note(it) }
    }

    /** Returns the payload bytes consumed, or null when the image is skipped. */
    private fun emitFromFile(
        protocol: TerminalImageProtocol,
        path: String,
        budget: Long,
    ): Long? {
        val bytes =
            readImageWithinBudget(
                path = path,
                budget = budget,
                requirePng = protocol == TerminalImageProtocol.KITTY,
                fileSystem = fileSystem,
            ) ?: return null
        when (protocol) {
            TerminalImageProtocol.ITERM -> writeItermInlineImage(path.toPath().name, bytes, emit)
            else -> writeKittyInlineImage(bytes, emit)
        }
        return bytes.size.toLong()
    }

    /** Returns the RGBA bytes consumed, or null when the image is skipped. */
    private suspend fun emitFromFetch(
        index: Int,
        budget: Long,
    ): Long? {
        val image = sixelFetch(index) ?: return null
        // The size is only known after the fetch, so an over-budget image
        // costs one wasted transcode round trip — acceptable, since the
        // request box bounds every reply to a few MB
        if (image.rgba.size > budget) return null
        writeSixelInlineImage(image.rgba, image.width, image.height, emit)
        return image.rgba.size.toLong()
    }
}

/** Shared budget-guarded image read (used by `paste` previews and `pick`). */
internal fun readImageWithinBudget(
    path: String,
    budget: Long,
    requirePng: Boolean,
    fileSystem: FileSystem = FileSystem.SYSTEM,
): ByteArray? {
    val okioPath = path.toPath()
    return try {
        val size = fileSystem.metadata(okioPath).size ?: return null
        if (size <= 0 || size > budget || size > Int.MAX_VALUE) return null
        fileSystem.source(okioPath).buffer().use { source ->
            if (requirePng) {
                if (!source.request(PNG_SIGNATURE_SIZE.toLong())) return null
                val signature = source.peek().readByteArray(PNG_SIGNATURE_SIZE.toLong())
                if (!isPng(signature)) return null
            }

            val bytes = ByteArray(size.toInt())
            source.readFully(bytes)
            // Reference-backed files can change after metadata(). Reject a
            // file that grew instead of reading beyond the declared budget.
            if (!source.exhausted()) return null
            bytes
        }
    } catch (_: IOException) {
        null
    }
}
