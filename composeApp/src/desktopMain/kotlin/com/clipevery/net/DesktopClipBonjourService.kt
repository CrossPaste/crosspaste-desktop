package com.clipevery.net

import com.clipevery.app.AppInfo
import com.clipevery.app.logger
import com.clipevery.endpoint.EndpointInfoFactory
import com.clipevery.model.sync.ResponseSyncInfo
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.InetAddress
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo


class DesktopClipBonjourService(private val endpointInfoFactory: EndpointInfoFactory,
                                private val appInfo: AppInfo): ClipBonjourService {

    private val jmdnsMap: MutableMap<String, JmDNS> = mutableMapOf()

    override fun registerService(): ClipBonjourService {
        val endpointInfo = endpointInfoFactory.createEndpointInfo()
        val responseSyncInfo = ResponseSyncInfo(appInfo, endpointInfo)
        val responseSyncInfoJson = Json.encodeToString(responseSyncInfo)
        val responseSyncInfoJsonBytes = responseSyncInfoJson.encodeToByteArray()
        for (hostInfo in endpointInfo.hostInfoList) {
            val jmDNS: JmDNS = JmDNS.create(InetAddress.getByName(hostInfo.hostAddress))
            jmdnsMap.putIfAbsent(hostInfo.hostAddress, jmDNS)
            val serviceInfo = ServiceInfo.create(
                "_clipeveryService._tcp.local.",
                "clipevery@${appInfo.appInstanceId}@${hostInfo.hostAddress.replace(".", "_")}",
                endpointInfo.port,
                0,
                0,
                responseSyncInfoJsonBytes
            )
            jmDNS.registerService(serviceInfo)
        }
        logger.info { "Registering service: $responseSyncInfoJson" }
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
