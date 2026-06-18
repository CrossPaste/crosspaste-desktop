package com.crosspaste.net

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Guards [AbstractResourcesClient.request]'s failure contract: it returns
 * Result<ClientResponse> and must never let a network/TLS exception escape, or it
 * would crash the caller's coroutine (e.g. the periodic update-check loop). The
 * one exception is [CancellationException], which must propagate so structured
 * concurrency cancellation keeps working — critical for the shared Android/iOS code.
 */
class AbstractResourcesClientTest {

    /** Drives [clientRequest] from a lambda so each test can throw or respond at will. */
    private class FakeResourcesClient(
        private val httpClient: HttpClient,
        private val onRequest: suspend (String, HttpClient) -> HttpResponse,
    ) : AbstractResourcesClient(mockk(relaxed = true)) {

        override val logger = KotlinLogging.logger {}

        override fun getHttpClient(): HttpClient = httpClient

        override suspend fun clientRequest(
            url: String,
            client: HttpClient,
        ): HttpResponse = onRequest(url, client)
    }

    private fun mockClient(status: HttpStatusCode = HttpStatusCode.OK): HttpClient =
        HttpClient(MockEngine { respond("body", status) })

    @Test
    fun `network exception surfaces as failure instead of throwing`() =
        runBlocking {
            val boom = IllegalStateException("unable to find valid certification path to requested target")
            val client = FakeResourcesClient(mockClient()) { _, _ -> throw boom }

            val result = client.request("https://example.test/meta.json")

            assertTrue(result.isFailure)
            assertSame(boom, result.exceptionOrNull())
        }

    @Test
    fun `cancellation is re-thrown, not captured into the result`() =
        runBlocking {
            val client =
                FakeResourcesClient(mockClient()) { _, _ -> throw CancellationException("scope cancelled") }

            assertFailsWith<CancellationException> {
                client.request("https://example.test/meta.json")
            }
            Unit
        }

    @Test
    fun `non-success status returns failure`() =
        runBlocking {
            val client =
                FakeResourcesClient(mockClient(HttpStatusCode.InternalServerError)) { url, http -> http.get(url) }

            val result = client.request("https://example.test/meta.json")

            assertTrue(result.isFailure)
        }

    @Test
    fun `success status returns success`() =
        runBlocking {
            val client =
                FakeResourcesClient(mockClient(HttpStatusCode.OK)) { url, http -> http.get(url) }

            val result = client.request("https://example.test/meta.json")

            assertTrue(result.isSuccess)
        }
}
