package com.crosspaste.net.clientapi

import com.crosspaste.db.sync.HostInfo
import com.crosspaste.dto.pairing.v3.PairingCommitAckV3
import com.crosspaste.dto.pairing.v3.PairingCommitV3
import com.crosspaste.net.NetworkInterfaceInfo
import com.crosspaste.net.NetworkInterfaceService
import com.crosspaste.net.PasteClient
import com.crosspaste.net.SyncInfoAdvertiser
import com.crosspaste.net.SyncInfoFactory
import com.crosspaste.net.SyncInfoHeaderCodec
import com.crosspaste.net.exception.DesktopExceptionHandler
import com.crosspaste.net.exception.ExceptionHandler
import com.crosspaste.sync.SyncTestFixtures
import com.crosspaste.utils.HostAndPort
import com.crosspaste.utils.buildUrl
import com.crosspaste.utils.getJsonUtils
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.reflect.TypeInfo
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The pairing v3 commit must self-register the initiator's address via the
 * `crosspaste-sync-info` header: an acceptor that never receives our mDNS (one-way
 * multicast: VM NAT, AP isolation) has no other way to learn where to reach us, and
 * would otherwise finish the pairing trusted but unreachable.
 */
class PairingV3ClientApiTest {

    private val json = getJsonUtils().JSON
    private val exceptionHandler: ExceptionHandler = DesktopExceptionHandler()

    private val commit =
        PairingCommitV3(
            sessionId = ByteArray(16) { 1 },
            transcriptHash = ByteArray(32) { 2 },
            commitMac = ByteArray(32) { 3 },
        )

    private val ack =
        PairingCommitAckV3(
            sessionId = commit.sessionId,
            transcriptHash = commit.transcriptHash,
            receiptMac = ByteArray(32) { 4 },
        )

    /** A mocked PasteClient whose `post` routes through a real HttpClient(MockEngine). */
    private fun buildClient(): Pair<PasteClient, MockEngine> {
        val engine =
            MockEngine {
                respond(
                    content = json.encodeToString(PairingCommitAckV3.serializer(), ack),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            }
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
        return client to engine
    }

    private fun advertiser(
        interfaces: List<NetworkInterfaceInfo>,
        capturedList: CapturingSlot<List<HostInfo>>,
    ): SyncInfoAdvertiser {
        val networkInterfaceService = mockk<NetworkInterfaceService>(relaxed = true)
        every { networkInterfaceService.getCurrentUseNetworkInterfaces() } returns interfaces
        val syncInfoFactory = mockk<SyncInfoFactory>()
        coEvery { syncInfoFactory.createSyncInfo(capture(capturedList)) } coAnswers {
            SyncTestFixtures.createSyncInfo(hostInfoList = capturedList.captured)
        }
        return SyncInfoAdvertiser(networkInterfaceService, syncInfoFactory)
    }

    private fun toPeer(host: String): URLBuilder.() -> Unit = { buildUrl(HostAndPort(host, 13129)) }

    private fun onlyCommitRequest(engine: MockEngine): HttpRequestData {
        val request = engine.requestHistory.single()
        assertTrue(request.url.encodedPath.endsWith("/sync/pairing/v3/commit"), request.url.toString())
        return request
    }

    @Test
    fun sendCommit_advertisesSubnetMatchedInterface() {
        runBlocking {
            // Peer at 192.168.1.20: only en0 shares its subnet; the 10.x interface must not
            // be advertised because the peer cannot route to it.
            val (client, engine) = buildClient()
            val captured = slot<List<HostInfo>>()
            val interfaces =
                listOf(
                    NetworkInterfaceInfo("en0", 24, "192.168.1.11"),
                    NetworkInterfaceInfo("en1", 24, "10.0.0.5"),
                )
            val api = PairingV3ClientApi(client, exceptionHandler, advertiser(interfaces, captured))

            val result = api.sendCommit(commit, toPeer("192.168.1.20"))

            assertTrue(result is SuccessResult, result.toString())
            assertEquals(listOf(HostInfo(24, "192.168.1.11")), captured.captured)
            val header = onlyCommitRequest(engine).headers[SyncInfoHeaderCodec.HEADER]
            assertNotNull(header)
            val advertised = assertNotNull(SyncInfoHeaderCodec.decode(header))
            assertEquals(listOf(HostInfo(24, "192.168.1.11")), advertised.endpointInfo.hostInfoList)
        }
    }

    @Test
    fun sendCommit_noSubnetMatch_advertisesEveryInterface() {
        runBlocking {
            // Cross-subnet (routed / NAT): nothing matches, so give the acceptor every
            // address we listen on and let its reachability probe pick one.
            val (client, engine) = buildClient()
            val captured = slot<List<HostInfo>>()
            val interfaces =
                listOf(
                    NetworkInterfaceInfo("en0", 24, "192.168.1.11"),
                    NetworkInterfaceInfo("en1", 24, "10.0.0.5"),
                )
            val api = PairingV3ClientApi(client, exceptionHandler, advertiser(interfaces, captured))

            api.sendCommit(commit, toPeer("172.16.9.9"))

            assertEquals(listOf(HostInfo(24, "192.168.1.11"), HostInfo(24, "10.0.0.5")), captured.captured)
            assertNotNull(onlyCommitRequest(engine).headers[SyncInfoHeaderCodec.HEADER])
        }
    }

    @Test
    fun sendCommit_withoutAdvertiser_sendsPlainCommit() {
        runBlocking {
            val (client, engine) = buildClient()
            val api = PairingV3ClientApi(client, exceptionHandler)

            val result = api.sendCommit(commit, toPeer("192.168.1.20"))

            assertTrue(result is SuccessResult, result.toString())
            assertNull(onlyCommitRequest(engine).headers[SyncInfoHeaderCodec.HEADER])
        }
    }
}
