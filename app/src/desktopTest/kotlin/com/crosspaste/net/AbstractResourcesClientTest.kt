package com.crosspaste.net

import com.crosspaste.app.AppFileType
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.utils.FileUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Guards [AbstractResourcesClient.request]'s failure contract: it returns
 * Result<ClientResponse> and must never let a network/TLS exception escape, or it
 * would crash the caller's coroutine (e.g. the periodic update-check loop). The
 * one exception is [CancellationException], which must propagate so structured
 * concurrency cancellation keeps working — critical for the shared Android/iOS code.
 */
class AbstractResourcesClientTest {

    private class FakeResourcesClient(
        private val httpClient: HttpClient,
        userDataPathProvider: UserDataPathProvider = mockk(relaxed = true),
        fileUtils: FileUtils = mockk(relaxed = true),
    ) : AbstractResourcesClient(userDataPathProvider, fileUtils) {

        override val logger = KotlinLogging.logger {}

        override fun getHttpClient(): HttpClient = httpClient
    }

    private fun mockClient(status: HttpStatusCode = HttpStatusCode.OK): HttpClient =
        HttpClient(MockEngine { respond("body", status) })

    @Test
    fun `network exception surfaces as failure instead of throwing`() =
        runBlocking {
            val boom = IllegalStateException("unable to find valid certification path to requested target")
            val client = FakeResourcesClient(HttpClient(MockEngine { throw boom }))

            val result = client.request("https://example.test/meta.json", ResourceRequestLimits.METADATA)

            assertTrue(result.isFailure)
            assertEquals(boom.message, result.exceptionOrNull()?.message)
        }

    @Test
    fun `cancellation is re-thrown, not captured into the result`() =
        runBlocking {
            val client = FakeResourcesClient(HttpClient(MockEngine { throw CancellationException("scope cancelled") }))

            assertFailsWith<CancellationException> {
                client.request("https://example.test/meta.json", ResourceRequestLimits.METADATA)
            }
            Unit
        }

    @Test
    fun `non-success status returns failure`() =
        runBlocking {
            val client = FakeResourcesClient(mockClient(HttpStatusCode.InternalServerError))

            val result = client.request("https://example.test/meta.json", ResourceRequestLimits.METADATA)

            assertTrue(result.isFailure)
        }

    @Test
    fun `success status returns success`() =
        runBlocking {
            val client = FakeResourcesClient(mockClient(HttpStatusCode.OK))

            val result = client.request("https://example.test/meta.json", ResourceRequestLimits.METADATA)

            assertTrue(result.isSuccess)
            assertEquals("body", result.getOrThrow().getBodyAsText())
        }

    @Test
    fun `text response honors the declared charset`() =
        runBlocking {
            val client =
                FakeResourcesClient(
                    HttpClient(
                        MockEngine {
                            respond(
                                content = ByteReadChannel(byteArrayOf(0xE9.toByte())),
                                headers = headersOf(HttpHeaders.ContentType, "text/plain; charset=ISO-8859-1"),
                            )
                        },
                    ),
                )

            val response =
                client
                    .request("https://example.test/text", ResourceRequestLimits.METADATA)
                    .getOrThrow()

            assertEquals("é", response.getBodyAsText())
        }

    @Test
    fun `declared content length above limit returns failure`() =
        runBlocking {
            val client =
                FakeResourcesClient(
                    HttpClient(
                        MockEngine {
                            respond(
                                content = "body",
                                headers = headersOf(HttpHeaders.ContentLength, "5"),
                            )
                        },
                    ),
                )

            val result = client.request("https://example.test/meta.json", maxBytes = 4)

            assertTrue(result.exceptionOrNull() is ResourceResponseTooLargeException)
        }

    @Test
    fun `streamed body above limit returns failure without content length`() =
        runBlocking {
            val client = FakeResourcesClient(mockClient())

            val result = client.request("https://example.test/meta.json", maxBytes = 3)

            assertTrue(result.exceptionOrNull() is ResourceResponseTooLargeException)
        }

    @Test
    fun `download write failure preserves existing target and cleans temp file`() =
        runBlocking {
            val target = "/target/module.bin".toPath()
            val temp = "/temp/download.tmp".toPath()
            val boom = IllegalStateException("write failed")
            val fileUtils = mockk<FileUtils>()
            val listener = mockk<DownloadProgressListener>(relaxed = true)
            val client = downloadClient(fileUtils, temp)
            coEvery { fileUtils.writeFile(temp, any<ByteReadChannel>()) } throws boom
            every { fileUtils.existFile(temp) } returns true
            every { fileUtils.deleteFile(temp) } returns Result.success(Unit)

            client.download("https://example.test/module.bin", target, listener)

            verify { listener.onFailure(HttpStatusCode.OK, boom) }
            coVerify(exactly = 0) { fileUtils.moveFile(any(), any()) }
            verify { fileUtils.deleteFile(temp) }
            verify(exactly = 0) { fileUtils.deleteFile(target) }
        }

    @Test
    fun `download move failure is reported and preserves existing target`() =
        runBlocking {
            val target = "/target/module.bin".toPath()
            val temp = "/temp/download.tmp".toPath()
            val boom = IllegalStateException("move failed")
            val fileUtils = mockk<FileUtils>()
            val listener = mockk<DownloadProgressListener>(relaxed = true)
            val client = downloadClient(fileUtils, temp)
            coEvery { fileUtils.writeFile(temp, any<ByteReadChannel>()) } just Runs
            every { fileUtils.moveFile(temp, target) } returns Result.failure(boom)
            every { fileUtils.existFile(temp) } returns true
            every { fileUtils.deleteFile(temp) } returns Result.success(Unit)

            client.download("https://example.test/module.bin", target, listener)

            verify { listener.onFailure(HttpStatusCode.OK, boom) }
            verify(exactly = 0) { listener.onSuccess() }
            verify { fileUtils.deleteFile(temp) }
            verify(exactly = 0) { fileUtils.deleteFile(target) }
        }

    @Test
    fun `download success is reported only after atomic move`() =
        runBlocking {
            val target = "/target/module.bin".toPath()
            val temp = "/temp/download.tmp".toPath()
            val fileUtils = mockk<FileUtils>()
            val listener = mockk<DownloadProgressListener>(relaxed = true)
            val client = downloadClient(fileUtils, temp)
            coEvery { fileUtils.writeFile(temp, any<ByteReadChannel>()) } just Runs
            every { fileUtils.moveFile(temp, target) } returns Result.success(Unit)

            client.download("https://example.test/module.bin", target, listener)

            verify { fileUtils.moveFile(temp, target) }
            verify { listener.onSuccess() }
            verify(exactly = 0) { listener.onFailure(any(), any()) }
        }

    @Test
    fun `download cancellation cleans temp file and propagates`() =
        runBlocking {
            val target = "/target/module.bin".toPath()
            val temp = "/temp/download.tmp".toPath()
            val cancellation = CancellationException("cancelled")
            val fileUtils = mockk<FileUtils>()
            val listener = mockk<DownloadProgressListener>(relaxed = true)
            val client = downloadClient(fileUtils, temp)
            coEvery { fileUtils.writeFile(temp, any<ByteReadChannel>()) } throws cancellation
            every { fileUtils.existFile(temp) } returns true
            every { fileUtils.deleteFile(temp) } returns Result.success(Unit)

            assertFailsWith<CancellationException> {
                client.download("https://example.test/module.bin", target, listener)
            }

            verify { listener.onFailure(HttpStatusCode.OK, cancellation) }
            verify { fileUtils.deleteFile(temp) }
            verify(exactly = 0) { fileUtils.deleteFile(target) }
        }

    private fun downloadClient(
        fileUtils: FileUtils,
        temp: okio.Path,
    ): FakeResourcesClient {
        every { fileUtils.createRandomFileName() } returns temp.name
        val pathProvider = mockk<UserDataPathProvider>()
        every {
            pathProvider.resolve(
                fileName = temp.name,
                appFileType = AppFileType.TEMP,
            )
        } returns temp
        return FakeResourcesClient(
            httpClient = mockClient(),
            userDataPathProvider = pathProvider,
            fileUtils = fileUtils,
        )
    }
}
