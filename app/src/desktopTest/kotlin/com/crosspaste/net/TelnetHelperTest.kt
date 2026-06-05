package com.crosspaste.net

import com.crosspaste.db.sync.HostInfo
import com.crosspaste.utils.HEADER_APP_INSTANCE_ID
import io.ktor.client.statement.*
import io.ktor.http.*
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
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
    ): TelnetHelper = TelnetHelper(pasteClient, syncApi)

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
