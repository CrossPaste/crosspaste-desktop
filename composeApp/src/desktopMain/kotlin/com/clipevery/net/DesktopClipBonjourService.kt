package com.clipevery.net

import com.clipevery.app.AppInfo
import com.clipevery.app.logger
import com.clipevery.dto.sync.SyncInfo
import com.clipevery.endpoint.EndpointInfoFactory
import com.clipevery.sync.DeviceManager
import com.clipevery.utils.TxtRecordUtils
import com.clipevery.utils.ioDispatcher
import io.ktor.util.collections.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.net.InetAddress
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener

class DesktopClipBonjourService(
    private val appInfo: AppInfo,
    private val endpointInfoFactory: EndpointInfoFactory,
    private val deviceManager: DeviceManager,
) : ClipBonjourService {

    companion object DesktopClipBonjourService {
        private const val SERVICE_TYPE = "_clipeveryService._tcp.local."
    }

    private val supervisorJob = SupervisorJob()

    private val scope = CoroutineScope(ioDispatcher + supervisorJob)

    private val jmdnsMap: MutableMap<String, JmDNS> = ConcurrentMap()

    override fun registerService(): ClipBonjourService {
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
                            "clipevery@${appInfo.appInstanceId}@${hostAddress.replace(".", "_")}",
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

    override fun unregisterService(): ClipBonjourService {
        for (jmDNS in jmdnsMap) {
            scope.launch {
                jmDNS.value.unregisterAllServices()
                jmDNS.value.close()
            }
        }
        jmdnsMap.clear()
        return this
    }
}
