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
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
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

    private fun newUpdater(
        zipBytes: ByteArray,
        checksumBody: String,
        tmp: Path,
    ): WindowsZipUpdater {
        val engine =
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
        val resourcesClient = MockResourcesClient(HttpClient(engine))
        return WindowsZipUpdater(
            appUrls = mockk(relaxed = true),
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
            platform = mockk(relaxed = true),
            metadataFetcher = UpdateMetadataFetcher(resourcesClient),
            baseUrlOverride = baseUrl,
            forcedChannel = WindowsUpdateChannel.PORTABLE_ZIP,
        )
    }

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
}

/** Minimal [ResourcesClient] over a MockEngine-backed [HttpClient]. */
private class MockResourcesClient(
    private val client: HttpClient,
) : ResourcesClient {

    override suspend fun request(url: String): Result<ClientResponse> {
        val response = client.get(url)
        return if (response.status.isSuccess()) {
            Result.success(ClientResponse(response))
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
