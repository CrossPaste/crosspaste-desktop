package com.clipevery.net

import com.clipevery.app.AppInfo
import com.clipevery.app.logger
import com.clipevery.dto.sync.SyncInfo
import com.clipevery.endpoint.EndpointInfoFactory
import com.clipevery.utils.TxtRecordUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.InetAddress
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener
import javax.jmdns.impl.util.ByteWrangler

class DesktopClipBonjourService(
    private val appInfo: AppInfo,
    private val endpointInfoFactory: EndpointInfoFactory,
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

        for (hostInfo in endpointInfo.hostInfoList) {
            val jmDNS: JmDNS = JmDNS.create(InetAddress.getByName(hostInfo.hostAddress))
            jmdnsMap.putIfAbsent(hostInfo.hostAddress, jmDNS)
            val serviceInfo =
                ServiceInfo.create(
                    SERVICE_TYPE,
                    "clipevery@${appInfo.appInstanceId}@${hostInfo.hostAddress.replace(".", "_")}",
                    endpointInfo.port,
                    0,
                    0,
                    txtRecordDict,
                )
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

    override suspend fun search(timeMillis: Long): List<SyncInfo> {
        return withContext(Dispatchers.IO) {
            val jmdns = JmDNS.create("0.0.0.0")
            jmdns.use {
                val searchSyncInfoListener = SearchSyncInfoListener(appInfo.appInstanceId)
                it.addServiceListener(SERVICE_TYPE, searchSyncInfoListener)
                delay(timeMillis)
                searchSyncInfoListener.getSyncInfoList()
            }
        }
    }
}

class SearchSyncInfoListener(val appInstanceId: String) : ServiceListener {

    val logger = KotlinLogging.logger {}

    private val syncInfoMap: MutableMap<String, SyncInfo> = mutableMapOf()

    override fun serviceAdded(event: ServiceEvent) {
        logger.info { "Service added: " + event.info }
    }

    override fun serviceRemoved(event: ServiceEvent) {
        logger.info { "Service removed: " + event.info }
        val map: Map<String, ByteArray> = mutableMapOf()
        ByteWrangler.readProperties(map, event.info.textBytes)
        val syncInfo = TxtRecordUtils.decodeFromTxtRecordDict<SyncInfo>(map)
        syncInfoMap.remove(syncInfo.appInfo.appInstanceId)
    }

    override fun serviceResolved(event: ServiceEvent) {
        logger.info { "Service resolved: " + event.info }
        val map: Map<String, ByteArray> = mutableMapOf()
        ByteWrangler.readProperties(map, event.info.textBytes)
        val syncInfo = TxtRecordUtils.decodeFromTxtRecordDict<SyncInfo>(map)
        if (syncInfo.appInfo.appInstanceId != appInstanceId) {
            syncInfoMap[syncInfo.appInfo.appInstanceId] = syncInfo
        }
    }

    fun getSyncInfoList(): List<SyncInfo> {
        return syncInfoMap.values.toList()
    }
}
