package com.crosspaste.net

import com.crosspaste.app.AppInfo
import com.crosspaste.app.EndpointInfoFactory
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.sync.NearbyDeviceManager
import com.crosspaste.utils.TxtRecordUtils
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
    }

    private val logger = KotlinLogging.logger {}

    private val actionChannel = Channel<List<NetworkInterfaceInfo>>(Channel.UNLIMITED)

    private val supervisorJob = SupervisorJob()

    private val scope = CoroutineScope(ioDispatcher + supervisorJob)

    private val serviceListener = DesktopServiceListener(nearbyDeviceManager)

    private val jmdnsMap: MutableMap<String, JmDNS> = ConcurrentMap()

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

    override fun request(appInstanceId: String) {
        jmdnsMap[appInstanceId]?.let { jmdns ->
            for (serviceInfo in jmdns.list(SERVICE_TYPE)) {
                jmdns.requestServiceInfo(SERVICE_TYPE, serviceInfo.name)
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
