package com.crosspaste.net.routing

import com.crosspaste.app.AppControl
import com.crosspaste.app.AppInfo
import com.crosspaste.paste.PasteCollection
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteReleaseService
import com.crosspaste.paste.PasteType
import com.crosspaste.paste.PasteboardService
import com.crosspaste.sync.PastePullService
import com.crosspaste.sync.PushSessionManager
import com.crosspaste.sync.SyncHandler
import com.crosspaste.sync.SyncTestFixtures.createConnectedSyncRuntimeInfo
import com.crosspaste.utils.HEADER_APP_INSTANCE_ID
import com.crosspaste.utils.HEADER_TARGET_APP_INSTANCE_ID
import com.crosspaste.utils.getJsonUtils
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PasteRoutingTest {

    @Test
    fun `authenticated identity replaces body identity`() {
        val pasteData = createPasteData(appInstanceId = "spoofed-peer")

        val bound = pasteData.bindAuthenticatedRemoteIdentity("authenticated-peer")

        assertEquals("authenticated-peer", bound.appInstanceId)
        assertTrue(bound.remote)
        assertEquals(pasteData.hash, bound.hash)
    }

    @Test
    fun `matching identity is still marked remote`() {
        val pasteData = createPasteData(appInstanceId = "authenticated-peer")

        val bound = pasteData.bindAuthenticatedRemoteIdentity("authenticated-peer")

        assertEquals("authenticated-peer", bound.appInstanceId)
        assertTrue(bound.remote)
    }

    // ========== /sync/paste target identity validation (#4894) ==========

    private val jsonUtils = getJsonUtils()

    private val localAppInfo =
        AppInfo(
            appInstanceId = "local-instance",
            appVersion = "1.0.0",
            appRevision = "abc",
            userName = "tester",
        )

    private fun withPasteRouting(
        pasteboardService: PasteboardService,
        appControl: AppControl =
            mockk(relaxed = true) {
                coEvery { isReceiveEnabled() } returns true
            },
        pastePullService: PastePullService = mockk(relaxed = true),
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        val syncHandler =
            mockk<SyncHandler> {
                every { currentSyncRuntimeInfo } returns
                    createConnectedSyncRuntimeInfo(appInstanceId = "remote-peer")
            }
        val syncRoutingApi =
            mockk<SyncRoutingApi> {
                every { getSyncHandler("remote-peer") } returns syncHandler
            }
        application {
            install(ContentNegotiation) {
                json(jsonUtils.JSON)
            }
            routing {
                pasteRouting(
                    appControl,
                    localAppInfo,
                    pasteboardService,
                    mockk<PasteReleaseService>(),
                    pastePullService,
                    mockk<PushSessionManager>(),
                    syncRoutingApi,
                )
            }
        }
        block()
    }

    private suspend fun ApplicationTestBuilder.postSyncPaste(targetAppInstanceId: String?) =
        client.post("/sync/paste") {
            header(HEADER_APP_INSTANCE_ID, "remote-peer")
            targetAppInstanceId?.let { header(HEADER_TARGET_APP_INSTANCE_ID, it) }
            contentType(ContentType.Application.Json)
            setBody(jsonUtils.JSON.encodeToString(createPasteData(appInstanceId = "remote-peer")))
        }

    @Test
    fun `paste targeting a stale identity is rejected without touching the pasteboard`() {
        val pasteboardService = mockk<PasteboardService>(relaxed = true)
        withPasteRouting(pasteboardService) {
            val response = postSyncPaste(targetAppInstanceId = "stale-old-identity")

            assertEquals(HttpStatusCode.BadRequest, response.status)
            coVerify(exactly = 0) { pasteboardService.tryWriteRemotePasteboard(any()) }
        }
    }

    @Test
    fun `paste without target header is rejected`() {
        val pasteboardService = mockk<PasteboardService>(relaxed = true)
        withPasteRouting(pasteboardService) {
            val response = postSyncPaste(targetAppInstanceId = null)

            assertEquals(HttpStatusCode.BadRequest, response.status)
            coVerify(exactly = 0) { pasteboardService.tryWriteRemotePasteboard(any()) }
        }
    }

    @Test
    fun `paste targeting our identity is accepted`() {
        val pasteboardService = mockk<PasteboardService>(relaxed = true)
        val appControl =
            mockk<AppControl>(relaxed = true) {
                coEvery { isReceiveEnabled() } returns true
            }
        val pastePullService = mockk<PastePullService>(relaxed = true)
        coEvery { pasteboardService.tryWriteRemotePasteboard(any()) } returns Result.success(Unit)
        withPasteRouting(pasteboardService, appControl, pastePullService) {
            val response = postSyncPaste(targetAppInstanceId = "local-instance")

            assertEquals(HttpStatusCode.OK, response.status)
            coVerify(exactly = 1) { pasteboardService.tryWriteRemotePasteboard(any()) }
            coVerify(exactly = 1) { pastePullService.updateMaxCreateTime("remote-peer", any()) }
            coVerify(exactly = 1) { appControl.completeReceiveOperation() }
        }
    }

    @Test
    fun `paste persistence failure is rejected without advancing cursor`() {
        val pasteboardService = mockk<PasteboardService>(relaxed = true)
        val appControl =
            mockk<AppControl>(relaxed = true) {
                coEvery { isReceiveEnabled() } returns true
            }
        val pastePullService = mockk<PastePullService>(relaxed = true)
        coEvery { pasteboardService.tryWriteRemotePasteboard(any()) } returns
            Result.failure(IllegalStateException("database unavailable"))

        withPasteRouting(pasteboardService, appControl, pastePullService) {
            val response = postSyncPaste(targetAppInstanceId = "local-instance")

            assertEquals(HttpStatusCode.BadRequest, response.status)
            coVerify(exactly = 1) { pasteboardService.tryWriteRemotePasteboard(any()) }
            coVerify(exactly = 0) { pastePullService.updateMaxCreateTime(any(), any()) }
            coVerify(exactly = 0) { appControl.completeReceiveOperation() }
        }
    }

    private fun createPasteData(appInstanceId: String): PasteData =
        PasteData(
            appInstanceId = appInstanceId,
            pasteCollection = PasteCollection(emptyList()),
            pasteType = PasteType.TEXT_TYPE.type,
            size = 4,
            hash = "hash",
        )
}
