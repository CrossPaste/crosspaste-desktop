package com.crosspaste.sync

import com.crosspaste.config.TestAppConfig
import com.crosspaste.config.TestConfigManager
import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.db.sync.SyncState
import com.crosspaste.sync.SyncTestFixtures.createSyncRuntimeInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PastePullTriggerTest {

    private val peer = "remote-device"

    private fun info(
        connectState: Int,
        appInstanceId: String = peer,
        allowReceive: Boolean = true,
    ): SyncRuntimeInfo =
        createSyncRuntimeInfo(
            appInstanceId = appInstanceId,
            connectState = connectState,
            allowReceive = allowReceive,
        )

    private class Harness(
        val service: PastePullTrigger,
        val infos: MutableStateFlow<List<SyncRuntimeInfo>>,
        val configManager: TestConfigManager,
        val pastePullService: PastePullService,
    )

    private fun TestScope.harness(
        initial: List<SyncRuntimeInfo> = emptyList(),
        enabled: Boolean = true,
        pastePullService: PastePullService = mockk(relaxed = true),
    ): Harness {
        val infos = MutableStateFlow(initial)
        val syncManager = mockk<SyncManager>(relaxed = true)
        every { syncManager.realTimeSyncRuntimeInfos } returns infos
        val configManager = TestConfigManager(TestAppConfig(enableAutoPastePull = enabled))
        // A child of backgroundScope, so stop() can cancel it without failing the test
        val scope = CoroutineScope(backgroundScope.coroutineContext + Job(backgroundScope.coroutineContext[Job]))
        val service = PastePullTrigger(configManager, pastePullService, syncManager, scope)
        return Harness(service, infos, configManager, pastePullService)
    }

    @Test
    fun `peer already connected at startup is pulled once`() =
        runTest {
            val h = harness(initial = listOf(info(SyncState.CONNECTED)))

            h.service.start()
            runCurrent()

            coVerify(exactly = 1) { h.pastePullService.pullDevice(peer) }
        }

    @Test
    fun `disabled auto pull never pulls on reconnect`() =
        runTest {
            val h = harness(initial = listOf(info(SyncState.DISCONNECTED)), enabled = false)

            h.service.start()
            runCurrent()
            h.infos.value = listOf(info(SyncState.CONNECTED))
            runCurrent()

            coVerify(exactly = 0) { h.pastePullService.pullDevice(any(), any()) }
        }

    @Test
    fun `reconnect pulls again while staying connected does not`() =
        runTest {
            val h = harness(initial = listOf(info(SyncState.DISCONNECTED)))
            h.service.start()
            runCurrent()

            h.infos.value = listOf(info(SyncState.CONNECTED))
            runCurrent()
            // An unrelated row change while still connected
            h.infos.value = listOf(info(SyncState.CONNECTED).copy(noteName = "renamed"))
            runCurrent()
            coVerify(exactly = 1) { h.pastePullService.pullDevice(peer) }

            h.infos.value = listOf(info(SyncState.DISCONNECTED))
            runCurrent()
            h.infos.value = listOf(info(SyncState.CONNECTED))
            runCurrent()
            coVerify(exactly = 2) { h.pastePullService.pullDevice(peer) }
        }

    @Test
    fun `triggers during a running pull collapse into one follow-up pull`() =
        runTest {
            val pastePullService = mockk<PastePullService>()
            val gate = CompletableDeferred<Unit>()
            var running = 0
            var maxRunning = 0
            var calls = 0
            coEvery { pastePullService.pullDevice(peer, any()) } coAnswers {
                calls++
                running++
                maxRunning = maxOf(maxRunning, running)
                if (calls == 1) gate.await()
                running--
            }
            val h = harness(initial = listOf(info(SyncState.CONNECTED)), pastePullService = pastePullService)
            h.service.start()
            runCurrent()

            repeat(3) {
                h.infos.value = listOf(info(SyncState.DISCONNECTED))
                runCurrent()
                h.infos.value = listOf(info(SyncState.CONNECTED))
                runCurrent()
            }
            gate.complete(Unit)
            runCurrent()

            assertEquals(2, calls)
            assertEquals(1, maxRunning)
        }

    @Test
    fun `a failed pull does not stop later pulls`() =
        runTest {
            val pastePullService = mockk<PastePullService>()
            var calls = 0
            coEvery { pastePullService.pullDevice(peer, any()) } coAnswers {
                calls++
                if (calls == 1) error("network down")
            }
            val h = harness(initial = listOf(info(SyncState.CONNECTED)), pastePullService = pastePullService)
            h.service.start()
            runCurrent()

            h.infos.value = listOf(info(SyncState.DISCONNECTED))
            runCurrent()
            h.infos.value = listOf(info(SyncState.CONNECTED))
            runCurrent()

            assertEquals(2, calls)
        }

    @Test
    fun `removing a device cancels its in-flight pull`() =
        runTest {
            val pastePullService = mockk<PastePullService>()
            val started = CompletableDeferred<Unit>()
            var finished = false
            coEvery { pastePullService.pullDevice(peer, any()) } coAnswers {
                started.complete(Unit)
                CompletableDeferred<Unit>().await()
                finished = true
            }
            val h = harness(initial = listOf(info(SyncState.CONNECTED)), pastePullService = pastePullService)
            h.service.start()
            runCurrent()
            assertTrue(started.isCompleted)

            h.infos.value = emptyList()
            runCurrent()

            assertTrue(!finished)
            // Re-pairing starts a fresh worker
            h.infos.value = listOf(info(SyncState.CONNECTED))
            runCurrent()
            coVerify(exactly = 2) { pastePullService.pullDevice(peer, any()) }
        }

    @Test
    fun `enabling auto pull catches up on peers already connected`() =
        runTest {
            val h = harness(initial = listOf(info(SyncState.CONNECTED)), enabled = false)
            h.service.start()
            runCurrent()

            h.configManager.updateConfig("enableAutoPastePull", true)
            runCurrent()

            coVerify(exactly = 1) { h.pastePullService.pullDevice(peer) }
        }

    @Test
    fun `enabling allowReceive on an already connected peer triggers a pull`() =
        runTest {
            val h = harness(initial = listOf(info(SyncState.CONNECTED, allowReceive = false)))
            h.service.start()
            runCurrent()

            coVerify(exactly = 0) { h.pastePullService.pullDevice(peer) }

            h.infos.value = listOf(info(SyncState.CONNECTED, allowReceive = true))
            runCurrent()

            coVerify(exactly = 1) { h.pastePullService.pullDevice(peer) }
        }

    @Test
    fun `stop cancels the workers`() =
        runTest {
            val pastePullService = mockk<PastePullService>()
            var finished = false
            coEvery { pastePullService.pullDevice(peer, any()) } coAnswers {
                CompletableDeferred<Unit>().await()
                finished = true
            }
            val h = harness(initial = listOf(info(SyncState.CONNECTED)), pastePullService = pastePullService)
            h.service.start()
            runCurrent()

            h.service.stop()
            h.infos.value = listOf(info(SyncState.DISCONNECTED))
            runCurrent()
            h.infos.value = listOf(info(SyncState.CONNECTED))
            runCurrent()

            assertTrue(!finished)
            coVerify(exactly = 1) { pastePullService.pullDevice(peer, any()) }
        }
}
