package com.crosspaste.net.clientapi

import com.crosspaste.config.AppConfig
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.dto.push.PushCompleteResponse
import com.crosspaste.dto.push.PushHeaders
import com.crosspaste.dto.push.PushPrepareResponse
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.PasteClient
import com.crosspaste.net.exception.DesktopExceptionHandler
import com.crosspaste.net.exception.ExceptionHandler
import com.crosspaste.paste.PasteCollection
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteType
import com.crosspaste.paste.item.CreatePasteItemHelper.createFilesPasteItem
import com.crosspaste.utils.HostAndPort
import com.crosspaste.utils.buildUrl
import com.crosspaste.utils.getJsonUtils
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.reflect.TypeInfo
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Tests PushClientApi against a Ktor MockEngine. PasteClient is mocked and its
 * post/postBinary delegate to a real HttpClient backed by the MockEngine, so
 * we exercise URL building + header wiring + content type + body encoding
 * without standing up a real server.
 */
class PushClientApiTest {

    private val json = getJsonUtils().JSON
    private val exceptionHandler: ExceptionHandler = DesktopExceptionHandler()

    private fun configManager(encrypt: Boolean = false): CommonConfigManager {
        val cfg = mockk<AppConfig>(relaxed = true)
        every { cfg.enableEncryptSync } returns encrypt
        return mockk<CommonConfigManager>(relaxed = true).also {
            every { it.getCurrentConfig() } returns cfg
        }
    }

    /**
     * Builds a mocked PasteClient whose `post` and `postBinary` route through
     * a real HttpClient(MockEngine). Tests inspect `engine.requestHistory`
     * after the call to assert headers, body, and URL.
     */
    private fun buildClient(
        responder: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): Pair<PasteClient, MockEngine> {
        val engine = MockEngine(responder)
        val realClient =
            HttpClient(engine) {
                install(ContentNegotiation) { json(json, ContentType.Application.Json) }
            }
        val client = mockk<PasteClient>()
        coEvery {
            client.post(any<Any>(), any<TypeInfo>(), any<Long>(), any(), any())
        } coAnswers {
            val message: Any = firstArg()
            val headersBuilder: HeadersBuilder.() -> Unit = arg(3)
            val urlBuilder: URLBuilder.() -> Unit = arg(4)
            realClient.post {
                headers(headersBuilder)
                contentType(ContentType.Application.Json)
                url { urlBuilder() }
                setBody(message)
            }
        }
        coEvery {
            client.postBinary(any(), any(), any(), any(), any())
        } coAnswers {
            val bytes: ByteArray = firstArg()
            val ct: ContentType = thirdArg()
            val headersBuilder: HeadersBuilder.() -> Unit = arg(3)
            val urlBuilder: URLBuilder.() -> Unit = arg(4)
            realClient.post {
                headers(headersBuilder)
                url { urlBuilder() }
                setBody(ByteArrayContent(bytes, contentType = ct))
            }
        }
        return client to engine
    }

    private fun toUrl(): URLBuilder.() -> Unit =
        {
            buildUrl(HostAndPort("127.0.0.1", 13129))
        }

    private fun samplePasteData(): PasteData =
        PasteData(
            appInstanceId = "sender-instance",
            pasteAppearItem =
                createFilesPasteItem(
                    relativePathList = emptyList(),
                    fileInfoTreeMap = emptyMap(),
                ),
            pasteCollection = PasteCollection(emptyList()),
            pasteType = PasteType.FILE_TYPE.type,
            source = null,
            size = 0L,
            hash = "h",
        )

    // ---------- preparePush ----------

    @Test
    fun preparePush_success_returnsParsedResponse() =
        runBlocking {
            val expected =
                PushPrepareResponse(
                    pasteId = 42L,
                    chunkCount = 3,
                    chunkSize = 1024L,
                    sessionToken = "tok",
                    needIcon = true,
                )
            val (client, engine) =
                buildClient { _ ->
                    respond(
                        content = json.encodeToString(expected),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val api = PushClientApi(client, configManager(encrypt = false), exceptionHandler)

            val result = api.preparePush(samplePasteData(), "remote-1", toUrl())

            assertTrue(result is SuccessResult, "expected SuccessResult, got $result")
            assertEquals(expected, result.getResult<PushPrepareResponse>())

            val request = engine.requestHistory.single()
            assertEquals("/sync/paste", request.url.encodedPath)
            assertEquals("remote-1", request.headers["targetAppInstanceId"])
            assertEquals(PushHeaders.SYNC_MODE_PUSH, request.headers[PushHeaders.SYNC_MODE])
            assertNull(request.headers["secure"], "encrypt disabled → no secure header")
        }

    @Test
    fun preparePush_setsSecureHeader_whenEncryptionEnabled() =
        runBlocking {
            val expected = PushPrepareResponse(1L, 1, 1024L, "t", false)
            val (client, engine) =
                buildClient { _ ->
                    respond(
                        content = json.encodeToString(expected),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val api = PushClientApi(client, configManager(encrypt = true), exceptionHandler)

            api.preparePush(samplePasteData(), "remote-1", toUrl())
            assertEquals("1", engine.requestHistory.single().headers["secure"])
        }

    @Test
    fun preparePush_serverError_returnsFailureResult() =
        runBlocking {
            val fail = FailResponse(StandardErrorCode.PUSH_SESSION_REJECTED.getCode(), "no capacity")
            val (client, _) =
                buildClient { _ ->
                    respond(
                        content = json.encodeToString(fail),
                        status = HttpStatusCode.BadRequest,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val api = PushClientApi(client, configManager(encrypt = false), exceptionHandler)

            val result = api.preparePush(samplePasteData(), "remote-1", toUrl())
            assertTrue(result is FailureResult, "expected FailureResult, got $result")
            assertEquals(
                StandardErrorCode.PUSH_SESSION_REJECTED.toErrorCode(),
                result.exception.getErrorCode(),
            )
        }

    // ---------- pushChunk ----------

    @Test
    fun pushChunk_success_carriesChunkHeaders() =
        runBlocking {
            val (client, engine) =
                buildClient { _ ->
                    respond(content = "", status = HttpStatusCode.OK)
                }
            val api = PushClientApi(client, configManager(encrypt = false), exceptionHandler)

            val result =
                api.pushChunk(
                    pasteId = 100L,
                    chunkIndex = 2,
                    sessionToken = "tok",
                    targetAppInstanceId = "remote-1",
                    chunkBytes = byteArrayOf(1, 2, 3),
                    toUrl = toUrl(),
                )

            assertTrue(result is SuccessResult, "got $result")
            val req = engine.requestHistory.single()
            assertEquals("/sync/file/push", req.url.encodedPath)
            assertEquals("100", req.headers[PushHeaders.PASTE_ID])
            assertEquals("2", req.headers[PushHeaders.CHUNK_INDEX])
            assertEquals("tok", req.headers[PushHeaders.SESSION_TOKEN])
            val bytes =
                when (val body = req.body) {
                    is OutgoingContent.ByteArrayContent -> body.bytes()
                    else -> fail("expected ByteArrayContent, got ${body::class}")
                }
            assertContentEquals(byteArrayOf(1, 2, 3), bytes)
        }

    @Test
    fun pushChunk_setsSecureHeader_whenEncryptionEnabled() =
        runBlocking {
            val (client, engine) =
                buildClient { _ ->
                    respond(content = "", status = HttpStatusCode.OK)
                }
            val api = PushClientApi(client, configManager(encrypt = true), exceptionHandler)

            api.pushChunk(1L, 0, "t", "remote-1", byteArrayOf(0xA), toUrl())
            assertEquals("1", engine.requestHistory.single().headers["secure"])
        }

    @Test
    fun pushChunk_500_returnsFailureWithUploadCode() =
        runBlocking {
            val (client, _) =
                buildClient { _ ->
                    respondError(HttpStatusCode.InternalServerError)
                }
            val api = PushClientApi(client, configManager(encrypt = false), exceptionHandler)
            val result =
                api.pushChunk(7L, 0, "t", "remote-1", byteArrayOf(0), toUrl())

            assertTrue(result is FailureResult, "got $result")
            assertEquals(
                StandardErrorCode.PUSH_CHUNK_UPLOAD_FAIL.toErrorCode(),
                result.exception.getErrorCode(),
            )
        }

    // ---------- completePush ----------

    @Test
    fun completePush_success_returnsParsedResponse() =
        runBlocking {
            val expected = PushCompleteResponse(missingChunks = listOf(2, 5))
            val (client, engine) =
                buildClient { _ ->
                    respond(
                        content = json.encodeToString(expected),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val api = PushClientApi(client, configManager(encrypt = false), exceptionHandler)

            val result =
                api.completePush(
                    pasteId = 99L,
                    sessionToken = "tk",
                    targetAppInstanceId = "remote-1",
                    toUrl = toUrl(),
                )

            assertTrue(result is SuccessResult, "got $result")
            assertEquals(expected, result.getResult<PushCompleteResponse>())
            val req = engine.requestHistory.single()
            assertEquals("/sync/paste/push/complete", req.url.encodedPath)
            assertEquals("99", req.headers[PushHeaders.PASTE_ID])
            assertEquals("tk", req.headers[PushHeaders.SESSION_TOKEN])
        }

    // ---------- pushIcon ----------

    @Test
    fun pushIcon_success_postsToIconPushPath() =
        runBlocking {
            val (client, engine) =
                buildClient { _ ->
                    respond(content = "", status = HttpStatusCode.OK)
                }
            val api = PushClientApi(client, configManager(encrypt = false), exceptionHandler)

            val result =
                api.pushIcon(
                    source = "Slack",
                    targetAppInstanceId = "remote-1",
                    iconBytes = byteArrayOf(1, 2),
                    toUrl = toUrl(),
                )
            assertTrue(result is SuccessResult, "got $result")
            assertEquals(
                "/sync/icon/push/Slack",
                engine.requestHistory
                    .single()
                    .url.encodedPath,
            )
        }

    private fun assertContentEquals(
        expected: ByteArray,
        actual: ByteArray,
    ) {
        assertEquals(expected.toList(), actual.toList())
    }
}
