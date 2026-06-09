package com.crosspaste.app

import com.crosspaste.net.ClientResponse
import com.crosspaste.net.DownloadProgressListener
import com.crosspaste.net.ResourcesClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.runBlocking
import okio.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Verifies the GitHub-first, crosspaste.com-fallback resolution used by both the
 * update banner and the portable-zip downloader.
 */
class UpdateMetadataFetcherTest {

    private val metadataUrl = "https://github.test/metadata.properties"
    private val versionApiUrl = "https://crosspaste.test/api/desktop.json"

    /** Routes each request by URL to a (status, body) pair; absent URLs 404. */
    private fun fetcher(routes: Map<String, Pair<HttpStatusCode, String>>): UpdateMetadataFetcher {
        val engine =
            MockEngine { request ->
                val (status, body) = routes[request.url.toString()] ?: (HttpStatusCode.NotFound to "not found")
                respond(body, status)
            }
        return UpdateMetadataFetcher(RoutingResourcesClient(HttpClient(engine)))
    }

    @Test
    fun `prefers github metadata properties when reachable`() =
        runBlocking {
            val fetcher =
                fetcher(
                    mapOf(
                        metadataUrl to (HttpStatusCode.OK to "app.version=2.0.0\napp.revision=100\n"),
                        // A different version here would surface only if the fallback ran.
                        versionApiUrl to
                            (HttpStatusCode.OK to """{"version":"9.9.9","revision":"999","tag":"9.9.9.999"}"""),
                    ),
                )

            val release = fetcher.fetchLatest(metadataUrl, versionApiUrl)

            assertEquals(ReleaseMetadata("2.0.0", "100", "2.0.0.100"), release)
        }

    @Test
    fun `falls back to desktop json when github is unreachable`() =
        runBlocking {
            val fetcher =
                fetcher(
                    mapOf(
                        metadataUrl to (HttpStatusCode.InternalServerError to "boom"),
                        versionApiUrl to
                            (HttpStatusCode.OK to """{"version":"3.0.0","revision":"300","tag":"3.0.0.300"}"""),
                    ),
                )

            val release = fetcher.fetchLatest(metadataUrl, versionApiUrl)

            assertEquals(ReleaseMetadata("3.0.0", "300", "3.0.0.300"), release)
        }

    @Test
    fun `derives the tag when desktop json omits it`() =
        runBlocking {
            val fetcher =
                fetcher(
                    mapOf(
                        metadataUrl to (HttpStatusCode.InternalServerError to "boom"),
                        versionApiUrl to (HttpStatusCode.OK to """{"version":"3.1.0","revision":"310"}"""),
                    ),
                )

            val release = fetcher.fetchLatest(metadataUrl, versionApiUrl)

            assertEquals(ReleaseMetadata("3.1.0", "310", "3.1.0.310"), release)
        }

    @Test
    fun `honors the tag reported by desktop json over version dot revision`() =
        runBlocking {
            val fetcher =
                fetcher(
                    mapOf(
                        metadataUrl to (HttpStatusCode.InternalServerError to "boom"),
                        // A tag that intentionally diverges from "version.revision".
                        versionApiUrl to
                            (HttpStatusCode.OK to """{"version":"3.0.0","revision":"300","tag":"custom-tag"}"""),
                    ),
                )

            val release = fetcher.fetchLatest(metadataUrl, versionApiUrl)

            assertEquals(ReleaseMetadata("3.0.0", "300", "custom-tag"), release)
        }

    @Test
    fun `does not fall back when the version api url is null`() =
        runBlocking {
            val fetcher =
                fetcher(
                    mapOf(
                        metadataUrl to (HttpStatusCode.InternalServerError to "boom"),
                        versionApiUrl to
                            (HttpStatusCode.OK to """{"version":"3.0.0","revision":"300","tag":"3.0.0.300"}"""),
                    ),
                )

            assertNull(fetcher.fetchLatest(metadataUrl, versionApiUrl = null))
        }

    @Test
    fun `returns null when both sources fail`() =
        runBlocking {
            val fetcher =
                fetcher(
                    mapOf(
                        metadataUrl to (HttpStatusCode.InternalServerError to "boom"),
                        versionApiUrl to (HttpStatusCode.ServiceUnavailable to "down"),
                    ),
                )

            assertNull(fetcher.fetchLatest(metadataUrl, versionApiUrl))
        }
}

/** Minimal [ResourcesClient] over a MockEngine-backed [HttpClient]. */
private class RoutingResourcesClient(
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
    ): Unit = throw UnsupportedOperationException("not used")
}
