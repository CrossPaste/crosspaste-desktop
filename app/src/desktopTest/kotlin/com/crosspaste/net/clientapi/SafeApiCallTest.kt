package com.crosspaste.net.clientapi

import com.crosspaste.net.exception.DesktopExceptionHandler
import com.crosspaste.net.exception.ExceptionHandler
import com.crosspaste.net.plugin.ClientEncryptPlugin
import com.crosspaste.secure.SecureMessageProcessor
import com.crosspaste.secure.SecureStore
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class SafeApiCallTest {

    private val logger = KotlinLogging.logger {}
    private val exceptionHandler: ExceptionHandler = DesktopExceptionHandler()

    private val mockProcessor =
        mockk<SecureMessageProcessor> {
            every { encrypt(any()) } answers { firstArg<ByteArray>().reversedArray() }
        }
    private val mockSecureStore =
        mockk<SecureStore> {
            coEvery { getMessageProcessor(any()) } returns mockProcessor
        }

    private fun createClient(): HttpClient {
        val mockEngine =
            MockEngine { _ ->
                respond(content = "OK", status = HttpStatusCode.OK)
            }
        return HttpClient(mockEngine) {
            install(ClientEncryptPlugin(mockSecureStore))
        }
    }

    @Test
    fun `ClientEncryptPlugin throw is converted to EncryptFail by safeApiCall`() =
        runBlocking {
            // Pins the cross-layer contract: when the encrypt plugin throws (here triggered
            // by a non-ByteArrayContent / non-NoContent body under `secure: 1`), the API-layer
            // safeApiCall must catch it and return ClientApiResult.EncryptFail rather than
            // letting the throw escape the coroutine.
            val client = createClient()
            val streamingBody =
                object : OutgoingContent.WriteChannelContent() {
                    override val contentType: ContentType = ContentType.Application.OctetStream

                    override suspend fun writeTo(channel: ByteWriteChannel) {
                        channel.writeFully("payload".encodeToByteArray())
                    }
                }

            val result =
                safeApiCall(logger, exceptionHandler) {
                    val response: HttpResponse =
                        client.post("https://localhost/test") {
                            header("targetAppInstanceId", "test-instance")
                            header("secure", "1")
                            setBody(streamingBody)
                        }
                    SuccessResult(response.status)
                }

            assertEquals(EncryptFail, result)
        }
}
