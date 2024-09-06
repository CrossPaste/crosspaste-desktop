package com.crosspaste.net

import com.crosspaste.app.AppInfo
import com.crosspaste.app.EndpointInfoFactory
import com.crosspaste.app.logger
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.sync.DeviceManager
import com.crosspaste.utils.TxtRecordUtils
import com.crosspaste.utils.ioDispatcher
import io.ktor.util.collections.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.net.InetAddress
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener

class DesktopPasteBonjourService(
    private val appInfo: AppInfo,
    private val endpointInfoFactory: EndpointInfoFactory,
    private val deviceManager: DeviceManager,
) : PasteBonjourService {

    companion object {
        private const val SERVICE_TYPE = "_crosspasteService._tcp.local."
    }

    private val supervisorJob = SupervisorJob()

    private val scope = CoroutineScope(ioDispatcher + supervisorJob)

    private val jmdnsMap: MutableMap<String, JmDNS> = ConcurrentMap()

    override fun registerService(): PasteBonjourService {
        val endpointInfo = endpointInfoFactory.createEndpointInfo()
        val syncInfo = SyncInfo(appInfo, endpointInfo)
        logger.debug { "Registering service: $syncInfo" }

        val txtRecordDict = TxtRecordUtils.encodeToTxtRecordDict(syncInfo)

        val serviceListener = deviceManager as ServiceListener

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
                jmdnsMap.values.map { jmDNS ->
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
