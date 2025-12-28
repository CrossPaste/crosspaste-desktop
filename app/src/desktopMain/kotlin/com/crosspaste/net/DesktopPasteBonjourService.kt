package com.crosspaste.net

import com.crosspaste.app.AppInfo
import com.crosspaste.app.EndpointInfoFactory
import com.crosspaste.db.sync.HostInfo
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.sync.NearbyDeviceManager
import com.crosspaste.utils.TxtRecordUtils
import com.crosspaste.utils.getDateUtils
import com.crosspaste.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.util.collections.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.net.InetAddress
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener
import javax.jmdns.impl.util.ByteWrangler

class DesktopPasteBonjourService(
    private val appInfo: AppInfo,
    private val endpointInfoFactory: EndpointInfoFactory,
    nearbyDeviceManager: NearbyDeviceManager,
    private val networkInterfaceService: NetworkInterfaceService,
) : PasteBonjourService {

    companion object {
        private const val SERVICE_TYPE = "_crosspasteService._tcp.local."

        private const val ACTIVE_SCAN_TIMEOUT = 3000L // jmdns.list blocking time
        private const val INTERFACE_SCAN_INTERVAL = 5000L // Throttle for heavy scan per interface
        private const val DEVICE_RESOLVE_INTERVAL = 2000L // Throttle for specific device resolution

        private val dateUtils = getDateUtils()
    }

    private val logger = KotlinLogging.logger {}

    private val actionChannel = Channel<List<NetworkInterfaceInfo>>(Channel.UNLIMITED)

    private val supervisorJob = SupervisorJob()

    private val scope = CoroutineScope(ioDispatcher + supervisorJob)

    private val serviceListener = DesktopServiceListener(nearbyDeviceManager)

    private val jmdnsMap: MutableMap<String, JmDNS> = ConcurrentMap()

    private val interfaceScanThrottle: MutableMap<String, Long> = ConcurrentMap()

    private val deviceResolveThrottle: MutableMap<String, Long> = ConcurrentMap()

    init {
        scope.launch {
            networkInterfaceService.networkInterfaces.collect { interfaces ->
                actionChannel.send(interfaces)
            }
        }

        scope.launch {
            for (interfaces in actionChannel) {
                processNetworkChange(interfaces)
            }
        }
    }

    fun processNetworkChange(interfaces: List<NetworkInterfaceInfo>) {
        close()

        setup(interfaces)
    }

    fun setup(interfaces: List<NetworkInterfaceInfo>) {
        val hostInfoList = interfaces.map { info -> info.toHostInfo() }
        val endpointInfo = endpointInfoFactory.createEndpointInfo(hostInfoList)
        val syncInfo = SyncInfo(appInfo, endpointInfo)

        logger.debug { "Registering service: $syncInfo" }

        val txtRecordDict = TxtRecordUtils.encodeToTxtRecordDict(syncInfo)

        val deferred =
            scope.async {
                hostInfoList
                    .map { hostInfo ->
                        async {
                            val hostAddress = hostInfo.hostAddress
                            jmdnsMap.computeIfAbsent(hostAddress) {
                                val jmDNS = JmDNS.create(InetAddress.getByName(hostAddress))
                                val serviceInfo =
                                    ServiceInfo.create(
                                        SERVICE_TYPE,
                                        "crosspaste@${appInfo.appInstanceId}@${hostAddress.replace(".", "_")}",
                                        endpointInfo.port,
                                        0,
                                        0,
                                        txtRecordDict,
                                    )
                                jmDNS.addServiceListener(SERVICE_TYPE, serviceListener)
                                jmDNS.registerService(serviceInfo)
                                jmDNS
                            }
                        }
                    }.awaitAll()
            }

        runBlocking { deferred.await() }
    }

    override fun request(
        appInstanceId: String,
        hostInfoList: List<HostInfo>,
    ) {
        // Use the existing scope to avoid blocking the caller
        scope.launch {
            val currentTime = dateUtils.nowEpochMilliseconds()

            // Run scans in parallel across all interfaces
            jmdnsMap
                .map { (hostAddress, jmdns) ->
                    async {
                        processRequestOnInterface(hostAddress, jmdns, appInstanceId, currentTime)
                    }
                }.awaitAll()
        }
    }

    private fun processRequestOnInterface(
        hostAddress: String,
        jmdns: JmDNS,
        appInstanceId: String,
        currentTime: Long,
    ) {
        val servicePrefix = "crosspaste@$appInstanceId@"

        // 1. Try to find from local cache first
        var targetService = jmdns.list(SERVICE_TYPE).find { it.name.startsWith(servicePrefix) }

        // 2. Atomic check for heavy scan (Interface-level)
        if (targetService == null) {
            var performedScan = false
            interfaceScanThrottle.compute(hostAddress) { _, lastTime ->
                if (lastTime == null || currentTime - lastTime > INTERFACE_SCAN_INTERVAL) {
                    performedScan = true
                    currentTime // Update with current time
                } else {
                    lastTime // Keep the old timestamp
                }
            }

            if (performedScan) {
                logger.info { "Performing active scan for $appInstanceId on $hostAddress" }
                // list(type, timeout) is blocking
                val freshServices = jmdns.list(SERVICE_TYPE, ACTIVE_SCAN_TIMEOUT)
                targetService = freshServices.find { it.name.startsWith(servicePrefix) }
            }
        }

        // 3. Atomic check for device resolution (Device-level)
        if (targetService != null) {
            val deviceKey = "${hostAddress}_$appInstanceId"
            var shouldResolve = false
            deviceResolveThrottle.compute(deviceKey) { _, lastTime ->
                if (lastTime == null || currentTime - lastTime > DEVICE_RESOLVE_INTERVAL) {
                    shouldResolve = true
                    currentTime
                } else {
                    lastTime
                }
            }

            if (shouldResolve) {
                logger.info { "Requesting service info for $appInstanceId via $hostAddress" }
                jmdns.requestServiceInfo(SERVICE_TYPE, targetService.name)
            }
        }
    }

    override fun close() {
        runCatching {
            val deferred =
                scope.async {
                    jmdnsMap.values
                        .map { jmDNS ->
                            async {
                                jmDNS.unregisterAllServices()
                                jmDNS.close()
                            }
                        }.awaitAll()
                }
            runBlocking { deferred.await() }
            jmdnsMap.clear()
            interfaceScanThrottle.clear()
            deviceResolveThrottle.clear()
        }
    }
}

class DesktopServiceListener(
    private val nearbyDeviceManager: NearbyDeviceManager,
) : ServiceListener {

    private val logger = KotlinLogging.logger {}

    override fun serviceAdded(event: ServiceEvent) {
        logger.debug { "Service added: " + event.info }
    }

    override fun serviceRemoved(event: ServiceEvent) {
        val map: Map<String, ByteArray> = mutableMapOf()
        ByteWrangler.readProperties(map, event.info.textBytes)
        val syncInfo = TxtRecordUtils.decodeFromTxtRecordDict<SyncInfo>(map)
        nearbyDeviceManager.removeDevice(syncInfo)
    }

    override fun serviceResolved(event: ServiceEvent) {
        val map: Map<String, ByteArray> = mutableMapOf()
        ByteWrangler.readProperties(map, event.info.textBytes)
        val syncInfo = TxtRecordUtils.decodeFromTxtRecordDict<SyncInfo>(map)
        nearbyDeviceManager.addDevice(syncInfo)
    }
}
