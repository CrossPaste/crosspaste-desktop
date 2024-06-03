package com.clipevery.net

import com.clipevery.app.AppInfo
import com.clipevery.app.logger
import com.clipevery.dto.sync.SyncInfo
import com.clipevery.endpoint.EndpointInfoFactory
import com.clipevery.sync.DeviceManager
import com.clipevery.utils.TxtRecordUtils
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

    private val jmdnsMap: MutableMap<String, JmDNS> = mutableMapOf()

    override fun registerService(): ClipBonjourService {
        val endpointInfo = endpointInfoFactory.createEndpointInfo()
        val syncInfo = SyncInfo(appInfo, endpointInfo)
        logger.debug { "Registering service: $syncInfo" }

        val txtRecordDict = TxtRecordUtils.encodeToTxtRecordDict(syncInfo)

        val serviceListener = deviceManager as ServiceListener

        for (hostInfo in endpointInfo.hostInfoList) {
            val hostAddress = hostInfo.hostAddress
            val jmDNS: JmDNS = JmDNS.create(InetAddress.getByName(hostAddress))
            jmdnsMap.putIfAbsent(hostAddress, jmDNS)
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
        }
        return this
    }

    override fun unregisterService(): ClipBonjourService {
        val iterator = jmdnsMap.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val jmDNS = entry.value
            jmDNS.unregisterAllServices()
            jmDNS.close()
            iterator.remove()
        }
        return this
    }
}
