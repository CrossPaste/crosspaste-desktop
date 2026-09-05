package com.crosspaste.net

import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.presist.OneFilePersist
import com.crosspaste.utils.DesktopLocaleUtils
import com.crosspaste.utils.getJsonUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
@OptIn(ExperimentalCoroutinesApi::class)
class DesktopNetworkInterfaceServiceSelfHealingTest {

    @Suppress("unused")
    private val jsonUtils = getJsonUtils()

    private val en0 = NetworkInterfaceInfo("en0", 24, "192.168.1.5")
    private val en0NewIp = NetworkInterfaceInfo("en0", 24, "192.168.1.9")
    private val en5 = NetworkInterfaceInfo("en5", 24, "10.0.0.5")

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

    @Test
    fun `runtime loss of the chosen interface falls back in memory without clobbering the preference`() =
        runTest {
            val configManager = newConfigManager()
            // The user explicitly picked en5 while several interfaces were available.
            configManager.updateConfig(
                "useNetworkInterfaces",
                jsonUtils.JSON.encodeToString(listOf("en5")),
            )

            setLiveSnapshot(listOf(en5))
            val monitor = FakeNetworkStateMonitor()
            val job = Job()
            val service = TestableService(configManager, monitor, CoroutineScope(coroutineContext + job))
            advanceUntilIdle()
            assertEquals(listOf(en5), service.networkInterfaces.value)

            // en5 goes offline, only en0 remains: bind to en0 so discovery survives...
            setLiveSnapshot(listOf(en0))
            monitor.fireNetworkChange()
            advanceUntilIdle()
            assertEquals(listOf(en0), service.networkInterfaces.value)
            // ...but the persisted preference must be left untouched.
            assertEquals(
                listOf("en5"),
                jsonUtils.JSON.decodeFromString<List<String>>(
                    configManager.getCurrentConfig().useNetworkInterfaces,
                ),
            )

            // en5 returns: heal back to the user's actual choice.
            setLiveSnapshot(listOf(en5))
            monitor.fireNetworkChange()
            advanceUntilIdle()
            assertEquals(listOf(en5), service.networkInterfaces.value)

            job.cancel()
        }

    @Test
    fun `discovery switched off binds nothing even while the machine is online`() =
        runTest {
            val configManager = newConfigManager()
            // What the settings toggle writes when the user turns discovery off.
            configManager.updateConfig(
                listOf("useNetworkInterfaces", "enableDiscovery"),
                listOf("[]", false),
            )

            setLiveSnapshot(listOf(en0, en5))
            val monitor = FakeNetworkStateMonitor()
            val job = Job()
            val service = TestableService(configManager, monitor, CoroutineScope(coroutineContext + job))
            advanceUntilIdle()
            assertEquals(emptyList(), service.networkInterfaces.value)

            // The offline fallback must not resurrect discovery on a network event...
            monitor.fireNetworkChange()
            advanceUntilIdle()
            assertEquals(emptyList(), service.networkInterfaces.value)
            // ...nor persist an auto-selected interface behind the user's back.
            assertEquals("[]", configManager.getCurrentConfig().useNetworkInterfaces)

            job.cancel()
        }

    @Test
    fun `toggling only enableDiscovery re-resolves without an interface list change`() =
        runTest {
            val configManager = newConfigManager()
            configManager.updateConfig(
                "useNetworkInterfaces",
                jsonUtils.JSON.encodeToString(listOf("en0")),
            )

            setLiveSnapshot(listOf(en0))
            val monitor = FakeNetworkStateMonitor()
            val job = Job()
            val service = TestableService(configManager, monitor, CoroutineScope(coroutineContext + job))
            advanceUntilIdle()
            assertEquals(listOf(en0), service.networkInterfaces.value)

            // CLI-style write: only the boolean changes, the list stays ["en0"].
            configManager.updateConfig("enableDiscovery", false)
            advanceUntilIdle()
            assertEquals(emptyList(), service.networkInterfaces.value)

            configManager.updateConfig("enableDiscovery", true)
            advanceUntilIdle()
            assertEquals(listOf(en0), service.networkInterfaces.value)

            job.cancel()
        }

    @Test
    fun `discovery enabled while offline auto-selects once the network returns`() =
        runTest {
            val configManager = newConfigManager()
            // Toggle switched on with no interface available: intent persisted, list empty.
            configManager.updateConfig(
                listOf("useNetworkInterfaces", "enableDiscovery"),
                listOf("[]", true),
            )

            setLiveSnapshot(emptyList())
            val monitor = FakeNetworkStateMonitor()
            val job = Job()
            val service = TestableService(configManager, monitor, CoroutineScope(coroutineContext + job))
            advanceUntilIdle()
            assertEquals(emptyList(), service.networkInterfaces.value)

            setLiveSnapshot(listOf(en0))
            monitor.fireNetworkChange()
            advanceUntilIdle()

            assertEquals(listOf(en0), service.networkInterfaces.value)
            // Bootstrap default is persisted so the choice survives the next restart.
            assertEquals(
                listOf("en0"),
                jsonUtils.JSON.decodeFromString<List<String>>(
                    configManager.getCurrentConfig().useNetworkInterfaces,
                ),
            )

            job.cancel()
        }
}
