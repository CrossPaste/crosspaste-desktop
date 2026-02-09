package com.crosspaste.net

import com.crosspaste.db.sync.HostInfo
import com.crosspaste.dto.sync.EndpointInfo
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.net.clientapi.FailureResult
import com.crosspaste.net.clientapi.SuccessResult
import com.crosspaste.sync.SyncHandler
import com.crosspaste.utils.HostAndPort
import com.crosspaste.utils.buildUrl
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SyncIntegrationTest {

    private val instances = mutableListOf<TestInstance>()

    private fun createInstance(id: String): TestInstance = TestInstance(id).also { instances.add(it) }

    @AfterTest
    fun tearDown() {
        runBlocking {
            instances.forEach { it.stop() }
        }
    }

    private fun urlFor(instance: TestInstance): (io.ktor.http.URLBuilder.() -> Unit) =
        {
            buildUrl(HostAndPort("localhost", instance.getPort()))
        }

    private suspend fun trustBToA(
        a: TestInstance,
        b: TestInstance,
    ) {
        val result =
            b.syncClientApi.trust(
                a.appInfo.appInstanceId,
                "localhost",
                a.getToken(),
                urlFor(a),
            )
        assertTrue(result is SuccessResult, "Trust B->A should succeed")
    }

    // ---- Test 1: Full trust via token ----

    @Test
    fun testTwoDeviceTrust() =
        runBlocking {
            val a = createInstance("device-a")
            val b = createInstance("device-b")
            a.start()
            b.start()

            trustBToA(a, b)

            // A's secureIO should have B's public key
            assertTrue(a.secureIO.existCryptPublicKey(b.appInfo.appInstanceId))
            // B's secureIO should have A's public key
            assertTrue(b.secureIO.existCryptPublicKey(a.appInfo.appInstanceId))

            // Verify the stored keys match the original key pairs
            assertContentEquals(
                a.secureIO.serializedPublicKey(b.appInfo.appInstanceId),
                TestInstance.secureKeyPairSerializer.encodeCryptPublicKey(
                    b.secureKeyPair.cryptKeyPair.publicKey,
                ),
            )
            assertContentEquals(
                b.secureIO.serializedPublicKey(a.appInfo.appInstanceId),
                TestInstance.secureKeyPairSerializer.encodeCryptPublicKey(
                    a.secureKeyPair.cryptKeyPair.publicKey,
                ),
            )
        }

    // ---- Test 2: Encrypted heartbeat after trust ----

    @Test
    fun testHeartbeatAfterTrust() =
        runBlocking {
            val a = createInstance("device-a")
            val b = createInstance("device-b")
            a.start()
            b.start()

            trustBToA(a, b)

            val result =
                b.syncClientApi.heartbeat(
                    targetAppInstanceId = a.appInfo.appInstanceId,
                    toUrl = urlFor(a),
                )

            assertTrue(result is SuccessResult)
            assertEquals(VersionRelation.EQUAL_TO, result.getResult<VersionRelation>())
        }

    // ---- Test 3: Heartbeat with SyncInfo (encrypted body) ----

    @Test
    fun testHeartbeatWithSyncInfo() =
        runBlocking {
            val a = createInstance("device-a")
            val b = createInstance("device-b")
            a.start()
            b.start()

            trustBToA(a, b)

            val syncInfo =
                SyncInfo(
                    appInfo = b.appInfo,
                    endpointInfo =
                        EndpointInfo(
                            deviceId = "test-device-id",
                            deviceName = "test-device",
                            platform = TestInstance.platform,
                            hostInfoList =
                                listOf(
                                    HostInfo(
                                        networkPrefixLength = 24,
                                        hostAddress = "127.0.0.1",
                                    ),
                                ),
                            port = b.getPort(),
                        ),
                )

            val result =
                b.syncClientApi.heartbeat(
                    syncInfo = syncInfo,
                    targetAppInstanceId = a.appInfo.appInstanceId,
                    toUrl = urlFor(a),
                )

            assertTrue(result is SuccessResult)
            assertEquals(VersionRelation.EQUAL_TO, result.getResult<VersionRelation>())

            // A's syncRoutingApi should have received the syncInfo
            assertNotNull(a.syncRoutingApi.syncInfo)
            assertEquals(
                b.appInfo.appInstanceId,
                a.syncRoutingApi.syncInfo!!
                    .appInfo.appInstanceId,
            )
        }

    // ---- Test 4: Bidirectional trust ----

    @Test
    fun testBidirectionalTrust() =
        runBlocking {
            val a = createInstance("device-a")
            val b = createInstance("device-b")
            a.start()
            b.start()

            // B trusts A
            trustBToA(a, b)
            // A trusts B
            val result =
                a.syncClientApi.trust(
                    b.appInfo.appInstanceId,
                    "localhost",
                    b.getToken(),
                    urlFor(b),
                )
            assertTrue(result is SuccessResult)

            // All 4 key entries should exist
            assertTrue(a.secureIO.existCryptPublicKey(b.appInfo.appInstanceId))
            assertTrue(b.secureIO.existCryptPublicKey(a.appInfo.appInstanceId))
            // From bidirectional trust, B's server also saved A's key
            assertTrue(b.secureIO.existCryptPublicKey(a.appInfo.appInstanceId))
            // And A's server also saved B's key
            assertTrue(a.secureIO.existCryptPublicKey(b.appInfo.appInstanceId))
        }

    // ---- Test 5: Three device mesh ----

    @Test
    fun testThreeDeviceMesh() =
        runBlocking {
            val a = createInstance("device-a")
            val b = createInstance("device-b")
            val c = createInstance("device-c")
            a.start()
            b.start()
            c.start()

            // B trusts A
            trustBToA(a, b)
            // C trusts A
            trustBToA(a, c)
            // C trusts B
            trustBToA(b, c)

            // A has B's and C's keys
            assertTrue(a.secureIO.existCryptPublicKey(b.appInfo.appInstanceId))
            assertTrue(a.secureIO.existCryptPublicKey(c.appInfo.appInstanceId))
            // B has A's and C's keys
            assertTrue(b.secureIO.existCryptPublicKey(a.appInfo.appInstanceId))
            assertTrue(b.secureIO.existCryptPublicKey(c.appInfo.appInstanceId))
            // C has A's and B's keys
            assertTrue(c.secureIO.existCryptPublicKey(a.appInfo.appInstanceId))
            assertTrue(c.secureIO.existCryptPublicKey(b.appInfo.appInstanceId))

            // Heartbeat works between all 3 pairs
            val resultBA =
                b.syncClientApi.heartbeat(
                    targetAppInstanceId = a.appInfo.appInstanceId,
                    toUrl = urlFor(a),
                )
            assertTrue(resultBA is SuccessResult)

            val resultCA =
                c.syncClientApi.heartbeat(
                    targetAppInstanceId = a.appInfo.appInstanceId,
                    toUrl = urlFor(a),
                )
            assertTrue(resultCA is SuccessResult)

            val resultCB =
                c.syncClientApi.heartbeat(
                    targetAppInstanceId = b.appInfo.appInstanceId,
                    toUrl = urlFor(b),
                )
            assertTrue(resultCB is SuccessResult)
        }

    // ---- Test 6: Trust with wrong token ----

    @Test
    fun testTrustWithWrongToken() =
        runBlocking {
            val a = createInstance("device-a")
            val b = createInstance("device-b")
            a.start()
            b.start()

            val result =
                b.syncClientApi.trust(
                    a.appInfo.appInstanceId,
                    "localhost",
                    999999,
                    urlFor(a),
                )

            assertTrue(result is FailureResult)
            assertFalse(a.secureIO.existCryptPublicKey(b.appInfo.appInstanceId))
        }

    // ---- Test 7: Heartbeat without trust ----

    @Test
    fun testHeartbeatWithoutTrust() =
        runBlocking {
            val a = createInstance("device-a")
            val b = createInstance("device-b")
            a.start()
            b.start()

            val result =
                b.syncClientApi.heartbeat(
                    targetAppInstanceId = a.appInfo.appInstanceId,
                    toUrl = urlFor(a),
                )

            // Should fail because A doesn't have B's public key
            assertTrue(result is FailureResult)
        }

    // ---- Test 8: Telnet discovery ----

    @Test
    fun testTelnetDiscovery() =
        runBlocking {
            val a = createInstance("device-a")
            val b = createInstance("device-b")
            a.start()

            val versionRelation = b.telnetHelper.telnet("localhost", a.getPort())

            assertNotNull(versionRelation)
            assertEquals(VersionRelation.EQUAL_TO, versionRelation)
        }

    // ---- Test 9: SwitchHost first responder wins ----

    @Test
    fun testSwitchHostFirstResponderWins() =
        runBlocking {
            val a = createInstance("device-a")
            a.start()

            // One unreachable host and one reachable (localhost)
            val hostInfoList =
                listOf(
                    HostInfo(networkPrefixLength = 24, hostAddress = "10.255.255.1"),
                    HostInfo(networkPrefixLength = 8, hostAddress = "127.0.0.1"),
                )

            val b = createInstance("device-b")
            val result = b.telnetHelper.switchHost(hostInfoList, a.getPort())

            assertNotNull(result)
            assertEquals("127.0.0.1", result.first.hostAddress)
            assertEquals(VersionRelation.EQUAL_TO, result.second)
        }

    // ---- Test 10: Notify remove ----

    @Test
    fun testNotifyRemove() =
        runBlocking {
            val a = createInstance("device-a")
            val b = createInstance("device-b")
            a.start()
            b.start()

            trustBToA(a, b)

            // Add B's handler to A's routing so removal can be verified
            a.syncRoutingApi.innerSyncHandlers[b.appInfo.appInstanceId] = mockk(relaxed = true)

            b.syncClientApi.notifyRemove(urlFor(a))

            // A's handlers should no longer contain B
            assertFalse(a.syncRoutingApi.innerSyncHandlers.containsKey(b.appInfo.appInstanceId))
        }

    // ---- Test 11: Notify exit ----

    @Test
    fun testNotifyExit() =
        runBlocking {
            val a = createInstance("device-a")
            val b = createInstance("device-b")
            a.start()
            b.start()

            trustBToA(a, b)

            // Create a mock SyncHandler for B in A's routing
            val mockHandler =
                mockk<SyncHandler>(relaxed = true) {
                    coEvery { markExit() } returns Unit
                }
            a.syncRoutingApi.innerSyncHandlers[b.appInfo.appInstanceId] = mockHandler

            b.syncClientApi.notifyExit(urlFor(a))

            // Give the coroutine scope time to execute markExit
            kotlinx.coroutines.delay(200)

            // markExit should have been called on B's handler in A
            coVerify { mockHandler.markExit() }
        }

    // ---- Test 12: Show token ----

    @Test
    fun testShowToken() =
        runBlocking {
            val a = createInstance("device-a")
            val b = createInstance("device-b")
            a.start()

            val result = b.syncClientApi.showToken(urlFor(a))

            assertTrue(result is SuccessResult)
        }
}
