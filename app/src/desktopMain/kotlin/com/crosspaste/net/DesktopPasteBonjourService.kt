package com.crosspaste.net

import com.crosspaste.app.AppInfo
import com.crosspaste.app.EndpointInfoFactory
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.sync.NearbyDeviceManager
import com.crosspaste.utils.TxtRecordUtils
import com.crosspaste.utils.ioDispatcher
import com.crosspaste.utils.mainDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.util.collections.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
    private val nearbyDeviceManager: NearbyDeviceManager,
) : PasteBonjourService {

    companion object {
        private const val SERVICE_TYPE = "_crosspasteService._tcp.local."
    }

    private val logger = KotlinLogging.logger {}

    private val supervisorJob = SupervisorJob()

    private val scope = CoroutineScope(ioDispatcher + supervisorJob)

    private val jmdnsMap: MutableMap<String, JmDNS> = ConcurrentMap()

    override fun registerService(): PasteBonjourService {
        val endpointInfo = endpointInfoFactory.createEndpointInfo()
        val syncInfo = SyncInfo(appInfo, endpointInfo)
        logger.debug { "Registering service: $syncInfo" }

        val txtRecordDict = TxtRecordUtils.encodeToTxtRecordDict(syncInfo)

        val serviceListener = DesktopServiceListener(nearbyDeviceManager)

        for (hostInfo in endpointInfo.hostInfoList) {
            scope.launch {
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
        }
        return this
    }

    override fun unregisterService(): PasteBonjourService {
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
        return this
    }
}

class DesktopServiceListener(
    private val nearbyDeviceManager: NearbyDeviceManager,
) : ServiceListener {

    private val logger = KotlinLogging.logger {}

    private val coroutineScope = CoroutineScope(SupervisorJob() + mainDispatcher)

    override fun serviceAdded(event: ServiceEvent) {
        logger.debug { "Service added: " + event.info }
    }

    override fun serviceRemoved(event: ServiceEvent) {
        val map: Map<String, ByteArray> = mutableMapOf()
        ByteWrangler.readProperties(map, event.info.textBytes)
        val syncInfo = TxtRecordUtils.decodeFromTxtRecordDict<SyncInfo>(map)
        coroutineScope.launch {
            nearbyDeviceManager.removeDevice(syncInfo)
        }
    }

    override fun serviceResolved(event: ServiceEvent) {
        val map: Map<String, ByteArray> = mutableMapOf()
        ByteWrangler.readProperties(map, event.info.textBytes)
        val syncInfo = TxtRecordUtils.decodeFromTxtRecordDict<SyncInfo>(map)
        coroutineScope.launch {
            nearbyDeviceManager.addDevice(syncInfo)
        }
    }
}
