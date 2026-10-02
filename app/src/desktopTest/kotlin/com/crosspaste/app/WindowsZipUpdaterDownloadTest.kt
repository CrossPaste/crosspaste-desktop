package com.crosspaste.app

import com.crosspaste.net.ClientResponse
import com.crosspaste.net.DownloadProgressListener
import com.crosspaste.net.ResourcesClient
import com.crosspaste.path.AppPathProvider
import com.crosspaste.utils.getFileUtils
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.http.isSuccess
import io.ktor.utils.io.toByteArray
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okio.Path
import okio.Path.Companion.toOkioPath
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Exercises the portable-zip update pipeline (metadata -> checksum race -> download
 * -> SHA-256 verify -> unzip to staging) end-to-end against an in-memory mirror,
 * without publishing a release. The Windows apply/restart step is covered manually
 * (see doc/en/WindowsZipSelfUpdateTest.md).
 */
class WindowsZipUpdaterDownloadTest {

    private val version = "9.9.9"
    private val revision = "9999"
    private val fileName = "crosspaste-$version-$revision-windows-amd64.zip"
    private val baseUrl = "https://updates.test"

    private fun buildZip(): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            // Mirror the real zip's top-level layout (app/, bin/, ...).
            mapOf(
                "app/version.txt" to version,
                "bin/marker.txt" to "marker",
            ).forEach { (name, content) ->
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray())
                zos.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private fun singleSourceEngine(
        zipBytes: ByteArray,
        checksumBody: String,
    ): MockEngine =
        MockEngine { request ->
            val path = request.url.encodedPath
            when {
                path.endsWith("metadata.properties") ->
                    respond("app.version=$version\napp.revision=$revision\n", HttpStatusCode.OK)
                path.endsWith("checksum.txt") ->
                    respond(checksumBody, HttpStatusCode.OK)
                path.endsWith(".zip") ->
                    respond(zipBytes, HttpStatusCode.OK)
                else ->
                    respond("not found", HttpStatusCode.NotFound)
            }
        }

    private fun newUpdater(
        zipBytes: ByteArray,
        checksumBody: String,
        tmp: Path,
        engine: MockEngine = singleSourceEngine(zipBytes, checksumBody),
        baseUrlOverride: String? = baseUrl,
    ): WindowsZipUpdater {
        val httpClient = HttpClient(engine)
        val resourcesClient = MockResourcesClient(httpClient)
        return WindowsZipUpdater(
            appInfo = mockk(relaxed = true) { every { appVersion } returns "1.0.0" },
            appUrls =
                mockk(
                    relaxed = true,
                ) { every { checkMetadataUrl } returns "https://meta.test/metadata.properties" },
            appPathProvider = FakeAppPathProvider(tmp),
            appLaunchState =
                DesktopAppLaunchState(
                    0L,
                    acquiredLock = true,
                    firstLaunch = false,
                    accessibilityPermissions = false,
                    installFrom = null,
                ),
            resourcesClient = resourcesClient,
            downloader = ResumableUpdateDownloader(httpClient = { httpClient }, sleep = {}),
            platform = mockk(relaxed = true),
            metadataFetcher = UpdateMetadataFetcher(resourcesClient),
            baseUrlOverride = baseUrlOverride,
            forcedChannel = WindowsUpdateChannel.PORTABLE_ZIP,
        )
    }

    private val githubHost = "github.com"
    private val ossHost = "oss.crosspaste.com"
    private val etag = "\"mirror-etag\""

    private enum class Mirror { FULL, HONOR_RANGE, DOWN, TRUNCATED }

    /**
     * Serves the real GitHub + OSS mirror layout (no override): metadata from the
     * mocked check URL, checksum.txt from both mirrors, and the zip according to each
     * host's [Mirror] mode. HONOR_RANGE answers a matching If-Range with 206; TRUNCATED
     * promises the full length but sends half, like a dropped connection. Every zip
     * request is recorded as host to Range header.
     */
    private fun mirrorsEngine(
        zipBytes: ByteArray,
        checksumBody: String,
        modes: Map<String, Mirror>,
        zipRequests: MutableList<Pair<String, String?>>,
    ): MockEngine =
        MockEngine { request ->
            val path = request.url.encodedPath
            val host = request.url.host
            when {
                path.endsWith("metadata.properties") ->
                    respond("app.version=$version\napp.revision=$revision\n", HttpStatusCode.OK)
                path.endsWith("checksum.txt") ->
                    respond(checksumBody, HttpStatusCode.OK)
                path.endsWith(".zip") -> {
                    val range = request.headers[HttpHeaders.Range]
                    zipRequests += host to range
                    when (modes.getValue(host)) {
                        Mirror.DOWN -> respond("unavailable", HttpStatusCode.ServiceUnavailable)
                        Mirror.TRUNCATED ->
                            respond(
                                zipBytes.copyOfRange(0, zipBytes.size / 2),
                                HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentLength, zipBytes.size.toString()),
                            )
                        Mirror.HONOR_RANGE if range != null &&
                            request.headers[HttpHeaders.IfRange].let { it == null || it == etag } -> {
                            val from = range.removePrefix("bytes=").removeSuffix("-").toInt()
                            respond(
                                zipBytes.copyOfRange(from, zipBytes.size),
                                HttpStatusCode.PartialContent,
                                headersOf(
                                    HttpHeaders.ETag to listOf(etag),
                                    HttpHeaders.ContentRange to
                                        listOf("bytes $from-${zipBytes.size - 1}/${zipBytes.size}"),
                                    HttpHeaders.ContentLength to listOf((zipBytes.size - from).toString()),
                                ),
                            )
                        }
                        else ->
                            respond(
                                zipBytes,
                                HttpStatusCode.OK,
                                headersOf(
                                    HttpHeaders.ETag to listOf(etag),
                                    HttpHeaders.ContentLength to listOf(zipBytes.size.toString()),
                                ),
                            )
                    }
                }
                else -> respond("not found", HttpStatusCode.NotFound)
            }
        }

    private fun updateDir(tmp: Path): Path = tmp.resolve("user").resolve("update")

    /** A partial download of the zip's first [bytes] bytes attributed to [source]. */
    private fun seedPartial(
        tmp: Path,
        zip: ByteArray,
        bytes: Int,
        source: String,
    ) {
        val dir = updateDir(tmp)
        getFileUtils().createDir(dir, mustCreate = false)
        dir.resolve("$fileName.part").toFile().writeBytes(zip.copyOfRange(0, bytes))
        dir.resolve("$fileName.etag").toFile().writeText(etag)
        dir.resolve("$fileName.source").toFile().writeText(source)
    }

    private val ossBase = "https://$ossHost/$version.$revision/"

    @Test
    fun `downloads, verifies and extracts to staging on a matching checksum`() {
        val zip = buildZip()
        val tmp = Files.createTempDirectory("cp-update-ok").toOkioPath()
        val updater = newUpdater(zip, "${sha256(zip)}  $fileName", tmp)

        runBlocking {
            updater.startDownload()
            val terminal =
                withTimeout(20.seconds) {
                    updater.updateState.first {
                        it is UpdateState.ReadyToApply || it is UpdateState.Failed
                    }
                }

            assertTrue(terminal is UpdateState.ReadyToApply, "expected ReadyToApply but was $terminal")
            assertEquals(version, terminal.version)

            val staged =
                tmp
                    .resolve("user")
                    .resolve("update")
                    .resolve("staging")
                    .resolve("app")
                    .resolve("version.txt")
            assertTrue(getFileUtils().existFile(staged), "staging should contain the extracted files")
        }
    }

    @Test
    fun `fails with checksum mismatch when the digest does not match`() {
        val zip = buildZip()
        val tmp = Files.createTempDirectory("cp-update-bad").toOkioPath()
        val wrongHash = "0".repeat(64)
        val updater = newUpdater(zip, "$wrongHash  $fileName", tmp)

        runBlocking {
            updater.startDownload()
            val terminal =
                withTimeout(20.seconds) {
                    updater.updateState.first {
                        it is UpdateState.ReadyToApply || it is UpdateState.Failed
                    }
                }

            assertTrue(terminal is UpdateState.Failed, "expected Failed but was $terminal")
            assertEquals("update_checksum_mismatch", terminal.reasonKey)
        }
    }

    @Test
    fun `a background failure is quiet and a manual one is not`() {
        val zip = buildZip()
        val tmp = Files.createTempDirectory("cp-update-quiet").toOkioPath()
        val wrongHash = "0".repeat(64)
        val updater = newUpdater(zip, "$wrongHash  $fileName", tmp)

        runBlocking {
            updater.startBackgroundDownload()
            val background =
                withTimeout(20.seconds) {
                    updater.updateState.first { it is UpdateState.Failed }
                }
            assertEquals(false, (background as UpdateState.Failed).manual)

            updater.startDownload()
            val manual =
                withTimeout(20.seconds) {
                    updater.updateState.first { it is UpdateState.Failed && it.manual }
                }
            assertEquals(true, (manual as UpdateState.Failed).manual)
        }
    }

    @Test
    fun `a staged update is restored by the next instance without downloading again`() {
        val zip = buildZip()
        val tmp = Files.createTempDirectory("cp-update-restore").toOkioPath()
        val first = newUpdater(zip, "${sha256(zip)}  $fileName", tmp)

        runBlocking {
            first.startBackgroundDownload()
            withTimeout(20.seconds) { first.updateState.first { it is UpdateState.ReadyToApply } }
        }

        val marker = tmp.resolve("user").resolve("update").resolve(WindowsZipUpdater.READY_MARKER)
        assertTrue(getFileUtils().existFile(marker), "the staged release should be recorded")
        val zipLeftBehind = tmp.resolve("user").resolve("update").resolve(fileName)
        assertTrue(!getFileUtils().existFile(zipLeftBehind), "the zip is dropped once extracted")

        // A new instance (the app relaunched without applying) comes up ready to apply.
        val second = newUpdater(ByteArray(0), "", tmp)
        val restored = second.updateState.value
        assertTrue(restored is UpdateState.ReadyToApply, "expected ReadyToApply but was $restored")
        assertEquals(version, restored.version)
    }

    @Test
    fun `a partial download is resumed from the mirror it came from`() {
        val zip = buildZip()
        val tmp = Files.createTempDirectory("cp-update-affinity").toOkioPath()
        seedPartial(tmp, zip, bytes = 100, source = ossBase)
        val zipRequests = mutableListOf<Pair<String, String?>>()
        val engine =
            mirrorsEngine(
                zip,
                "${sha256(zip)}  $fileName",
                mapOf(githubHost to Mirror.HONOR_RANGE, ossHost to Mirror.HONOR_RANGE),
                zipRequests,
            )
        val updater = newUpdater(zip, "", tmp, engine = engine, baseUrlOverride = null)

        runBlocking {
            updater.startBackgroundDownload()
            withTimeout(20.seconds) { updater.updateState.first { it is UpdateState.ReadyToApply } }
        }

        // Whichever mirror wins the checksum race, the zip itself continues on OSS.
        assertEquals(listOf<Pair<String, String?>>(ossHost to "bytes=100-"), zipRequests)
        val dir = updateDir(tmp)
        assertTrue(
            !getFileUtils().existFile(dir.resolve("$fileName.source")),
            "source file is cleaned up after completion",
        )
        assertTrue(!getFileUtils().existFile(dir.resolve("$fileName.part")), "part file is cleaned up after completion")
    }

    @Test
    fun `a mirror that fails is replaced by the other one, continuing the same partial`() {
        val zip = buildZip()
        val tmp = Files.createTempDirectory("cp-update-fallback").toOkioPath()
        // Pin the first attempt to OSS so the race cannot pick GitHub up front.
        seedPartial(tmp, zip, bytes = 100, source = ossBase)
        val zipRequests = mutableListOf<Pair<String, String?>>()
        val engine =
            mirrorsEngine(
                zip,
                "${sha256(zip)}  $fileName",
                mapOf(githubHost to Mirror.HONOR_RANGE, ossHost to Mirror.DOWN),
                zipRequests,
            )
        val updater = newUpdater(zip, "", tmp, engine = engine, baseUrlOverride = null)

        runBlocking {
            updater.startBackgroundDownload()
            withTimeout(20.seconds) { updater.updateState.first { it is UpdateState.ReadyToApply } }
        }

        // OSS answered 503; the 100 bytes stay and GitHub is asked for the rest with a
        // bare Range, since OSS's ETag no longer applies.
        assertEquals(listOf<Pair<String, String?>>(ossHost to "bytes=100-", githubHost to "bytes=100-"), zipRequests)
    }

    @Test
    fun `a transfer that drops mid-way continues on the other mirror from the same byte`() {
        val zip = buildZip()
        val tmp = Files.createTempDirectory("cp-update-keep-partial").toOkioPath()
        seedPartial(tmp, zip, bytes = 100, source = ossBase)
        val zipRequests = mutableListOf<Pair<String, String?>>()
        val engine =
            mirrorsEngine(
                zip,
                "${sha256(zip)}  $fileName",
                mapOf(githubHost to Mirror.HONOR_RANGE, ossHost to Mirror.TRUNCATED),
                zipRequests,
            )
        val updater = newUpdater(zip, "", tmp, engine = engine, baseUrlOverride = null)

        runBlocking {
            updater.startBackgroundDownload()
            val terminal =
                withTimeout(20.seconds) {
                    updater.updateState.first { it is UpdateState.ReadyToApply || it is UpdateState.Failed }
                }
            assertTrue(terminal is UpdateState.ReadyToApply, "expected ReadyToApply but was $terminal")
        }

        // OSS ignored the range and dropped at half; GitHub picks up exactly there.
        assertEquals(
            listOf<Pair<String, String?>>(ossHost to "bytes=100-", githubHost to "bytes=${zip.size / 2}-"),
            zipRequests,
        )
    }
}

/** Minimal [ResourcesClient] over a MockEngine-backed [HttpClient]. */
private class MockResourcesClient(
    private val client: HttpClient,
) : ResourcesClient {

    override suspend fun request(
        url: String,
        maxBytes: Long,
    ): Result<ClientResponse> {
        val response = client.get(url)
        return if (response.status.isSuccess()) {
            val body = response.bodyAsChannel().toByteArray()
            require(body.size.toLong() <= maxBytes)
            Result.success(ClientResponse(body, response.contentType()))
        } else {
            Result.failure(IllegalStateException("HTTP ${response.status.value}"))
        }
    }

    override suspend fun download(
        url: String,
        path: Path,
        listener: DownloadProgressListener,
    ) {
        val response = client.get(url)
        if (response.status.isSuccess()) {
            getFileUtils().writeFile(path, response.bodyAsChannel())
            val length = response.contentLength() ?: -1L
            listener.onProgress(length.coerceAtLeast(0L), length)
            listener.onSuccess()
        } else {
            listener.onFailure(response.status, null)
        }
    }
}

/** Routes every app path under a single temp [root]. */
private class FakeAppPathProvider(
    root: Path,
) : AppPathProvider {

    override val userHome: Path = root
    override val pasteAppPath: Path = root.resolve("app")
    override val pasteAppJarPath: Path = root.resolve("app").resolve("app")
    override val pasteAppExePath: Path = root.resolve("app").resolve("bin")
    override val pasteUserPath: Path = root.resolve("user")

    override fun resolve(
        fileName: String?,
        appFileType: com.crosspaste.app.AppFileType,
    ): Path = fileName?.let { pasteUserPath.resolve(it) } ?: pasteUserPath
}
