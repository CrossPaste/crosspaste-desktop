package com.crosspaste.net

import com.crosspaste.app.EndpointInfoFactory
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.sync.NearbyDeviceManager
import com.crosspaste.sync.SyncTestFixtures
import com.crosspaste.utils.TxtRecordUtils
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.IOException
import java.net.InetAddress
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopPasteBonjourServiceTest {

    private val localInterface = NetworkInterfaceInfo("en0", 24, "192.168.1.10")
    private val secondInterface = NetworkInterfaceInfo("en1", 24, "10.0.0.10")

    @Test
    fun `setup failure on one interface does not block another and is retried`() =
        runTest {
            val factory = RecordingJmDnsFactory()
            factory.createFailures[localInterface.hostAddress] = 1
            val fixture = createFixture(factory)
            runCurrent()

            fixture.service.processNetworkChange(listOf(localInterface, secondInterface))

            assertEquals(1, factory.createAttempts[localInterface.hostAddress])
            assertNotNull(factory.latest(secondInterface.hostAddress).listener)

            advanceTimeBy(2000)
            runCurrent()

            assertEquals(2, factory.createAttempts[localInterface.hostAddress])
            assertNotNull(factory.latest(localInterface.hostAddress).listener)
        }

    @Test
    fun `registration failure closes partially created instance`() =
        runTest {
            val factory = RecordingJmDnsFactory(failRegistrationFor = setOf(localInterface.hostAddress))
            val fixture = createFixture(factory)
            runCurrent()

            fixture.service.processNetworkChange(listOf(localInterface))

            assertTrue(factory.latest(localInterface.hostAddress).closed)
        }

    @Test
    fun `close failure never leaves old instance active`() =
        runTest {
            val factory = RecordingJmDnsFactory(failCloseFor = setOf(localInterface.hostAddress))
            val fixture = createFixture(factory)
            runCurrent()
            fixture.service.processNetworkChange(listOf(localInterface))
            val first = factory.latest(localInterface.hostAddress)

            fixture.service.processNetworkChange(listOf(localInterface))

            assertTrue(first.closeAttempted)
            assertEquals(2, factory.createAttempts[localInterface.hostAddress])
            assertNotNull(factory.latest(localInterface.hostAddress).listener)
        }

    @Test
    fun `removal with empty TXT record still removes valid service name`() {
        val removed = mutableListOf<Pair<String, String>>()
        val listener =
            DesktopServiceListener(
                isActive = { true },
                onResolved = { _, _ -> },
                onRemoved = { serviceName, appInstanceId ->
                    removed += serviceName to appInstanceId
                },
            )
        val serviceInfo =
            ServiceInfo.create(
                DesktopPasteBonjourService.SERVICE_TYPE,
                "crosspaste@peer-1@192_168_1_20",
                13129,
                "",
            )

        listener.serviceRemoved(serviceEvent(serviceInfo))

        assertEquals(
            listOf("crosspaste@peer-1@192_168_1_20" to "peer-1"),
            removed,
        )
    }

    @Test
    fun `malformed service names never remove a device`() {
        val removed = mutableListOf<String>()
        val listener =
            DesktopServiceListener(
                isActive = { true },
                onResolved = { _, _ -> },
                onRemoved = { _, appInstanceId -> removed += appInstanceId },
            )

        listOf(
            "other@peer-1@host",
            "crosspaste@@host",
            "crosspaste@peer-1@",
            "crosspaste@peer-1@host@extra",
        ).forEach { serviceName ->
            val serviceInfo =
                ServiceInfo.create(
                    DesktopPasteBonjourService.SERVICE_TYPE,
                    serviceName,
                    13129,
                    "",
                )
            listener.serviceRemoved(serviceEvent(serviceInfo))
        }

        assertTrue(removed.isEmpty())
    }

    @Test
    fun `device remains nearby until all interface sources are removed`() =
        runTest {
            val factory = RecordingJmDnsFactory()
            val fixture = createFixture(factory)
            runCurrent()
            fixture.service.processNetworkChange(listOf(localInterface, secondInterface))
            val syncInfo = SyncTestFixtures.createSyncInfo(appInstanceId = "peer-1")

            val first = factory.latest(localInterface.hostAddress)
            val second = factory.latest(secondInterface.hostAddress)
            first.emitResolved(syncInfo, "crosspaste@peer-1@first")
            second.emitResolved(syncInfo, "crosspaste@peer-1@second")

            first.emitRemoved("crosspaste@peer-1@first")
            assertTrue("peer-1" in fixture.nearby.devices)
            assertTrue(fixture.nearby.removedIds.isEmpty())

            second.emitRemoved("crosspaste@peer-1@second")
            assertFalse("peer-1" in fixture.nearby.devices)
            assertEquals(listOf("peer-1"), fixture.nearby.removedIds)
        }

    @Test
    fun `network rebuild removes old presence and ignores stale callbacks`() =
        runTest {
            val factory = RecordingJmDnsFactory()
            val fixture = createFixture(factory)
            runCurrent()
            fixture.service.processNetworkChange(listOf(localInterface))
            val oldInstance = factory.latest(localInterface.hostAddress)
            val syncInfo = SyncTestFixtures.createSyncInfo(appInstanceId = "peer-1")
            oldInstance.emitResolved(syncInfo, "crosspaste@peer-1@old")
            assertEquals(1, fixture.nearby.addedIds.size)

            fixture.service.processNetworkChange(listOf(secondInterface))

            assertTrue(oldInstance.closed)
            assertFalse("peer-1" in fixture.nearby.devices)
            oldInstance.emitResolved(syncInfo, "crosspaste@peer-1@old")
            assertEquals(1, fixture.nearby.addedIds.size)
        }

    @Test
    fun `target refresh uses presence cache and throttles cache misses`() =
        runTest {
            val factory = RecordingJmDnsFactory()
            val fixture = createFixture(factory)
            runCurrent()
            fixture.service.processNetworkChange(listOf(localInterface))
            val instance = factory.latest(localInterface.hostAddress)

            fixture.service.refreshTarget("peer-1", emptyList())
            runCurrent()
            fixture.service.refreshTarget("peer-1", emptyList())
            runCurrent()
            assertEquals(1, instance.listCalls)

            instance.emitResolved(
                SyncTestFixtures.createSyncInfo(appInstanceId = "peer-2"),
                "crosspaste@peer-2@cached",
            )
            fixture.service.refreshTarget("peer-2", emptyList())
            runCurrent()

            assertEquals(1, instance.listCalls)
            assertEquals(listOf("crosspaste@peer-2@cached"), instance.requestedServices)
        }

    @Test
    fun `concurrent manual refresh requests share one scan`() =
        runTest {
            val factory = RecordingJmDnsFactory()
            val fixture = createFixture(factory)
            runCurrent()
            fixture.service.processNetworkChange(listOf(localInterface))
            val instance = factory.latest(localInterface.hostAddress)

            fixture.service.refreshAll()
            fixture.service.refreshAll()
            runCurrent()

            assertEquals(1, instance.listCalls)
            assertEquals(1, fixture.nearby.searchStarts)
            advanceTimeBy(2000)
            runCurrent()
            assertEquals(1, fixture.nearby.searchStops)
        }

    private fun TestScope.createFixture(factory: RecordingJmDnsFactory): Fixture {
        val scope = backgroundScope
        val endpointInfoFactory = mockk<EndpointInfoFactory>()
        coEvery { endpointInfoFactory.createEndpointInfo(any()) } answers {
            SyncTestFixtures.createEndpointInfo(hostInfoList = firstArg())
        }
        val nearby = RecordingNearbyDeviceManager(scope)
        val networkInterfaceService = MutableNetworkInterfaceService()
        val service =
            DesktopPasteBonjourService(
                appInfo = SyncTestFixtures.createAppInfo(appInstanceId = "self"),
                endpointInfoFactory = endpointInfoFactory,
                nearbyDeviceManager = nearby,
                networkInterfaceService = networkInterfaceService,
                scope = scope,
                jmdnsFactory = factory,
                cleanupDispatcher = StandardTestDispatcher(testScheduler),
            )
        return Fixture(service, nearby)
    }

    private data class Fixture(
        val service: DesktopPasteBonjourService,
        val nearby: RecordingNearbyDeviceManager,
    )

    private class MutableNetworkInterfaceService : NetworkInterfaceService {
        override val networkInterfaces = MutableStateFlow<List<NetworkInterfaceInfo>>(emptyList())

        override fun getAllNetworkInterfaceInfo(): List<NetworkInterfaceInfo> = networkInterfaces.value

        override fun getSortedNetworkInterfaceInfo(): List<NetworkInterfaceInfo> = networkInterfaces.value

        override fun getPreferredNetworkInterface(): NetworkInterfaceInfo? = networkInterfaces.value.firstOrNull()

        override fun clearProviderCache() = Unit
    }

    private class RecordingNearbyDeviceManager(
        override val nearbyDeviceScope: CoroutineScope,
    ) : NearbyDeviceManager {
        private val _nearbySyncInfos = MutableStateFlow<List<SyncInfo>>(emptyList())
        override val nearbySyncInfos: StateFlow<List<SyncInfo>> = _nearbySyncInfos
        private val _searching = MutableStateFlow(false)
        override val searching: StateFlow<Boolean> = _searching

        val devices = mutableMapOf<String, SyncInfo>()
        val addedIds = mutableListOf<String>()
        val removedIds = mutableListOf<String>()
        var searchStarts = 0
        var searchStops = 0

        override fun addDevice(syncInfo: SyncInfo) {
            val id = syncInfo.appInfo.appInstanceId
            devices[id] = syncInfo
            addedIds += id
            _nearbySyncInfos.value = devices.values.toList()
        }

        override fun removeDevice(appInstanceId: String) {
            devices.remove(appInstanceId)
            removedIds += appInstanceId
            _nearbySyncInfos.value = devices.values.toList()
        }

        override fun startSearching() {
            searchStarts++
            _searching.value = true
        }

        override fun stopSearching() {
            searchStops++
            _searching.value = false
        }
    }

    private class RecordingJmDnsFactory(
        private val failRegistrationFor: Set<String> = emptySet(),
        private val failCloseFor: Set<String> = emptySet(),
    ) : JmDnsFactory {
        val createAttempts = mutableMapOf<String, Int>()
        val createFailures = mutableMapOf<String, Int>()
        private val instances = mutableMapOf<String, MutableList<RecordingJmDnsInstance>>()

        override fun create(address: InetAddress): JmDnsInstance {
            val hostAddress = address.hostAddress
            createAttempts[hostAddress] = createAttempts.getOrDefault(hostAddress, 0) + 1
            val failuresLeft = createFailures.getOrDefault(hostAddress, 0)
            if (failuresLeft > 0) {
                createFailures[hostAddress] = failuresLeft - 1
                throw IOException("simulated create failure")
            }

            val instance =
                RecordingJmDnsInstance(
                    failRegistration = hostAddress in failRegistrationFor,
                    failClose = hostAddress in failCloseFor,
                )
            instances.getOrPut(hostAddress, ::mutableListOf) += instance
            return instance
        }

        fun latest(hostAddress: String): RecordingJmDnsInstance =
            instances[hostAddress]?.lastOrNull()
                ?: error("No JmDNS instance for $hostAddress")
    }

    private class RecordingJmDnsInstance(
        private val failRegistration: Boolean,
        private val failClose: Boolean,
    ) : JmDnsInstance {
        var listener: ServiceListener? = null
        var closed = false
        var closeAttempted = false
        var listCalls = 0
        val requestedServices = mutableListOf<String>()

        override fun addServiceListener(
            type: String,
            listener: ServiceListener,
        ) {
            this.listener = listener
        }

        override fun removeServiceListener(
            type: String,
            listener: ServiceListener,
        ) {
            if (this.listener === listener) {
                this.listener = null
            }
        }

        override fun registerService(serviceInfo: ServiceInfo) {
            if (failRegistration) throw IOException("simulated registration failure")
        }

        override fun list(
            type: String,
            timeout: Long,
        ): List<ServiceInfo> {
            listCalls++
            return emptyList()
        }

        override fun requestServiceInfo(
            type: String,
            name: String,
        ) {
            requestedServices += name
        }

        override fun close() {
            closeAttempted = true
            if (failClose) throw IOException("simulated close failure")
            closed = true
        }

        fun emitResolved(
            syncInfo: SyncInfo,
            serviceName: String,
        ) {
            val txtRecord = TxtRecordUtils.encodeToTxtRecordDict(syncInfo)
            val serviceInfo =
                ServiceInfo.create(
                    DesktopPasteBonjourService.SERVICE_TYPE,
                    serviceName,
                    syncInfo.endpointInfo.port,
                    0,
                    0,
                    txtRecord,
                )
            listener?.serviceResolved(serviceEvent(serviceInfo))
        }

        fun emitRemoved(serviceName: String) {
            val serviceInfo =
                ServiceInfo.create(
                    DesktopPasteBonjourService.SERVICE_TYPE,
                    serviceName,
                    13129,
                    "",
                )
            listener?.serviceRemoved(serviceEvent(serviceInfo))
        }
    }

    companion object {
        private fun serviceEvent(serviceInfo: ServiceInfo): ServiceEvent =
            mockk {
                every { info } returns serviceInfo
            }
    }
}
