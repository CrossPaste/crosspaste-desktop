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
    fun handleFirstValue_disconnected_emitsResolve() =
        runTest {
            val emitter = TestEmitter()
            val childScope = CoroutineScope(coroutineContext + Job())
            val syncRuntimeInfo = createSyncRuntimeInfo(connectState = SyncState.DISCONNECTED)

            GeneralSyncHandler(syncRuntimeInfo, emitter.emitEvent, childScope)
            advanceTimeBy(SMALL_ADVANCE_MS)

            assertTrue(emitter.events.any { it is SyncEvent.Resolve })
            childScope.cancel()
        }

    @Test
    fun handleFirstValue_incompatible_emitsResolve() =
        runTest {
            val emitter = TestEmitter()
            val childScope = CoroutineScope(coroutineContext + Job())
            val syncRuntimeInfo = createSyncRuntimeInfo(connectState = SyncState.INCOMPATIBLE)

            GeneralSyncHandler(syncRuntimeInfo, emitter.emitEvent, childScope)
            advanceTimeBy(SMALL_ADVANCE_MS)

            assertTrue(emitter.events.any { it is SyncEvent.Resolve })
            childScope.cancel()
        }

    @Test
    fun handleFirstValue_unmatched_emitsResolve() =
        runTest {
            val emitter = TestEmitter()
            val childScope = CoroutineScope(coroutineContext + Job())
            val syncRuntimeInfo = createSyncRuntimeInfo(connectState = SyncState.UNMATCHED)

            GeneralSyncHandler(syncRuntimeInfo, emitter.emitEvent, childScope)
            advanceTimeBy(SMALL_ADVANCE_MS)

            assertTrue(emitter.events.any { it is SyncEvent.Resolve })
            childScope.cancel()
        }

    @Test
    fun handleFirstValue_unverified_emitsResolve() =
        runTest {
            val emitter = TestEmitter()
            val childScope = CoroutineScope(coroutineContext + Job())
            val syncRuntimeInfo = createUnverifiedSyncRuntimeInfo()

            GeneralSyncHandler(syncRuntimeInfo, emitter.emitEvent, childScope)
            advanceTimeBy(SMALL_ADVANCE_MS)

            assertTrue(emitter.events.any { it is SyncEvent.Resolve })
            childScope.cancel()
        }

    @Test
    fun handleFirstValue_connecting_emitsResolve() =
        runTest {
            val emitter = TestEmitter()
            val childScope = CoroutineScope(coroutineContext + Job())
            val syncRuntimeInfo = createConnectingSyncRuntimeInfo()

            GeneralSyncHandler(syncRuntimeInfo, emitter.emitEvent, childScope)
            advanceTimeBy(SMALL_ADVANCE_MS)

            assertTrue(emitter.events.any { it is SyncEvent.Resolve })
            childScope.cancel()
        }

    @Test
    fun handleFirstValue_connected_emitsResolve() =
        runTest {
            val emitter = TestEmitter()
            val childScope = CoroutineScope(coroutineContext + Job())
            val syncRuntimeInfo = createConnectedSyncRuntimeInfo()

            GeneralSyncHandler(syncRuntimeInfo, emitter.emitEvent, childScope)
            advanceTimeBy(SMALL_ADVANCE_MS)

            assertTrue(emitter.events.any { it is SyncEvent.Resolve })
            childScope.cancel()
        }

    // ========== B. handleValueChange (state transitions) ==========

    @Test
    fun handleValueChange_portChange_emitsResolve() =
        runTest {
            val emitter = TestEmitter()
            val childScope = CoroutineScope(coroutineContext + Job())
            val syncRuntimeInfo = createConnectedSyncRuntimeInfo(port = 13129)

            val handler = GeneralSyncHandler(syncRuntimeInfo, emitter.emitEvent, childScope)
            advanceTimeBy(SMALL_ADVANCE_MS)
            emitter.events.clear()

            handler.updateSyncRuntimeInfo(syncRuntimeInfo.copy(port = 13130))
            advanceTimeBy(SMALL_ADVANCE_MS)

            assertTrue(emitter.events.any { it is SyncEvent.Resolve })
            childScope.cancel()
        }

    @Test
    fun handleValueChange_hostAddressChange_emitsResolve() =
        runTest {
            val emitter = TestEmitter()
            val childScope = CoroutineScope(coroutineContext + Job())
            val syncRuntimeInfo = createConnectedSyncRuntimeInfo(hostAddress = "192.168.1.100")

            val handler = GeneralSyncHandler(syncRuntimeInfo, emitter.emitEvent, childScope)
            advanceTimeBy(SMALL_ADVANCE_MS)
            emitter.events.clear()

            handler.updateSyncRuntimeInfo(syncRuntimeInfo.copy(connectHostAddress = "192.168.1.101"))
            advanceTimeBy(SMALL_ADVANCE_MS)

            assertTrue(emitter.events.any { it is SyncEvent.Resolve })
            childScope.cancel()
        }

    @Test
    fun handleValueChange_stateToDisconnected_emitsRefreshSyncInfo() =
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
    fun handleValueChange_stateToConnecting_noEventEmitted() =
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

            assertTrue(emitter.events.none { it is SyncEvent.Resolve })
            childScope.cancel()
        }

    @Test
    fun handleValueChange_stateToConnected_emitsResolveToVerify() =
        runTest {
            val emitter = TestEmitter()
            val childScope = CoroutineScope(coroutineContext + Job())
            val syncRuntimeInfo = createConnectingSyncRuntimeInfo()

            val handler = GeneralSyncHandler(syncRuntimeInfo, emitter.emitEvent, childScope)
            advanceTimeBy(SMALL_ADVANCE_MS)
            emitter.events.clear()

            handler.updateSyncRuntimeInfo(syncRuntimeInfo.copy(connectState = SyncState.CONNECTED))
            advanceTimeBy(SMALL_ADVANCE_MS)

            // CONNECTED transition emits Resolve to immediately verify connectivity,
            // covering the server-side trust flow where trustSyncInfo() writes CONNECTED
            // without the local device ever having sent a heartbeat.
            assertTrue(emitter.events.any { it is SyncEvent.Resolve })
            childScope.cancel()
        }

    @Test
    fun handleValueChange_hostInfoListChangeWhileConnected_emitsResolve() =
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

            assertTrue(emitter.events.any { it is SyncEvent.Resolve })
            childScope.cancel()
        }

    /**
     * Regression for #4499 / #4500: within a single failing resolve pass the address
     * thrashes (CONNECTING@new -> DISCONNECTED@stale). The DISCONNECTED write changes
     * BOTH state and address. The address-change branch must NOT short-circuit with an
     * immediate Resolve here — that bypassed backoff and produced the ~530ms tight loop.
     * It still emits RefreshSyncInfo (previous != CONNECTED, so no Resolve).
     *
     * Backoff is NO LONGER applied from this DB write (discovery-driven-fast-reconnect,
     * fix one): handleValueChange must not call fail(), so external/discovery DB writes
     * can't starve polling. The real backoff for a failed attempt comes from the polling
     * callback's markPollFailure (covered by pollingCallback_markPollFailure_incrementsFailCount).
     */
    @Test
    fun handleValueChange_addressThrashOnFailure_noResolveNoBackoffFromDbWrite() =
        runTest {
            val emitter = TestEmitter()
            val childScope = CoroutineScope(coroutineContext + Job())
            val syncRuntimeInfo =
                createConnectingSyncRuntimeInfo(hostAddress = "192.168.1.109")

            val handler = GeneralSyncHandler(syncRuntimeInfo, emitter.emitEvent, childScope)
            advanceTimeBy(SMALL_ADVANCE_MS)
            emitter.events.clear()
            val failBefore = handler.syncPollingManager.currentFailCount

            // Failure path reverted the address to a stale snapshot value while also
            // dropping to DISCONNECTED — the exact write that used to bypass backoff.
            handler.updateSyncRuntimeInfo(
                syncRuntimeInfo.copy(
                    connectState = SyncState.DISCONNECTED,
                    connectHostAddress = "192.168.1.117",
                ),
            )
            advanceTimeBy(SMALL_ADVANCE_MS)

            assertTrue(emitter.events.none { it is SyncEvent.Resolve })
            assertTrue(emitter.events.any { it is SyncEvent.RefreshSyncInfo })
            assertEquals(failBefore, handler.syncPollingManager.currentFailCount)
            childScope.cancel()
        }

    /**
     * Case 5 (fix one regression): repeated external DB emits for a DISCONNECTED device
     * (e.g. mDNS address jitter writing the row) must NOT touch the backoff. Before fix one
     * the trailing `when` block called fail() on every such write, starving the polling
     * alarm. Now fail() is never called from handleValueChange, so fail count stays put and
     * no Resolve is emitted (the device is not CONNECTED).
     */
    @Test
    fun handleValueChange_repeatedDisconnectedDbWrites_doNotStarveBackoff() =
        runTest {
            val emitter = TestEmitter()
            val childScope = CoroutineScope(coroutineContext + Job())
            val base =
                createSyncRuntimeInfo(
                    connectState = SyncState.DISCONNECTED,
                    hostInfoList = listOf(HostInfo(24, "192.168.1.100")),
                )

            val handler = GeneralSyncHandler(base, emitter.emitEvent, childScope)
            advanceTimeBy(SMALL_ADVANCE_MS)
            emitter.events.clear()
            val failBefore = handler.syncPollingManager.currentFailCount

            // Several external DB emits (address jitter) while staying DISCONNECTED.
            handler.updateSyncRuntimeInfo(base.copy(hostInfoList = listOf(HostInfo(24, "192.168.1.101"))))
            advanceTimeBy(SMALL_ADVANCE_MS)
            handler.updateSyncRuntimeInfo(base.copy(hostInfoList = listOf(HostInfo(24, "192.168.1.102"))))
            advanceTimeBy(SMALL_ADVANCE_MS)
            handler.updateSyncRuntimeInfo(base.copy(hostInfoList = listOf(HostInfo(24, "192.168.1.103"))))
            advanceTimeBy(SMALL_ADVANCE_MS)

            assertEquals(failBefore, handler.syncPollingManager.currentFailCount)
            assertTrue(emitter.events.none { it is SyncEvent.Resolve })
            childScope.cancel()
        }

    /**
     * fastReconnect (discovery-driven, fix two): clears the connection-failure backoff and
     * emits a ForceResolve. serviceResolved is a reachability edge, so a fresh start is
     * warranted; a successful reconnect would also reset via the CONNECTED transition, but
     * resetting up front lets the polling fallback retry promptly if this attempt races.
     */
    @Test
    fun fastReconnect_resetsBackoffAndEmitsForceResolve() =
        runTest {
            val emitter = TestEmitter()
            val childScope = CoroutineScope(coroutineContext + Job())
            val syncRuntimeInfo = createSyncRuntimeInfo(connectState = SyncState.DISCONNECTED)

            val handler = GeneralSyncHandler(syncRuntimeInfo, emitter.emitEvent, childScope)
            advanceTimeBy(SMALL_ADVANCE_MS)

            // Build up backoff via real poll failures.
            handler.createPollingCallback().markPollFailure()
            handler.createPollingCallback().markPollFailure()
            assertTrue(handler.syncPollingManager.currentFailCount > 0)
            emitter.events.clear()

            handler.fastReconnect()
            advanceTimeBy(SMALL_ADVANCE_MS)

            assertEquals(0, handler.syncPollingManager.currentFailCount)
            assertTrue(emitter.events.any { it is SyncEvent.ForceResolve })
            childScope.cancel()
        }

    /**
     * A connect-address change while NOT connected (here: staying CONNECTING) must not
     * trigger an immediate Resolve — only CONNECTED address changes do.
     */
    @Test
    fun handleValueChange_addressChangeWhileConnecting_doesNotEmitResolve() =
        runTest {
            val emitter = TestEmitter()
            val childScope = CoroutineScope(coroutineContext + Job())
            val syncRuntimeInfo =
                createConnectingSyncRuntimeInfo(hostAddress = "192.168.1.108")

            val handler = GeneralSyncHandler(syncRuntimeInfo, emitter.emitEvent, childScope)
            advanceTimeBy(SMALL_ADVANCE_MS)
            emitter.events.clear()

            handler.updateSyncRuntimeInfo(
                syncRuntimeInfo.copy(connectHostAddress = "192.168.1.109"),
            )
            advanceTimeBy(SMALL_ADVANCE_MS)

            assertTrue(emitter.events.none { it is SyncEvent.Resolve })
            childScope.cancel()
        }

    /**
     * Phase E + #4500 guard: an active switch makes the resolver write the sequence
     * CONNECTED@old → CONNECTING@new → CONNECTED@new within one pass. Fed through the
     * handler, this must stay bounded — the CONNECTING write (address changed but state
     * != CONNECTED) must NOT re-emit Resolve (the old backoff-bypass), only the final
     * CONNECTED transition emits exactly one verify Resolve, and no fail()/backoff fires.
     *
     * (The resolver half — that it produces this sequence with no DISCONNECTED — is
     * covered by SyncResolverTest.verifyConnection_currentAddressDead_activeSwitches...;
     * a single fully-wired live loop is impractical here because GeneralSyncHandler's
     * polling loop reads wall-clock, so the two halves are asserted independently.)
     */
    @Test
    fun handleValueChange_activeSwitchSequence_boundedResolvesNoBackoffBypass() =
        runTest {
            val emitter = TestEmitter()
            val childScope = CoroutineScope(coroutineContext + Job())
            val connectedOld = createConnectedSyncRuntimeInfo(hostAddress = "192.168.1.108")

            val handler = GeneralSyncHandler(connectedOld, emitter.emitEvent, childScope)
            advanceTimeBy(SMALL_ADVANCE_MS)
            emitter.events.clear()
            val failBefore = handler.syncPollingManager.currentFailCount

            // The exact DB-write sequence an active switch produces in one resolve pass.
            handler.updateSyncRuntimeInfo(
                connectedOld.copy(
                    connectState = SyncState.CONNECTING,
                    connectHostAddress = "192.168.1.109",
                ),
            )
            advanceTimeBy(SMALL_ADVANCE_MS)
            handler.updateSyncRuntimeInfo(
                connectedOld.copy(
                    connectState = SyncState.CONNECTED,
                    connectHostAddress = "192.168.1.109",
                ),
            )
            advanceTimeBy(SMALL_ADVANCE_MS)

            // Exactly one Resolve (the final CONNECTED verify); the CONNECTING address
            // change did NOT bypass backoff with an extra Resolve.
            assertEquals(1, emitter.events.count { it is SyncEvent.Resolve })
            assertEquals(failBefore, handler.syncPollingManager.currentFailCount)
            childScope.cancel()
        }

    // ========== C. API delegation ==========

    @Test
    fun forceResolve_emitsForceResolve() =
        runTest {
            val emitter = TestEmitter()
            val childScope = CoroutineScope(coroutineContext + Job())
            val syncRuntimeInfo = createConnectedSyncRuntimeInfo()

            val handler = GeneralSyncHandler(syncRuntimeInfo, emitter.emitEvent, childScope)
            advanceTimeBy(SMALL_ADVANCE_MS)
            emitter.events.clear()

            handler.forceResolve()
            advanceTimeBy(SMALL_ADVANCE_MS)

            assertTrue(emitter.events.any { it is SyncEvent.ForceResolve })
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
    fun getConnectHostAddress_noAddress_emitsResolveAndWaits() =
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

            // It should have emitted a Resolve event
            assertTrue(emitter.events.any { it is SyncEvent.Resolve })
            assertNull(address)
            childScope.cancel()
        }

    // ========== X. callback wiring (polling vs force) ==========

    // NOTE on approach: the plan's original design had the test capture a polling-origin
    // Resolve event via `advanceTimeBy(61_000L)` to trigger SyncPollingManager's 60s tick.
    // However, `SyncPollingManager.waitForNextExecution` reads wall-clock time via
    // `DateUtils.nowEpochMilliseconds()` (Clock.System.now()), not the virtual TestScheduler
    // clock. Under `runTest`, `advanceTimeBy` advances virtual time but not wall-clock, so the
    // polling loop spins indefinitely (`delay(1000)` returns under virtual time but the
    // `while` check never sees wall-clock pass `nextExecutionTime`). That hangs `runTest`.
    //
    // To verify the wiring without refactoring the time source, we expose
    // `createPollingCallback` as an `internal` member on GeneralSyncHandler and invoke it
    // directly — it is the same factory the polling loop uses (`init { job = startPollingResolve { emitEvent(Resolve(..., createPollingCallback())) } }`).

    @Test
    fun pollingCallback_markPollFailure_incrementsFailCount() =
        runTest {
            val emitter = TestEmitter()
            val childScope = CoroutineScope(coroutineContext + Job())
            val syncRuntimeInfo = createConnectedSyncRuntimeInfo()

            val handler = GeneralSyncHandler(syncRuntimeInfo, emitter.emitEvent, childScope)
            advanceTimeBy(SMALL_ADVANCE_MS)

            // Directly obtain the polling-origin callback the polling loop would use
            val pollingCallback = handler.createPollingCallback()

            val before = handler.syncPollingManager.currentFailCount
            pollingCallback.markPollFailure()
            val after = handler.syncPollingManager.currentFailCount

            assertEquals(before + 1, after)
            childScope.cancel()
        }

    @Test
    fun forceResolveCallback_markPollFailure_isNoOp() =
        runTest {
            val emitter = TestEmitter()
            val childScope = CoroutineScope(coroutineContext + Job())
            val syncRuntimeInfo = createConnectedSyncRuntimeInfo()

            val handler = GeneralSyncHandler(syncRuntimeInfo, emitter.emitEvent, childScope)
            advanceTimeBy(SMALL_ADVANCE_MS)

            handler.forceResolve()
            advanceTimeBy(SMALL_ADVANCE_MS)

            val forceEvent =
                emitter.events.filterIsInstance<SyncEvent.ForceResolve>().first()

            val before = handler.syncPollingManager.currentFailCount
            forceEvent.callback.markPollFailure()
            val after = handler.syncPollingManager.currentFailCount

            assertEquals(before, after)
            childScope.cancel()
        }
}
