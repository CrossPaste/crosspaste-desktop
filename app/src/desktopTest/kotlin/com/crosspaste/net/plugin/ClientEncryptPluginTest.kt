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
import io.ktor.http.contentType
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
    fun `throws PasteException for unsupported content type when secure header is set`() =
        runBlocking {
            val client = createClient()

            val exception =
                assertFailsWith<PasteException> {
                    // GET request with secure headers produces a non-ByteArrayContent body,
                    // which should throw PasteException instead of silently sending plaintext
                    client.get("https://localhost/test") {
                        header("targetAppInstanceId", "test-instance")
                        header("secure", "1")
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
