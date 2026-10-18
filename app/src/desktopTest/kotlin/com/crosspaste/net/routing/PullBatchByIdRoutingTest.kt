package com.crosspaste.net.routing

import com.crosspaste.app.AppControl
import com.crosspaste.app.AppInfo
import com.crosspaste.config.TestAppConfig
import com.crosspaste.net.PasteClient
import com.crosspaste.net.clientapi.PullClientApi
import com.crosspaste.net.clientapi.SuccessResult
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteState
import com.crosspaste.sync.PullTestStore
import com.crosspaste.sync.SyncHandler
import com.crosspaste.sync.SyncTestFixtures
import com.crosspaste.sync.pullTestPaste
import com.crosspaste.utils.HEADER_APP_INSTANCE_ID
import com.crosspaste.utils.HEADER_TARGET_APP_INSTANCE_ID
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PullBatchByIdRoutingTest {
    @Test fun `ID and legacy endpoints preserve their distinct cursor semantics and response shape`() =
        testApplication {
            val s = PullTestStore()
            val ids = (1L..60L).map { s.dao.createPasteData(pullTestPaste(it, it), PasteState.LOADED) }
            s.dao.createPasteData(pullTestPaste(100).copy(appInstanceId = "other"), PasteState.LOADED)
            s.dao.createPasteData(pullTestPaste(101), PasteState.LOADING)
            var allowSend = true
            val handler = mockk<SyncHandler>()
            every { handler.currentSyncRuntimeInfo } answers {
                SyncTestFixtures.createSyncRuntimeInfo(appInstanceId = "caller").copy(allowSend = allowSend)
            }
            val sync = mockk<SyncRoutingApi>()
            every { sync.getSyncHandler("caller") } returns handler
            val control = mockk<AppControl>()
            every { control.isFileSizeSyncEnabled(any()) } returns true
            application {
                install(ContentNegotiation) { json(s.json) }
                routing {
                    pullRouting(
                        control,
                        AppInfo("peer", "1", "1", "test"),
                        mockk(),
                        s.config,
                        s.dao,
                        sync,
                        mockk(),
                    )
                }
            }

            suspend fun get(path: String) =
                client.get(path) {
                    header(HEADER_APP_INSTANCE_ID, "caller")
                    header(HEADER_TARGET_APP_INSTANCE_ID, "peer")
                }

            fun decode(body: String): List<PasteData> = s.json.decodeFromString(body)
            val capped = get("/pull/pasteBatchById?afterId=0&limit=100")
            assertEquals(HttpStatusCode.OK, capped.status)
            assertEquals(ids.takeLast(50).reversed(), decode(capped.bodyAsText()).map { it.id })
            assertEquals(
                ids
                    .takeLast(
                        5,
                    ).reversed(),
                decode(get("/pull/pasteBatchById?afterId=${ids[54]}&limit=50").bodyAsText()).map {
                    it.id
                },
            )
            assertEquals(
                ids
                    .takeLast(
                        5,
                    ).reversed(),
                decode(get("/pull/pasteBatch?createTime=55&limit=50").bodyAsText()).map {
                    it.id
                },
            )
            assertEquals(ids.takeLast(10).reversed(), decode(get("/pull/pasteBatch").bodyAsText()).map { it.id })
            assertEquals(HttpStatusCode.BadRequest, get("/pull/pasteBatchById?afterId=-1").status)
            assertEquals(HttpStatusCode.BadRequest, get("/pull/pasteBatchById").status)

            // Exercise the actual request builder and decoder without opening sockets.
            val jsonClient =
                createClient {
                    install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json(s.json) }
                }
            val transport = mockk<PasteClient>()
            coEvery { transport.get(any(), any(), any()) } coAnswers {
                val headers = secondArg<HeadersBuilder.() -> Unit>()
                val url = URLBuilder().apply(thirdArg<URLBuilder.() -> Unit>()).build()
                jsonClient.get(url.toString()) {
                    header(HEADER_APP_INSTANCE_ID, "caller")
                    this.headers.apply(headers)
                }
            }
            val api = PullClientApi(transport, s.config, mockk(relaxed = true))
            val result =
                api.pullPasteBatchById("peer", ids[54], 50) {
                    protocol = io.ktor.http.URLProtocol.HTTP
                    host =
                        "localhost"
                }
            assertTrue(result is SuccessResult)
            assertEquals(ids.takeLast(5).reversed(), result.getResult<List<PasteData>>().map { it.id })

            every { s.config.getCurrentConfig() } returns TestAppConfig(enableSyncText = false)
            assertEquals(emptyList(), decode(get("/pull/pasteBatchById?afterId=0").bodyAsText()))
            allowSend = false
            assertTrue(get("/pull/pasteBatchById?afterId=0").status.value in 400..499)
            assertTrue(get("/pull/pasteBatch").status.value in 400..499)
        }
}
