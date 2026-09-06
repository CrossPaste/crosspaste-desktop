package com.crosspaste.net.routing

import com.crosspaste.app.AppInfo
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.dto.push.PushCompleteResponse
import com.crosspaste.dto.push.PushHeaders
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.net.clientapi.FailResponse
import com.crosspaste.paste.PasteCollection
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteState
import com.crosspaste.paste.PasteType
import com.crosspaste.paste.PasteboardService
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.presist.FilesIndexBuilder
import com.crosspaste.sync.PushSessionManager
import com.crosspaste.sync.SyncHandler
import com.crosspaste.utils.HEADER_APP_INSTANCE_ID
import com.crosspaste.utils.HEADER_TARGET_APP_INSTANCE_ID
import com.crosspaste.utils.getJsonUtils
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import okio.Path.Companion.toOkioPath
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PushRoutingTest {

    @TempDir
    lateinit var tempDir: File

    private val json = getJsonUtils().JSON
    private val localAppInfo =
        AppInfo(
            appInstanceId = "local-instance",
            appVersion = "1.0.0",
            appRevision = "test",
            userName = "tester",
        )

    @Test
    fun `last chunk returns push complete failure when durable finalization fails`() {
        val pasteDao = mockk<PasteDao>(relaxed = true)
        val pasteboardService = mockk<PasteboardService>()
        coEvery { pasteboardService.tryWriteRemotePasteboardWithFile(41L) } returns
            Result.failure(IllegalStateException("database unavailable"))
        val manager = newManager(pasteDao, pasteboardService)
        val targetFile = File(tempDir, "chunk.bin")
        val filesIndex =
            FilesIndexBuilder(chunkSize = 1)
                .apply {
                    addFile(targetFile.toOkioPath(), size = 1)
                }.build()
        val session = assertNotNull(manager.create(41L, "remote-peer", filesIndex))

        withPushRouting(manager, connectedRoutingApi()) {
            val response =
                client.post("/sync/file/push") {
                    pushHeaders(41L, session.token)
                    header(PushHeaders.CHUNK_INDEX, "0")
                    contentType(ContentType.Application.OctetStream)
                    setBody(byteArrayOf(7))
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals(
                StandardErrorCode.PUSH_COMPLETE_FAIL.getCode(),
                json.decodeFromString<FailResponse>(response.bodyAsText()).errorCode,
            )
            assertEquals(1, manager.activeCount(), "failed finalization must stay retryable")
            coVerify(exactly = 1) { pasteboardService.tryWriteRemotePasteboardWithFile(41L) }
        }
    }

    @Test
    fun `complete uses durable loaded paste after in-memory session is gone`() {
        val pasteDao = mockk<PasteDao>()
        coEvery { pasteDao.getNoDeletePasteData(42L) } returns storedPaste(42L, "remote-peer")
        val manager = newManager(pasteDao, mockk(relaxed = true))

        withPushRouting(manager, mockk(relaxed = true)) {
            val response =
                client.post("/sync/paste/push/complete") {
                    pushHeaders(42L, "expired-token")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(
                PushCompleteResponse(emptyList()),
                json.decodeFromString<PushCompleteResponse>(response.bodyAsText()),
            )
        }
    }

    private fun withPushRouting(
        manager: PushSessionManager,
        syncRoutingApi: SyncRoutingApi,
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        application {
            install(ContentNegotiation) {
                json(json)
            }
            routing {
                pushRouting(
                    appInfo = localAppInfo,
                    pushSessionManager = manager,
                    syncRoutingApi = syncRoutingApi,
                    userDataPathProvider = mockk<UserDataPathProvider>(relaxed = true),
                )
            }
        }
        try {
            block()
        } finally {
            manager.close()
        }
    }

    private fun connectedRoutingApi(): SyncRoutingApi {
        val syncHandler = mockk<SyncHandler>(relaxed = true)
        return mockk {
            every { getSyncHandler("remote-peer") } returns syncHandler
        }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.pushHeaders(
        pasteId: Long,
        token: String,
    ) {
        header(HEADER_APP_INSTANCE_ID, "remote-peer")
        header(HEADER_TARGET_APP_INSTANCE_ID, localAppInfo.appInstanceId)
        header(PushHeaders.PASTE_ID, pasteId.toString())
        header(PushHeaders.SESSION_TOKEN, token)
    }

    private fun newManager(
        pasteDao: PasteDao,
        pasteboardService: PasteboardService,
    ): PushSessionManager =
        PushSessionManager(
            pasteDao = pasteDao,
            pasteboardService = pasteboardService,
            scope = CoroutineScope(Job()),
        )

    private fun storedPaste(
        id: Long,
        appInstanceId: String,
    ): PasteData =
        PasteData(
            id = id,
            appInstanceId = appInstanceId,
            pasteCollection = PasteCollection(emptyList()),
            pasteType = PasteType.TEXT_TYPE.type,
            size = 4,
            hash = "hash-$id",
            pasteState = PasteState.LOADED,
        )
}
