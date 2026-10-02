package com.crosspaste.app

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.request.headers
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentLength
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.delay
import okio.Path
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

/** Outcome of one [ResumableUpdateDownloader.download] call. */
sealed interface UpdateDownloadResult {
    data object Success : UpdateDownloadResult

    data class Failed(
        val status: HttpStatusCode?,
        val cause: Throwable? = null,
    ) : UpdateDownloadResult
}

/**
 * Downloads one large file with two properties the generic [com.crosspaste.net.ResourcesClient]
 * download lacks:
 *
 * - **Resume.** Bytes go to `<target>.part`; the server's ETag is kept next to it. A later
 *   call for the same target sends `Range: bytes=<have>-` plus `If-Range: <etag>`, so an
 *   unchanged file continues where it stopped (HTTP 206) and a changed one is restarted
 *   from zero (HTTP 200). A `.part` without an ETag is resumed with a bare `Range`: the
 *   caller uses that to continue on a different mirror of the same bytes, where the ETag
 *   would not match, and its checksum catches the (unlikely) case of the mirrors
 *   disagreeing. A 206 whose range does not line up with what we have is discarded and
 *   the download restarts.
 * - **Throttle.** [limitBytesPerSecond] is read before every chunk; a positive value caps
 *   the average rate over the current rate window with a sleep after each chunk, zero or
 *   less means unlimited. The window restarts whenever the limit changes so switching from
 *   throttled to unlimited never has to "catch up" and switching back never over-sleeps.
 *
 * The caller still verifies the finished file (SHA-256 in the updater), so resuming
 * cannot weaken integrity. [sleep] is injectable so tests can assert the throttle without
 * waiting on the wall clock.
 */
class ResumableUpdateDownloader(
    private val httpClient: () -> HttpClient,
    private val sleep: suspend (Duration) -> Unit = { delay(it) },
    private val now: () -> Long = System::nanoTime,
) {
    private val logger = KotlinLogging.logger {}

    suspend fun download(
        url: String,
        target: Path,
        limitBytesPerSecond: () -> Long = { 0L },
        onProgress: (bytesRead: Long, contentLength: Long?) -> Unit = { _, _ -> },
    ): UpdateDownloadResult {
        val part = partFile(target)
        val etagFile = etagFile(target)
        part.parentFile?.mkdirs()

        val have = if (part.isFile) part.length() else 0L
        val etag =
            etagFile
                .takeIf { it.isFile }
                ?.readText()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        if (have > 0 && etag == null) {
            logger.info { "Resuming ${part.name} at $have bytes without an ETag; the checksum validates the result" }
        }

        val attempt =
            runCatching { fetch(url, part, etagFile, have, etag, limitBytesPerSecond, onProgress) }
        attempt.exceptionOrNull()?.let { e ->
            if (e is CancellationException) throw e
            logger.warn(e) { "Update download failed: $url" }
            return UpdateDownloadResult.Failed(null, e)
        }
        return when (val outcome = attempt.getOrThrow()) {
            is Fetch.Done -> finish(part, etagFile, target)
            is Fetch.Restart -> {
                // The server would not (or could not) continue our partial file; start over
                // once from zero. A second restart request means something is wrong upstream.
                logger.info { "Server did not honor the resume request (${outcome.status}); restarting from zero" }
                reset(part, etagFile)
                val second =
                    runCatching { fetch(url, part, etagFile, 0L, null, limitBytesPerSecond, onProgress) }
                        .getOrElse { e ->
                            if (e is CancellationException) throw e
                            logger.warn(e) { "Update download failed: $url" }
                            return UpdateDownloadResult.Failed(null, e)
                        }
                when (second) {
                    is Fetch.Done -> finish(part, etagFile, target)
                    is Fetch.Restart -> UpdateDownloadResult.Failed(second.status)
                    is Fetch.Failed -> UpdateDownloadResult.Failed(second.status, second.cause)
                }
            }
            is Fetch.Failed -> UpdateDownloadResult.Failed(outcome.status, outcome.cause)
        }
    }

    /** Moves the completed part file into place and drops the ETag we no longer need. */
    private fun finish(
        part: File,
        etagFile: File,
        target: Path,
    ): UpdateDownloadResult =
        runCatching {
            val targetNio = target.toNioPath()
            targetNio.parent?.let { Files.createDirectories(it) }
            Files.move(
                part.toPath(),
                targetNio,
                StandardCopyOption.REPLACE_EXISTING,
            )
            etagFile.delete()
            UpdateDownloadResult.Success
        }.getOrElse { e ->
            if (e is CancellationException) throw e
            logger.warn(e) { "Cannot move ${part.name} into place at $target" }
            UpdateDownloadResult.Failed(null, e)
        }

    private sealed interface Fetch {
        data object Done : Fetch

        data class Restart(
            val status: HttpStatusCode,
        ) : Fetch

        data class Failed(
            val status: HttpStatusCode?,
            val cause: Throwable? = null,
        ) : Fetch
    }

    private suspend fun fetch(
        url: String,
        part: File,
        etagFile: File,
        offset: Long,
        etag: String?,
        limitBytesPerSecond: () -> Long,
        onProgress: (Long, Long?) -> Unit,
    ): Fetch =
        httpClient()
            .prepareGet(url) {
                if (offset > 0) {
                    headers {
                        append(HttpHeaders.Range, "bytes=$offset-")
                        if (etag != null) append(HttpHeaders.IfRange, etag)
                    }
                }
            }.execute { response ->
                when (response.status) {
                    HttpStatusCode.PartialContent -> {
                        val range = parseContentRange(response.headers[HttpHeaders.ContentRange])
                        if (range == null || range.first != offset) {
                            return@execute Fetch.Restart(response.status)
                        }
                        // A 206 without an ETag must not drop the one we validated against,
                        // or the next interruption would throw the whole part file away.
                        rememberEtag(etagFile, response, keepExisting = true)
                        writeBody(response, part, append = true, offset, range.second, limitBytesPerSecond, onProgress)
                    }
                    HttpStatusCode.OK -> {
                        // Either a fresh download or the server ignored Range: in both
                        // cases the body is the whole file, so start the part file over.
                        rememberEtag(etagFile, response)
                        writeBody(
                            response,
                            part,
                            append = false,
                            0L,
                            response.contentLength(),
                            limitBytesPerSecond,
                            onProgress,
                        )
                    }
                    HttpStatusCode.RequestedRangeNotSatisfiable -> Fetch.Restart(response.status)
                    else -> Fetch.Failed(response.status)
                }
            }

    private fun rememberEtag(
        etagFile: File,
        response: HttpResponse,
        keepExisting: Boolean = false,
    ) {
        val etag = response.headers[HttpHeaders.ETag]?.trim()
        when {
            !etag.isNullOrEmpty() -> etagFile.writeText(etag)
            !keepExisting -> etagFile.delete()
        }
    }

    private suspend fun writeBody(
        response: HttpResponse,
        part: File,
        append: Boolean,
        startOffset: Long,
        total: Long?,
        limitBytesPerSecond: () -> Long,
        onProgress: (Long, Long?) -> Unit,
    ): Fetch {
        val channel = response.bodyAsChannel()
        val buffer = ByteArray(CHUNK_SIZE)
        var written = startOffset
        val throttle = Throttle()
        FileOutputStream(part, append).use { out ->
            while (true) {
                val read = channel.readAvailable(buffer, 0, buffer.size)
                if (read < 0) break
                if (read == 0) continue
                out.write(buffer, 0, read)
                written += read
                onProgress(written, total)
                throttle.afterChunk(read.toLong(), limitBytesPerSecond())
            }
        }
        if (total != null && written != total) {
            return Fetch.Failed(
                null,
                IllegalStateException("Download ended at $written of $total bytes"),
            )
        }
        if (total == null) {
            // Cannot tell a complete body from a truncated one; the caller's checksum
            // decides, and a bad file is simply downloaded again next time.
            logger.warn { "Download finished without a known length ($written bytes); relying on the checksum" }
        }
        return Fetch.Done
    }

    /** Average-rate limiter over a window that restarts whenever the limit changes. */
    private inner class Throttle {
        private var limit = 0L
        private var windowStart = now()
        private var windowBytes = 0L

        suspend fun afterChunk(
            bytes: Long,
            currentLimit: Long,
        ) {
            if (currentLimit != limit) {
                limit = currentLimit
                windowStart = now()
                windowBytes = 0L
            }
            if (limit <= 0L) return
            windowBytes += bytes
            val expectedNanos = windowBytes * NANOS_PER_SECOND / limit
            val elapsedNanos = now() - windowStart
            val behind = expectedNanos - elapsedNanos
            if (behind > 0) {
                sleep(behind.nanoseconds)
            } else {
                // If the link was slower than the cap or paused, do not let "credit"
                // accumulate into an unthrottled burst when throughput recovers.
                windowStart = now()
                windowBytes = 0L
            }
        }
    }

    private fun reset(
        part: File,
        etagFile: File,
    ) {
        part.delete()
        etagFile.delete()
    }

    companion object {
        private const val CHUNK_SIZE = 64 * 1024
        private const val NANOS_PER_SECOND = 1_000_000_000L

        fun partFile(target: Path): File = File(target.toFile().path + ".part")

        fun etagFile(target: Path): File = File(target.toFile().path + ".etag")

        /** Parses `bytes <first>-<last>/<total>`; returns first byte and total (null when `*`). */
        fun parseContentRange(header: String?): Pair<Long, Long?>? {
            val value = header?.trim()?.removePrefix("bytes")?.trim() ?: return null
            val slash = value.indexOf('/')
            if (slash < 0) return null
            val range = value.substring(0, slash)
            val first = range.substringBefore('-').trim().toLongOrNull() ?: return null
            val total = value.substring(slash + 1).trim().let { if (it == "*") null else it.toLongOrNull() }
            return first to total
        }
    }
}
