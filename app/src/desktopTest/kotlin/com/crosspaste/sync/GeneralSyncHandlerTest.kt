package com.crosspaste.sync

import com.crosspaste.db.sync.HostInfo
import com.crosspaste.db.sync.SyncState
import com.crosspaste.sync.SyncTestFixtures.createConnectedSyncRuntimeInfo
import com.crosspaste.sync.SyncTestFixtures.createConnectingSyncRuntimeInfo
import com.crosspaste.sync.SyncTestFixtures.createSyncRuntimeInfo
import com.crosspaste.sync.SyncTestFixtures.createUnverifiedSyncRuntimeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GeneralSyncHandlerTest {

    private class TestEmitter {
        val events = mutableListOf<SyncEvent>()

        val emitEvent: suspend (SyncEvent) -> Unit = { event -> events.add(event) }

        fun findEvent(predicate: (SyncEvent) -> Boolean): SyncEvent? = events.find(predicate)
    }

    /**
     * Helper to advance enough time for the scan/collect flow to process,
     * without triggering the infinite polling loop.
     * The scan collector fires after a small delay once the StateFlow emits.
     */
    private companion object {
        // Small advance to let scan/collect run without hitting the polling loop wall-clock check
        const val SMALL_ADVANCE_MS = 100L
    }

    // ========== A. handleFirstValue (initial state) ==========

    @Test
    fun handleFirstValue_disconnected_emitsResolveDisconnected() =
        runTest {
            val emitter = TestEmitter()
            val childScope = CoroutineScope(coroutineContext + Job())
            val syncRuntimeInfo = createSyncRuntimeInfo(connectState = SyncState.DISCONNECTED)

            GeneralSyncHandler(syncRuntimeInfo, emitter.emitEvent, childScope)
            advanceTimeBy(SMALL_ADVANCE_MS)

            assertTrue(emitter.events.any { it is SyncEvent.ResolveDisconnected })
            childScope.cancel()
        }

    @Test
    fun handleFirstValue_incompatible_emitsResolveDisconnected() =
        runTest {
            val emitter = TestEmitter()
            val childScope = CoroutineScope(coroutineContext + Job())
            val syncRuntimeInfo = createSyncRuntimeInfo(connectState = SyncState.INCOMPATIBLE)

            GeneralSyncHandler(syncRuntimeInfo, emitter.emitEvent, childScope)
            advanceTimeBy(SMALL_ADVANCE_MS)

            assertTrue(emitter.events.any { it is SyncEvent.ResolveDisconnected })
            childScope.cancel()
        }

    @Test
    fun handleFirstValue_unmatched_emitsResolveDisconnected() =
        runTest {
            val emitter = TestEmitter()
            val childScope = CoroutineScope(coroutineContext + Job())
            val syncRuntimeInfo = createSyncRuntimeInfo(connectState = SyncState.UNMATCHED)

            GeneralSyncHandler(syncRuntimeInfo, emitter.emitEvent, childScope)
            advanceTimeBy(SMALL_ADVANCE_MS)

            assertTrue(emitter.events.any { it is SyncEvent.ResolveDisconnected })
            childScope.cancel()
        }

    @Test
    fun handleFirstValue_unverified_emitsResolveDisconnected() =
        runTest {
            val emitter = TestEmitter()
            val childScope = CoroutineScope(coroutineContext + Job())
            val syncRuntimeInfo = createUnverifiedSyncRuntimeInfo()

            GeneralSyncHandler(syncRuntimeInfo, emitter.emitEvent, childScope)
            advanceTimeBy(SMALL_ADVANCE_MS)

            assertTrue(emitter.events.any { it is SyncEvent.ResolveDisconnected })
            childScope.cancel()
        }

    @Test
    fun handleFirstValue_connecting_emitsResolveConnecting() =
        runTest {
            val emitter = TestEmitter()
            val childScope = CoroutineScope(coroutineContext + Job())
            val syncRuntimeInfo = createConnectingSyncRuntimeInfo()

            GeneralSyncHandler(syncRuntimeInfo, emitter.emitEvent, childScope)
            advanceTimeBy(SMALL_ADVANCE_MS)

            assertTrue(emitter.events.any { it is SyncEvent.ResolveConnecting })
            childScope.cancel()
        }

    @Test
    fun handleFirstValue_connected_emitsResolveConnection() =
        runTest {
            val emitter = TestEmitter()
            val childScope = CoroutineScope(coroutineContext + Job())
            val syncRuntimeInfo = createConnectedSyncRuntimeInfo()

            GeneralSyncHandler(syncRuntimeInfo, emitter.emitEvent, childScope)
            advanceTimeBy(SMALL_ADVANCE_MS)

            assertTrue(emitter.events.any { it is SyncEvent.ResolveConnection })
            childScope.cancel()
        }

    // ========== B. handleValueChange (state transitions) ==========

    @Test
    fun handleValueChange_portChange_emitsResolveConnection() =
        runTest {
            val emitter = TestEmitter()
            val childScope = CoroutineScope(coroutineContext + Job())
            val syncRuntimeInfo = createConnectedSyncRuntimeInfo(port = 13129)

            val handler = GeneralSyncHandler(syncRuntimeInfo, emitter.emitEvent, childScope)
            advanceTimeBy(SMALL_ADVANCE_MS)
            emitter.events.clear()

            handler.updateSyncRuntimeInfo(syncRuntimeInfo.copy(port = 13130))
            advanceTimeBy(SMALL_ADVANCE_MS)

            assertTrue(emitter.events.any { it is SyncEvent.ResolveConnection })
            childScope.cancel()
        }

    @Test
    fun handleValueChange_hostAddressChange_emitsResolveConnection() =
        runTest {
            val emitter = TestEmitter()
            val childScope = CoroutineScope(coroutineContext + Job())
            val syncRuntimeInfo = createConnectedSyncRuntimeInfo(hostAddress = "192.168.1.100")

            val handler = GeneralSyncHandler(syncRuntimeInfo, emitter.emitEvent, childScope)
            advanceTimeBy(SMALL_ADVANCE_MS)
            emitter.events.clear()

            handler.updateSyncRuntimeInfo(syncRuntimeInfo.copy(connectHostAddress = "192.168.1.101"))
            advanceTimeBy(SMALL_ADVANCE_MS)

            assertTrue(emitter.events.any { it is SyncEvent.ResolveConnection })
            childScope.cancel()
        }

    @Test
    fun handleValueChange_stateToDisconnected_emitsFailAndRefreshSyncInfo() =
        runTest {
            val emitter = TestEmitter()
            val childScope = CoroutineScope(coroutineContext + Job())
            val syncRuntimeInfo = createConnectedSyncRuntimeInfo()

            val handler = GeneralSyncHandler(syncRuntimeInfo, emitter.emitEvent, childScope)
            advanceTimeBy(SMALL_ADVANCE_MS)
            emitter.events.clear()

            handler.updateSyncRuntimeInfo(syncRuntimeInfo.copy(connectState = SyncState.DISCONNECTED))
            advanceTimeBy(SMALL_ADVANCE_MS)

            assertTrue(emitter.events.any { it is SyncEvent.RefreshSyncInfo })
            childScope.cancel()
        }

    @Test
    fun handleValueChange_stateToConnecting_emitsResolveConnecting() =
        runTest {
            val emitter = TestEmitter()
            val childScope = CoroutineScope(coroutineContext + Job())
            val syncRuntimeInfo = createSyncRuntimeInfo(connectState = SyncState.DISCONNECTED)

            val handler = GeneralSyncHandler(syncRuntimeInfo, emitter.emitEvent, childScope)
            advanceTimeBy(SMALL_ADVANCE_MS)
            emitter.events.clear()

            handler.updateSyncRuntimeInfo(
                syncRuntimeInfo.copy(
                    connectState = SyncState.CONNECTING,
                    connectHostAddress = "192.168.1.100",
                ),
            )
            advanceTimeBy(SMALL_ADVANCE_MS)

            assertTrue(emitter.events.any { it is SyncEvent.ResolveConnecting })
            childScope.cancel()
        }

    @Test
    fun handleValueChange_stateToConnected_emitsResolveConnection() =
        runTest {
            val emitter = TestEmitter()
            val childScope = CoroutineScope(coroutineContext + Job())
            val syncRuntimeInfo = createConnectingSyncRuntimeInfo()

            val handler = GeneralSyncHandler(syncRuntimeInfo, emitter.emitEvent, childScope)
            advanceTimeBy(SMALL_ADVANCE_MS)
            emitter.events.clear()

            handler.updateSyncRuntimeInfo(syncRuntimeInfo.copy(connectState = SyncState.CONNECTED))
            advanceTimeBy(SMALL_ADVANCE_MS)

            assertTrue(emitter.events.any { it is SyncEvent.ResolveConnection })
            childScope.cancel()
        }

    @Test
    fun handleValueChange_hostInfoListChangeWhileConnected_emitsResolveConnection() =
        runTest {
            val emitter = TestEmitter()
            val childScope = CoroutineScope(coroutineContext + Job())
            val syncRuntimeInfo = createConnectedSyncRuntimeInfo()

            val handler = GeneralSyncHandler(syncRuntimeInfo, emitter.emitEvent, childScope)
            advanceTimeBy(SMALL_ADVANCE_MS)
            emitter.events.clear()

            handler.updateSyncRuntimeInfo(
                syncRuntimeInfo.copy(
                    hostInfoList = listOf(HostInfo(24, "10.0.0.1")),
                ),
            )
            advanceTimeBy(SMALL_ADVANCE_MS)

            assertTrue(emitter.events.any { it is SyncEvent.ResolveConnection })
            childScope.cancel()
        }

    // ========== C. API delegation ==========

    @Test
    fun forceResolve_emitsForceResolveConnection() =
        runTest {
            val emitter = TestEmitter()
            val childScope = CoroutineScope(coroutineContext + Job())
            val syncRuntimeInfo = createConnectedSyncRuntimeInfo()

            val handler = GeneralSyncHandler(syncRuntimeInfo, emitter.emitEvent, childScope)
            advanceTimeBy(SMALL_ADVANCE_MS)
            emitter.events.clear()

            handler.forceResolve()
            advanceTimeBy(SMALL_ADVANCE_MS)

            assertTrue(emitter.events.any { it is SyncEvent.ForceResolveConnection })
            childScope.cancel()
        }

    @Test
    fun updateAllowSend_emitsUpdateAllowSend() =
        runTest {
            val emitter = TestEmitter()
            val childScope = CoroutineScope(coroutineContext + Job())
            val syncRuntimeInfo = createConnectedSyncRuntimeInfo()

            val handler = GeneralSyncHandler(syncRuntimeInfo, emitter.emitEvent, childScope)
            advanceTimeBy(SMALL_ADVANCE_MS)
            emitter.events.clear()

            handler.updateAllowSend(false)
            advanceTimeBy(SMALL_ADVANCE_MS)

            val event = emitter.findEvent { it is SyncEvent.UpdateAllowSend }
            assertNotNull(event)
            assertEquals(false, (event as SyncEvent.UpdateAllowSend).allowSend)
            childScope.cancel()
        }

    @Test
    fun updateAllowReceive_emitsUpdateAllowReceive() =
        runTest {
            val emitter = TestEmitter()
            val childScope = CoroutineScope(coroutineContext + Job())
            val syncRuntimeInfo = createConnectedSyncRuntimeInfo()

            val handler = GeneralSyncHandler(syncRuntimeInfo, emitter.emitEvent, childScope)
            advanceTimeBy(SMALL_ADVANCE_MS)
            emitter.events.clear()

            handler.updateAllowReceive(false)
            advanceTimeBy(SMALL_ADVANCE_MS)

            val event = emitter.findEvent { it is SyncEvent.UpdateAllowReceive }
            assertNotNull(event)
            assertEquals(false, (event as SyncEvent.UpdateAllowReceive).allowReceive)
            childScope.cancel()
        }

    @Test
    fun updateNoteName_emitsUpdateNoteName() =
        runTest {
            val emitter = TestEmitter()
            val childScope = CoroutineScope(coroutineContext + Job())
            val syncRuntimeInfo = createConnectedSyncRuntimeInfo()

            val handler = GeneralSyncHandler(syncRuntimeInfo, emitter.emitEvent, childScope)
            advanceTimeBy(SMALL_ADVANCE_MS)
            emitter.events.clear()

            handler.updateNoteName("My Phone")
            advanceTimeBy(SMALL_ADVANCE_MS)

            val event = emitter.findEvent { it is SyncEvent.UpdateNoteName }
            assertNotNull(event)
            assertEquals("My Phone", (event as SyncEvent.UpdateNoteName).noteName)
            childScope.cancel()
        }

    @Test
    fun trustByToken_emitsTrustByToken() =
        runTest {
            val emitter = TestEmitter()
            val childScope = CoroutineScope(coroutineContext + Job())
            val syncRuntimeInfo = createUnverifiedSyncRuntimeInfo()

            val handler = GeneralSyncHandler(syncRuntimeInfo, emitter.emitEvent, childScope)
            advanceTimeBy(SMALL_ADVANCE_MS)
            emitter.events.clear()

            handler.trustByToken(123456) { }
            advanceTimeBy(SMALL_ADVANCE_MS)

            val event = emitter.findEvent { it is SyncEvent.TrustByToken }
            assertNotNull(event)
            assertEquals(123456, (event as SyncEvent.TrustByToken).token)
            childScope.cancel()
        }

    @Test
    fun showToken_emitsShowToken() =
        runTest {
            val emitter = TestEmitter()
            val childScope = CoroutineScope(coroutineContext + Job())
            val syncRuntimeInfo = createUnverifiedSyncRuntimeInfo()

            val handler = GeneralSyncHandler(syncRuntimeInfo, emitter.emitEvent, childScope)
            advanceTimeBy(SMALL_ADVANCE_MS)
            emitter.events.clear()

            handler.showToken()
            advanceTimeBy(SMALL_ADVANCE_MS)

            assertTrue(emitter.events.any { it is SyncEvent.ShowToken })
            childScope.cancel()
        }

    // ========== D. getConnectHostAddress ==========

    @Test
    fun getConnectHostAddress_alreadyHasAddress_returnsImmediately() =
        runTest {
            val emitter = TestEmitter()
            val childScope = CoroutineScope(coroutineContext + Job())
            val syncRuntimeInfo = createConnectedSyncRuntimeInfo(hostAddress = "192.168.1.100")

            val handler = GeneralSyncHandler(syncRuntimeInfo, emitter.emitEvent, childScope)
            advanceTimeBy(SMALL_ADVANCE_MS)

            val address = handler.getConnectHostAddress()
            assertEquals("192.168.1.100", address)
            childScope.cancel()
        }

    @Test
    fun getConnectHostAddress_noAddress_emitsResolveConnectionAndWaits() =
        runTest {
            val emitter = TestEmitter()
            val childScope = CoroutineScope(coroutineContext + Job())
            val syncRuntimeInfo = createSyncRuntimeInfo(connectState = SyncState.DISCONNECTED)

            val handler = GeneralSyncHandler(syncRuntimeInfo, emitter.emitEvent, childScope)
            advanceTimeBy(SMALL_ADVANCE_MS)
            emitter.events.clear()

            // Since there's no connectHostAddress and the callback will complete after timeout,
            // the result will be null (no address resolved within timeout)
            val address = handler.getConnectHostAddress()

            // It should have emitted a ResolveConnection event
            assertTrue(emitter.events.any { it is SyncEvent.ResolveConnection })
            assertNull(address)
            childScope.cancel()
        }
}
