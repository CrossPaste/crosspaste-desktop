package com.crosspaste.net

import com.crosspaste.db.sync.HostInfo
import com.crosspaste.sync.SyncTestFixtures
import com.crosspaste.utils.HEADER_APP_INSTANCE_ID
import io.ktor.client.statement.*
import io.ktor.http.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

class TelnetHelperTest {

    @BeforeTest
    fun setup() {
        // bodyAsText() is a top-level extension function in Ktor, needs mockkStatic
        mockkStatic("io.ktor.client.statement.HttpResponseKt")
    }

    @AfterTest
    fun cleanup() {
        unmockkStatic("io.ktor.client.statement.HttpResponseKt")
    }

    private fun createMockPasteClient(): PasteClient = mockk(relaxed = true)

    private fun createMockResponse(
        statusCode: Int,
        body: String,
        appInstanceId: String? = null,
    ): HttpResponse {
        val response = mockk<HttpResponse>(relaxed = true)
        val statusObj = HttpStatusCode.fromValue(statusCode)
        every { response.status } returns statusObj
        coEvery { response.bodyAsText(any()) } returns body
        every { response.headers } returns
            if (appInstanceId != null) {
                headersOf(HEADER_APP_INSTANCE_ID, appInstanceId)
            } else {
                Headers.Empty
            }
        return response
    }

    private fun createTelnetHelper(
        pasteClient: PasteClient,
        syncApi: SyncApi = SyncApi,
        networkInterfaceService: NetworkInterfaceService = mockk(relaxed = true),
        syncInfoFactory: SyncInfoFactory = mockk(relaxed = true),
    ): TelnetHelper = TelnetHelper(networkInterfaceService, pasteClient, syncApi, syncInfoFactory)

    // ========== A. telnet (single host) ==========

    @Test
    fun telnet_200WithValidVersion_returnsVersionRelation() =
        runTest {
            val pasteClient = createMockPasteClient()
            val response = createMockResponse(200, SyncApi.VERSION.toString())
            coEvery { pasteClient.get(any(), any(), any()) } returns response

            val helper = createTelnetHelper(pasteClient)
            val result = helper.telnet("192.168.1.100", 13129)

            assertNotNull(result)
            assertEquals(VersionRelation.EQUAL_TO, result.versionRelation)
        }

    @Test
    fun telnet_200WithLowerVersion_returnsHigherThan() =
        runTest {
            val pasteClient = createMockPasteClient()
            val response = createMockResponse(200, "1") // Version 1, current is higher
            coEvery { pasteClient.get(any(), any(), any()) } returns response

            val helper = createTelnetHelper(pasteClient)
            val result = helper.telnet("192.168.1.100", 13129)

            assertNotNull(result)
            assertEquals(VersionRelation.HIGHER_THAN, result.versionRelation)
        }

    @Test
    fun telnet_200WithHigherVersion_returnsLowerThan() =
        runTest {
            val pasteClient = createMockPasteClient()
            val response = createMockResponse(200, "99") // Version 99, current is lower
            coEvery { pasteClient.get(any(), any(), any()) } returns response

            val helper = createTelnetHelper(pasteClient)
            val result = helper.telnet("192.168.1.100", 13129)

            assertNotNull(result)
            assertEquals(VersionRelation.LOWER_THAN, result.versionRelation)
        }

    @Test
    fun telnet_non200Status_returnsNull() =
        runTest {
            val pasteClient = createMockPasteClient()
            val response = createMockResponse(500, "error")
            coEvery { pasteClient.get(any(), any(), any()) } returns response

            val helper = createTelnetHelper(pasteClient)
            val result = helper.telnet("192.168.1.100", 13129)

            assertNull(result)
        }

    @Test
    fun telnet_nonIntegerBody_returnsNull() =
        runTest {
            val pasteClient = createMockPasteClient()
            val response = createMockResponse(200, "not-a-number")
            coEvery { pasteClient.get(any(), any(), any()) } returns response

            val helper = createTelnetHelper(pasteClient)
            val result = helper.telnet("192.168.1.100", 13129)

            assertNull(result)
        }

    @Test
    fun telnet_exceptionThrown_returnsNull() =
        runTest {
            val pasteClient = createMockPasteClient()
            coEvery { pasteClient.get(any(), any(), any()) } throws RuntimeException("connection failed")

            val helper = createTelnetHelper(pasteClient)
            val result = helper.telnet("192.168.1.100", 13129)

            assertNull(result)
        }

    @Test
    fun telnet_cancellationException_propagatesNotSwallowed() =
        runTest {
            // A coroutine cancellation must NOT be swallowed and turned into a null
            // "unreachable" result — it must propagate (matches the #4503 convention).
            val pasteClient = createMockPasteClient()
            coEvery { pasteClient.get(any(), any(), any()) } throws CancellationException("cancelled")

            val helper = createTelnetHelper(pasteClient)
            assertFailsWith<CancellationException> {
                helper.telnet("192.168.1.100", 13129)
            }
        }

    // ========== B. switchHost (parallel) ==========
    // switchHost uses CoroutineScope(ioDispatcher) internally so tests need real time

    @Test
    fun switchHost_singleHostSuccess_returnsPairWithHostInfo() =
        runBlocking {
            val pasteClient = createMockPasteClient()
            val response = createMockResponse(200, SyncApi.VERSION.toString())
            coEvery { pasteClient.get(any(), any(), any()) } returns response

            val helper = createTelnetHelper(pasteClient)
            val hostInfoList = listOf(HostInfo(24, "192.168.1.100"))

            val result = helper.switchHost(hostInfoList, 13129, timeout = 5000L)

            assertNotNull(result)
            assertEquals("192.168.1.100", result.first.hostAddress)
            assertEquals(VersionRelation.EQUAL_TO, result.second.versionRelation)
        }

    @Test
    fun switchHost_emptyList_returnsNull() =
        runTest {
            val pasteClient = createMockPasteClient()
            val helper = createTelnetHelper(pasteClient)

            val result = helper.switchHost(emptyList(), 13129)

            assertNull(result)
        }

    @Test
    fun switchHost_allFail_returnsNull() =
        runBlocking {
            val pasteClient = createMockPasteClient()
            coEvery { pasteClient.get(any(), any(), any()) } throws RuntimeException("connection failed")

            val helper = createTelnetHelper(pasteClient)
            val hostInfoList =
                listOf(
                    HostInfo(24, "192.168.1.100"),
                    HostInfo(24, "192.168.1.101"),
                )

            val result = helper.switchHost(hostInfoList, 13129, timeout = 2000L)

            assertNull(result)
        }

    @Test
    fun switchHost_oneSucceedsAmongFailures_returnsSuccessful() =
        runBlocking {
            val pasteClient = createMockPasteClient()
            val successResponse = createMockResponse(200, SyncApi.VERSION.toString())

            // All calls return the success response; switchHost races them
            coEvery { pasteClient.get(any(), any(), any()) } returns successResponse

            val helper = createTelnetHelper(pasteClient)
            val hostInfoList =
                listOf(
                    HostInfo(24, "192.168.1.100"),
                    HostInfo(24, "192.168.1.101"),
                )

            val result = helper.switchHost(hostInfoList, 13129, timeout = 5000L)

            assertNotNull(result)
        }

    @Test
    fun switchHost_allFail_returnsImmediatelyWithoutWaitingForTimeout() =
        runBlocking {
            // Regression guard: when every probe fails, switchHost must complete its
            // result with null as soon as all probes finish, NOT block until the internal
            // withTimeoutOrNull expires. The internal timeout is set very high (10s) and
            // the whole call is wrapped in a 2s deadline — if it returned only on timeout,
            // this would throw TimeoutCancellationException.
            val pasteClient = createMockPasteClient()
            coEvery { pasteClient.get(any(), any(), any()) } throws RuntimeException("connection failed")

            val helper = createTelnetHelper(pasteClient)
            val hostInfoList =
                listOf(
                    HostInfo(24, "192.168.1.100"),
                    HostInfo(24, "192.168.1.101"),
                )

            val result =
                withTimeout(2.seconds) {
                    helper.switchHost(hostInfoList, 13129, timeout = 10_000L)
                }

            assertNull(result)
        }

    @Test
    fun switchHost_404Response_returnsNull() =
        runBlocking {
            val pasteClient = createMockPasteClient()
            val response = createMockResponse(404, "not found")
            coEvery { pasteClient.get(any(), any(), any()) } returns response

            val helper = createTelnetHelper(pasteClient)
            val hostInfoList = listOf(HostInfo(24, "192.168.1.100"))

            val result = helper.switchHost(hostInfoList, 13129, timeout = 2000L)

            assertNull(result)
        }

    // ========== C. identity (Phase A: identity-aware discovery) ==========

    @Test
    fun telnet_readsPeerAppInstanceIdFromHeader() =
        runTest {
            val pasteClient = createMockPasteClient()
            val response = createMockResponse(200, SyncApi.VERSION.toString(), appInstanceId = "peer-123")
            coEvery { pasteClient.get(any(), any(), any()) } returns response

            val helper = createTelnetHelper(pasteClient)
            val result = helper.telnet("192.168.1.100", 13129)

            assertNotNull(result)
            assertEquals("peer-123", result.peerAppInstanceId)
        }

    @Test
    fun telnet_oldPeerWithoutHeader_peerAppInstanceIdNull() =
        runTest {
            val pasteClient = createMockPasteClient()
            val response = createMockResponse(200, SyncApi.VERSION.toString())
            coEvery { pasteClient.get(any(), any(), any()) } returns response

            val helper = createTelnetHelper(pasteClient)
            val result = helper.telnet("192.168.1.100", 13129)

            assertNotNull(result)
            assertNull(result.peerAppInstanceId)
        }

    @Test
    fun switchHost_identityMismatch_rejectsHost() =
        runBlocking {
            val pasteClient = createMockPasteClient()
            // Reachable, but advertises a different identity (a ghost on a historical IP).
            val response = createMockResponse(200, SyncApi.VERSION.toString(), appInstanceId = "ghost-999")
            coEvery { pasteClient.get(any(), any(), any()) } returns response

            val helper = createTelnetHelper(pasteClient)
            val hostInfoList = listOf(HostInfo(24, "192.168.1.100"))

            val result =
                helper.switchHost(hostInfoList, 13129, expectedAppInstanceId = "real-1", timeout = 2000L)

            assertNull(result)
        }

    @Test
    fun switchHost_identityMatch_acceptsHost() =
        runBlocking {
            val pasteClient = createMockPasteClient()
            val response = createMockResponse(200, SyncApi.VERSION.toString(), appInstanceId = "real-1")
            coEvery { pasteClient.get(any(), any(), any()) } returns response

            val helper = createTelnetHelper(pasteClient)
            val hostInfoList = listOf(HostInfo(24, "192.168.1.100"))

            val result =
                helper.switchHost(hostInfoList, 13129, expectedAppInstanceId = "real-1", timeout = 5000L)

            assertNotNull(result)
            assertEquals("192.168.1.100", result.first.hostAddress)
            assertEquals("real-1", result.second.peerAppInstanceId)
        }

    // ========== D. address push (#4509 phase 3: subnet-matched advertise header) ==========

    @Test
    fun telnet_advertisesOnlySubnetMatchedInterfaceInHeader() =
        runTest {
            // We probe a peer at 192.168.1.20. Of our local interfaces, only en0
            // (192.168.1.11/24) shares the peer's subnet; the 10.x interface must not be
            // advertised — that address is unreachable from the peer.
            val pasteClient = createMockPasteClient()
            val response = createMockResponse(200, SyncApi.VERSION.toString())
            val headersSlot = slot<HeadersBuilder.() -> Unit>()
            coEvery { pasteClient.get(any(), capture(headersSlot), any()) } returns response

            val networkInterfaceService = mockk<NetworkInterfaceService>(relaxed = true)
            every { networkInterfaceService.getCurrentUseNetworkInterfaces() } returns
                listOf(
                    NetworkInterfaceInfo("en0", 24, "192.168.1.11"),
                    NetworkInterfaceInfo("en1", 24, "10.0.0.5"),
                )

            val advertised =
                SyncTestFixtures.createSyncInfo(
                    hostInfoList = listOf(HostInfo(24, "192.168.1.11")),
                )
            val capturedList = slot<List<HostInfo>>()
            val syncInfoFactory = mockk<SyncInfoFactory>()
            coEvery { syncInfoFactory.createSyncInfo(capture(capturedList)) } returns advertised

            val helper =
                createTelnetHelper(
                    pasteClient,
                    networkInterfaceService = networkInterfaceService,
                    syncInfoFactory = syncInfoFactory,
                )
            helper.telnet("192.168.1.20", 13129)

            // Only the same-subnet interface fed the advertised SyncInfo.
            assertEquals(listOf(HostInfo(24, "192.168.1.11")), capturedList.captured)

            // The probe carries the advertise header, and it round-trips to the SyncInfo.
            val builtHeaders = HeadersBuilder().apply(headersSlot.captured).build()
            val headerValue = builtHeaders[SyncInfoHeaderCodec.HEADER]
            assertNotNull(headerValue)
            assertEquals(advertised, SyncInfoHeaderCodec.decode(headerValue))
        }

    @Test
    fun telnet_noSubnetMatch_omitsAdvertiseHeader() =
        runTest {
            // None of our interfaces share the peer's subnet (cross-subnet / routed): we
            // advertise nothing rather than leak an unreachable address.
            val pasteClient = createMockPasteClient()
            val response = createMockResponse(200, SyncApi.VERSION.toString())
            val headersSlot = slot<HeadersBuilder.() -> Unit>()
            coEvery { pasteClient.get(any(), capture(headersSlot), any()) } returns response

            val networkInterfaceService = mockk<NetworkInterfaceService>(relaxed = true)
            every { networkInterfaceService.getCurrentUseNetworkInterfaces() } returns
                listOf(NetworkInterfaceInfo("en0", 24, "10.0.0.5"))
            val syncInfoFactory = mockk<SyncInfoFactory>(relaxed = true)

            val helper =
                createTelnetHelper(
                    pasteClient,
                    networkInterfaceService = networkInterfaceService,
                    syncInfoFactory = syncInfoFactory,
                )
            helper.telnet("192.168.1.20", 13129)

            val builtHeaders = HeadersBuilder().apply(headersSlot.captured).build()
            assertNull(builtHeaders[SyncInfoHeaderCodec.HEADER])
            coVerify(exactly = 0) { syncInfoFactory.createSyncInfo(any()) }
        }

    // ========== E. identity (Phase A continued) ==========

    @Test
    fun switchHost_oldPeerUnknownIdentity_admittedAsFallback() =
        runBlocking {
            val pasteClient = createMockPasteClient()
            // Old peer: reachable, no identity header. Must still be admitted (heartbeat vets it).
            val response = createMockResponse(200, SyncApi.VERSION.toString())
            coEvery { pasteClient.get(any(), any(), any()) } returns response

            val helper = createTelnetHelper(pasteClient)
            val hostInfoList = listOf(HostInfo(24, "192.168.1.100"))

            val result =
                helper.switchHost(hostInfoList, 13129, expectedAppInstanceId = "real-1", timeout = 5000L)

            assertNotNull(result)
            assertNull(result.second.peerAppInstanceId)
        }
}
