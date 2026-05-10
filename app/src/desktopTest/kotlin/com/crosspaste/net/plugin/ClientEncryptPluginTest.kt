package com.crosspaste.net.plugin

import com.crosspaste.exception.PasteException
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.secure.SecureMessageProcessor
import com.crosspaste.secure.SecureStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ClientEncryptPluginTest {

    private val mockProcessor =
        mockk<SecureMessageProcessor> {
            every { encrypt(any()) } answers {
                val input = firstArg<ByteArray>()
                // Simple "encryption": reverse the bytes for verification
                input.reversedArray()
            }
        }

    private val mockSecureStore =
        mockk<SecureStore> {
            coEvery { getMessageProcessor(any()) } returns mockProcessor
        }

    private fun createClient(secureStore: SecureStore = mockSecureStore): HttpClient {
        val mockEngine =
            MockEngine { request ->
                respond(
                    content = "OK",
                    status = HttpStatusCode.OK,
                )
            }
        return HttpClient(mockEngine) {
            install(ClientEncryptPlugin(secureStore))
        }
    }

    @Test
    fun `encrypt succeeds with ByteArrayContent body`() =
        runBlocking {
            val client = createClient()

            // Should not throw - ByteArrayContent is supported
            val response =
                client.post("https://localhost/test") {
                    header("targetAppInstanceId", "test-instance")
                    header("secure", "1")
                    contentType(ContentType.Application.Json)
                    setBody("""{"key":"value"}""")
                }

            assertEquals(HttpStatusCode.OK, response.status)
        }

    @Test
    fun `no encryption when secure header is absent`() =
        runBlocking {
            val neverCalledStore =
                mockk<SecureStore> {
                    coEvery { getMessageProcessor(any()) } throws AssertionError("Should not be called")
                }
            val client = createClient(neverCalledStore)

            // No secure header - should pass through without encryption
            val response =
                client.post("https://localhost/test") {
                    header("targetAppInstanceId", "test-instance")
                    contentType(ContentType.Application.Json)
                    setBody("""{"key":"value"}""")
                }

            assertEquals(HttpStatusCode.OK, response.status)
        }

    @Test
    fun `no encryption when targetAppInstanceId header is absent`() =
        runBlocking {
            val neverCalledStore =
                mockk<SecureStore> {
                    coEvery { getMessageProcessor(any()) } throws AssertionError("Should not be called")
                }
            val client = createClient(neverCalledStore)

            // No targetAppInstanceId - should pass through without encryption
            val response =
                client.post("https://localhost/test") {
                    header("secure", "1")
                    contentType(ContentType.Application.Json)
                    setBody("""{"key":"value"}""")
                }

            assertEquals(HttpStatusCode.OK, response.status)
        }

    @Test
    fun `bodyless GET with secure header passes through without invoking encryption`() =
        runBlocking {
            // GET / DELETE etc. have no request body to encrypt — the secure header
            // signals that the *response* should be encrypted (handled by
            // ClientDecryptPlugin). The encrypt plugin must skip these instead of
            // aborting the request, otherwise pull endpoints crash on K/N iOS where
            // the throwing coroutine has no exception handler installed.
            val neverCalledStore =
                mockk<SecureStore> {
                    coEvery { getMessageProcessor(any()) } throws
                        AssertionError("Should not encrypt bodyless request")
                }
            val client = createClient(neverCalledStore)

            val response =
                client.get("https://localhost/test") {
                    header("targetAppInstanceId", "test-instance")
                    header("secure", "1")
                }

            assertEquals(HttpStatusCode.OK, response.status)
        }

    @Test
    fun `throws PasteException for unsupported body type when secure header is set`() =
        runBlocking {
            // Streaming bodies (WriteChannelContent) are neither ByteArrayContent nor
            // NoContent. Reaching this branch means a request was added that the encrypt
            // plugin doesn't know how to encrypt — fail loud rather than silently send
            // plaintext under a `secure: 1` header. The API layer's safeApiCall converts
            // this into ClientApiResult.EncryptFail for the caller.
            val neverCalledStore =
                mockk<SecureStore> {
                    coEvery { getMessageProcessor(any()) } throws
                        AssertionError("Should not reach encryption for unsupported body type")
                }
            val client = createClient(neverCalledStore)

            val streamingBody =
                object : OutgoingContent.WriteChannelContent() {
                    override val contentType: ContentType = ContentType.Application.OctetStream

                    override suspend fun writeTo(channel: ByteWriteChannel) {
                        channel.writeFully("payload".encodeToByteArray())
                    }
                }

            val exception =
                assertFailsWith<PasteException> {
                    client.post("https://localhost/test") {
                        header("targetAppInstanceId", "test-instance")
                        header("secure", "1")
                        setBody(streamingBody)
                    }
                }

            assertEquals(StandardErrorCode.ENCRYPT_FAIL.toErrorCode(), exception.getErrorCode())
            assertTrue(exception.message?.contains("Unsupported content type for encryption") == true)
        }

    @Test
    fun `propagates PasteException when key not found`() =
        runBlocking {
            val keyMissingStore =
                mockk<SecureStore> {
                    coEvery { getMessageProcessor(any()) } throws
                        PasteException(
                            StandardErrorCode.ENCRYPT_FAIL.toErrorCode(),
                            "Crypt public key not found",
                        )
                }
            val client = createClient(keyMissingStore)

            val exception =
                assertFailsWith<PasteException> {
                    client.post("https://localhost/test") {
                        header("targetAppInstanceId", "unknown-instance")
                        header("secure", "1")
                        contentType(ContentType.Application.Json)
                        setBody("""{"key":"value"}""")
                    }
                }

            assertEquals(StandardErrorCode.ENCRYPT_FAIL.toErrorCode(), exception.getErrorCode())
            assertTrue(exception.message?.contains("Crypt public key not found") == true)
        }
}
