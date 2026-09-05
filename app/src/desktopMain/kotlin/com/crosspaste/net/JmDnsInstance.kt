package com.crosspaste.net

import java.net.InetAddress
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener

internal interface JmDnsInstance {

    fun addServiceListener(
        type: String,
        listener: ServiceListener,
    )

    fun removeServiceListener(
        type: String,
        listener: ServiceListener,
    )

    fun registerService(serviceInfo: ServiceInfo)

    fun list(
        type: String,
        timeout: Long,
    ): List<ServiceInfo>

    fun requestServiceInfo(
        type: String,
        name: String,
    )

    fun close()
}

internal fun interface JmDnsFactory {

    fun create(address: InetAddress): JmDnsInstance
}

internal object DefaultJmDnsFactory : JmDnsFactory {

    override fun create(address: InetAddress): JmDnsInstance = DefaultJmDnsInstance(JmDNS.create(address))
}

private class DefaultJmDnsInstance(
    private val delegate: JmDNS,
) : JmDnsInstance {

    override fun addServiceListener(
        type: String,
        listener: ServiceListener,
    ) = delegate.addServiceListener(type, listener)

    override fun removeServiceListener(
        type: String,
        listener: ServiceListener,
    ) = delegate.removeServiceListener(type, listener)

    override fun registerService(serviceInfo: ServiceInfo) = delegate.registerService(serviceInfo)

    override fun list(
        type: String,
        timeout: Long,
    ): List<ServiceInfo> = delegate.list(type, timeout).toList()

    override fun requestServiceInfo(
        type: String,
        name: String,
    ) {
        delegate.requestServiceInfo(type, name)
    }

    override fun close() = delegate.close()
}
