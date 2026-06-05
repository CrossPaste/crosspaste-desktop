package com.crosspaste.net

import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.presist.OneFilePersist
import com.crosspaste.utils.DesktopLocaleUtils
import com.crosspaste.utils.getJsonUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toOkioPath
import java.nio.file.Files
import kotlin.coroutines.coroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Holds the "live" interface snapshot the service reads. Declared at file scope so
 * it is available even while the superclass constructor calls the overridden
 * [DesktopNetworkInterfaceService.getAllNetworkInterfaceInfo] — subclass instance
 * fields are not yet initialized at that point. Tests run sequentially and reset it.
 * Volatile so the flow coroutine sees writes made from the test body.
 */
private object LiveSnapshot {
    @Volatile
    var value: List<NetworkInterfaceInfo> = emptyList()
}

/**
 * Verifies the self-healing re-sourcing of [DesktopNetworkInterfaceService]:
 * a real OS network change (delivered via [NetworkStateMonitor]) re-reads the
 * live interface snapshot and re-emits, even when the config never changes.
 */
class DesktopNetworkInterfaceServiceSelfHealingTest {

    @Suppress("unused")
    private val jsonUtils = getJsonUtils()

    private val en0 = NetworkInterfaceInfo("en0", 24, "192.168.1.5")
    private val en0NewIp = NetworkInterfaceInfo("en0", 24, "192.168.1.9")

    /** Pushes network-change signals on demand, like the native monitor would. */
    private class FakeNetworkStateMonitor : NetworkStateMonitor {
        private val flow =
            MutableSharedFlow<Unit>(
                extraBufferCapacity = 1,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        override val networkChanges: Flow<Unit> = flow

        var started = false
            private set

        override fun start() {
            started = true
        }

        override fun stop() {
            started = false
        }

        fun fireNetworkChange() {
            flow.tryEmit(Unit)
        }
    }

    private class TestableService(
        configManager: DesktopConfigManager,
        monitor: NetworkStateMonitor,
        scope: CoroutineScope,
    ) : DesktopNetworkInterfaceService(configManager, monitor, scope) {

        override fun getAllNetworkInterfaceInfo(): List<NetworkInterfaceInfo> = LiveSnapshot.value
    }

    private fun setLiveSnapshot(interfaces: List<NetworkInterfaceInfo>) {
        LiveSnapshot.value = interfaces
    }

    private fun newConfigManager(): DesktopConfigManager {
        val configDir = Files.createTempDirectory("netSelfHealConfig").toOkioPath()
        configDir.toFile().deleteOnExit()
        return DesktopConfigManager(
            OneFilePersist(configDir.resolve("appConfig.json")),
            DesktopLocaleUtils,
        )
    }

    @Test
    fun `starts the monitor on construction`() =
        runTest {
            setLiveSnapshot(emptyList())
            val monitor = FakeNetworkStateMonitor()
            val job = Job()
            TestableService(newConfigManager(), monitor, CoroutineScope(coroutineContext + job))
            assertEquals(true, monitor.started)
            job.cancel()
        }

    @Test
    fun `cold start with no network heals once interfaces appear`() =
        runTest {
            setLiveSnapshot(emptyList())
            val monitor = FakeNetworkStateMonitor()
            val job = Job()
            val service = TestableService(newConfigManager(), monitor, CoroutineScope(coroutineContext + job))

            // No interfaces at construction: discovery on, config empty -> empty result.
            advanceUntilIdle()
            assertEquals(emptyList(), service.networkInterfaces.value)

            // Network comes up; a single native event should heal discovery.
            setLiveSnapshot(listOf(en0))
            monitor.fireNetworkChange()
            advanceUntilIdle()

            assertEquals(listOf(en0), service.networkInterfaces.value)
            job.cancel()
        }

    @Test
    fun `runtime ip change re-emits the new address`() =
        runTest {
            setLiveSnapshot(emptyList())
            val monitor = FakeNetworkStateMonitor()
            val job = Job()
            val service = TestableService(newConfigManager(), monitor, CoroutineScope(coroutineContext + job))

            setLiveSnapshot(listOf(en0))
            monitor.fireNetworkChange()
            advanceUntilIdle()
            assertEquals(listOf(en0), service.networkInterfaces.value)

            // Same interface name, new DHCP address.
            setLiveSnapshot(listOf(en0NewIp))
            monitor.fireNetworkChange()
            advanceUntilIdle()

            assertEquals(listOf(en0NewIp), service.networkInterfaces.value)
            job.cancel()
        }

    @Test
    fun `unchanged snapshot does not re-emit on a flap burst`() =
        runTest {
            setLiveSnapshot(emptyList())
            val monitor = FakeNetworkStateMonitor()
            val job = Job()
            val service = TestableService(newConfigManager(), monitor, CoroutineScope(coroutineContext + job))

            setLiveSnapshot(listOf(en0))
            monitor.fireNetworkChange()
            advanceUntilIdle()

            val emissions = mutableListOf<List<NetworkInterfaceInfo>>()
            val collectJob = launch { service.networkInterfaces.collect { emissions.add(it) } }
            advanceUntilIdle()

            // A burst of identical-snapshot events must collapse to zero new emissions.
            repeat(5) { monitor.fireNetworkChange() }
            advanceUntilIdle()
            collectJob.cancel()
            job.cancel()

            assertEquals(listOf(listOf(en0)), emissions)
        }
}
