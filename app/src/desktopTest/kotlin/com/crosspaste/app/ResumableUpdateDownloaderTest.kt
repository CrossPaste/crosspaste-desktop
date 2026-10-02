package com.crosspaste.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import okio.Path
import okio.Path.Companion.toOkioPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration

class ResumableUpdateDownloaderTest {

    private val url = "https://updates.test/file.bin"
    private val body = ByteArray(300_000) { (it % 251).toByte() }
    private val etag = "\"v1\""

    private fun target(): Path = Files.createTempDirectory("cp-dl").toOkioPath().resolve("file.bin")

    /** Serves [body]; honors Range when [honorRange], replaying every request's headers. */
    private fun server(
        honorRange: Boolean,
        requests: MutableList<Map<String, String?>>,
    ): HttpClient =
        HttpClient(
            MockEngine { request ->
                requests +=
                    mapOf(
                        HttpHeaders.Range to request.headers[HttpHeaders.Range],
                        HttpHeaders.IfRange to request.headers[HttpHeaders.IfRange],
                    )
                val range = request.headers[HttpHeaders.Range]
                if (honorRange && range != null && request.headers[HttpHeaders.IfRange] == etag) {
                    val from = range.removePrefix("bytes=").removeSuffix("-").toInt()
                    respond(
                        content = body.copyOfRange(from, body.size),
                        status = HttpStatusCode.PartialContent,
                        headers =
                            headersOf(
                                HttpHeaders.ETag to listOf(etag),
                                HttpHeaders.ContentRange to listOf("bytes $from-${body.size - 1}/${body.size}"),
                                HttpHeaders.ContentLength to listOf((body.size - from).toString()),
                            ),
                    )
                } else {
                    respond(
                        content = body,
                        status = HttpStatusCode.OK,
                        headers =
                            headersOf(
                                HttpHeaders.ETag to listOf(etag),
                                HttpHeaders.ContentLength to listOf(body.size.toString()),
                            ),
                    )
                }
            },
        )

    private fun seedPartial(
        target: Path,
        bytes: Int,
        withEtag: Boolean,
    ) {
        val part = ResumableUpdateDownloader.partFile(target)
        part.parentFile.mkdirs()
        part.writeBytes(body.copyOfRange(0, bytes))
        if (withEtag) ResumableUpdateDownloader.etagFile(target).writeText(etag)
    }

    @Test
    fun `resumes a partial file with Range and If-Range when the server honors it`() {
        val target = target()
        seedPartial(target, 120_000, withEtag = true)
        val requests = mutableListOf<Map<String, String?>>()
        val downloader = ResumableUpdateDownloader({ server(honorRange = true, requests) }, sleep = {})

        val result = runBlocking { downloader.download(url, target) }

        assertEquals(UpdateDownloadResult.Success, result)
        assertEquals(1, requests.size)
        assertEquals("bytes=120000-", requests[0][HttpHeaders.Range])
        assertEquals(etag, requests[0][HttpHeaders.IfRange])
        assertContentEquals(body, target.toFile().readBytes())
        assertTrue(!ResumableUpdateDownloader.partFile(target).exists())
        assertTrue(!ResumableUpdateDownloader.etagFile(target).exists())
    }

    @Test
    fun `starts over when the server ignores the range request`() {
        val target = target()
        seedPartial(target, 120_000, withEtag = true)
        val requests = mutableListOf<Map<String, String?>>()
        val downloader = ResumableUpdateDownloader({ server(honorRange = false, requests) }, sleep = {})

        val result = runBlocking { downloader.download(url, target) }

        assertEquals(UpdateDownloadResult.Success, result)
        assertEquals("bytes=120000-", requests[0][HttpHeaders.Range])
        assertContentEquals(body, target.toFile().readBytes())
    }

    @Test
    fun `a partial file without an ETag is not resumed`() {
        val target = target()
        seedPartial(target, 120_000, withEtag = false)
        val requests = mutableListOf<Map<String, String?>>()
        val downloader = ResumableUpdateDownloader({ server(honorRange = true, requests) }, sleep = {})

        val result = runBlocking { downloader.download(url, target) }

        assertEquals(UpdateDownloadResult.Success, result)
        assertNull(requests[0][HttpHeaders.Range])
        assertContentEquals(body, target.toFile().readBytes())
    }

    @Test
    fun `reports progress against the full length while resuming`() {
        val target = target()
        seedPartial(target, 120_000, withEtag = true)
        val progress = mutableListOf<Pair<Long, Long?>>()
        val downloader = ResumableUpdateDownloader({ server(honorRange = true, mutableListOf()) }, sleep = {})

        runBlocking { downloader.download(url, target) { read, total -> progress += read to total } }

        assertTrue(progress.first().first > 120_000L)
        assertEquals(body.size.toLong() to body.size.toLong(), progress.last())
    }

    @Test
    fun `throttles to the requested rate using the injected clock`() {
        val target = target()
        var clock = 0L
        val sleeps = mutableListOf<Duration>()
        val downloader =
            ResumableUpdateDownloader(
                httpClient = { server(honorRange = false, mutableListOf()) },
                // Sleeping is the only thing that moves the fake clock, as it would be the
                // dominant cost on a fast link.
                sleep = {
                    sleeps += it
                    clock += it.inWholeNanoseconds
                },
                now = { clock },
            )

        // 300 000 bytes at 100 000 B/s must ask for ~3 s of sleep in total.
        val result = runBlocking { downloader.download(url, target, limitBytesPerSecond = { 100_000L }) }

        assertEquals(UpdateDownloadResult.Success, result)
        val totalMillis = sleeps.sumOf { it.inWholeMilliseconds }
        assertTrue(totalMillis in 2_900..3_100, "expected about 3000 ms of throttling, got $totalMillis")
    }

    @Test
    fun `an unlimited rate never sleeps`() {
        val target = target()
        val sleeps = mutableListOf<Duration>()
        val downloader =
            ResumableUpdateDownloader(
                httpClient = { server(honorRange = false, mutableListOf()) },
                sleep = { sleeps += it },
            )

        runBlocking { downloader.download(url, target, limitBytesPerSecond = { 0L }) }

        assertTrue(sleeps.isEmpty())
    }

    @Test
    fun `parses Content-Range`() {
        assertEquals(120L to 300L, ResumableUpdateDownloader.parseContentRange("bytes 120-299/300"))
        assertEquals(0L to null, ResumableUpdateDownloader.parseContentRange("bytes 0-99/*"))
        assertNull(ResumableUpdateDownloader.parseContentRange("bytes */300"))
        assertNull(ResumableUpdateDownloader.parseContentRange(null))
    }

    @Test
    fun `a stall or pause does not cause an unthrottled catch-up burst afterwards`() {
        val target = target()
        var clock = 0L
        val sleeps = mutableListOf<Duration>()
        val downloader =
            ResumableUpdateDownloader(
                httpClient = { server(honorRange = false, mutableListOf()) },
                sleep = {
                    sleeps += it
                    clock += it.inWholeNanoseconds
                },
                now = { clock },
            )

        // Simulate a 10-second stall before downloading begins
        clock += 10_000_000_000L

        val result = runBlocking { downloader.download(url, target, limitBytesPerSecond = { 100_000L }) }

        assertEquals(UpdateDownloadResult.Success, result)
        assertTrue(sleeps.isNotEmpty(), "expected throttling to still occur after a stall")
        val totalMillis = sleeps.sumOf { it.inWholeMilliseconds }
        assertTrue(totalMillis > 1_500, "throttling should apply to chunks after the stall, slept $totalMillis ms")
    }
}
