package com.clipevery.net

import com.clipevery.app.AppInfo
import com.clipevery.app.logger
import com.clipevery.endpoint.EndpointInfoFactory
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.InetAddress
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo


class DesktopClipBonjourService(private val endpointInfoFactory: EndpointInfoFactory,
                                private val appInfo: AppInfo): ClipBonjourService {

    private val jmdns: JmDNS = JmDNS.create(InetAddress.getLocalHost())

    override fun registerService(): ClipBonjourService {
        val endpointInfo = endpointInfoFactory.createEndpointInfo()
        val endpointInfoJson = Json.encodeToString(endpointInfo)
        val serviceInfo = ServiceInfo.create(
            "_clipeveryService._tcp.local.",
            "clipevery_" + appInfo.appInstanceId,
            endpointInfo.port,
            0,
            0,
            endpointInfoJson.encodeToByteArray()
        )
        logger.info { "Registering service: $endpointInfoJson" }
        jmdns.registerService(serviceInfo)
        return this
    }

    override fun unregisterService(): ClipBonjourService {
        jmdns.unregisterAllServices()
        return this
    }
}
