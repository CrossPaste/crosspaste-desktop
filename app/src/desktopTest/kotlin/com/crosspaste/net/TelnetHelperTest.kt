package com.crosspaste.net

import com.crosspaste.db.sync.HostInfo
import io.ktor.client.statement.*
import io.ktor.http.*
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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
    ): HttpResponse {
        val response = mockk<HttpResponse>(relaxed = true)
        val statusObj = HttpStatusCode.fromValue(statusCode)
        every { response.status } returns statusObj
        coEvery { response.bodyAsText(any()) } returns body
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
            assertEquals(VersionRelation.EQUAL_TO, result)
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
            assertEquals(VersionRelation.HIGHER_THAN, result)
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
            assertEquals(VersionRelation.LOWER_THAN, result)
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

            val result = helper.switchHost(hostInfoList, 13129, 5000L)

            assertNotNull(result)
            assertEquals("192.168.1.100", result.first.hostAddress)
            assertEquals(VersionRelation.EQUAL_TO, result.second)
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

            val result = helper.switchHost(hostInfoList, 13129, 2000L)

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

            val result = helper.switchHost(hostInfoList, 13129, 5000L)

            assertNotNull(result)
        }

    @Test
    fun switchHost_404Response_returnsNull() =
        runBlocking {
            val pasteClient = createMockPasteClient()
            val response = createMockResponse(404, "not found")
            coEvery { pasteClient.get(any(), any(), any()) } returns response

            val helper = createTelnetHelper(pasteClient)
            val hostInfoList = listOf(HostInfo(24, "192.168.1.100"))

            val result = helper.switchHost(hostInfoList, 13129, 2000L)

            assertNull(result)
        }
}
