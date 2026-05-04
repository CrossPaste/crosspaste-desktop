package com.crosspaste.e2e.protocol

import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.e2e.net.NetworkUtils
import com.crosspaste.utils.TxtRecordUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.util.collections.ConcurrentMap
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceListener
import javax.jmdns.impl.util.ByteWrangler

class DiscoveryDriver {

    companion object {
        const val SERVICE_TYPE: String = "_crosspasteService._tcp.local."
        private const val ACTIVE_SCAN_TIMEOUT_MS = 1500L
    }

    private val logger = KotlinLogging.logger {}

    private val jmdnsInstances: List<JmDNS> = createJmdnsInstances()

    private val resolved: ConcurrentMap<String, SyncInfo> = ConcurrentMap()

    private val events: Channel<SyncInfo> = Channel(Channel.UNLIMITED)

    val syncInfoFlow: Flow<SyncInfo> = events.consumeAsFlow()

    private val listener =
        object : ServiceListener {
            override fun serviceAdded(event: ServiceEvent) {
                event.dns.requestServiceInfo(SERVICE_TYPE, event.info.name)
            }

            override fun serviceRemoved(event: ServiceEvent) {
                val parts = event.info.name.split("@")
                if (parts.size == 3 && parts[0] == "crosspaste") {
                    resolved.remove(parts[1])
                }
            }

            override fun serviceResolved(event: ServiceEvent) {
                val textBytes = event.info.textBytes ?: return
                if (textBytes.isEmpty()) return
                runCatching {
                    val raw: Map<String, ByteArray> = mutableMapOf()
                    ByteWrangler.readProperties(raw, textBytes)
                    val syncInfo = TxtRecordUtils.decodeFromTxtRecordDict<SyncInfo>(raw)
                    resolved[syncInfo.appInfo.appInstanceId] = syncInfo
                    events.trySend(syncInfo)
                }.onFailure { e ->
                    logger.debug(e) { "Failed to decode resolved service: ${event.info.name}" }
                }
            }
        }

    fun start() {
        jmdnsInstances.forEach { jm ->
            jm.addServiceListener(SERVICE_TYPE, listener)
            // JmDNS.list() blocks; run per interface on a daemon thread so the active PTR
            // query fires alongside the passive listener instead of waiting for peer announces.
            Thread {
                runCatching { jm.list(SERVICE_TYPE, ACTIVE_SCAN_TIMEOUT_MS) }
                    .onFailure { e -> logger.debug(e) { "Active scan failed" } }
            }.apply {
                name = "DiscoveryDriver-active-scan"
                isDaemon = true
                start()
            }
        }
    }

    /**
     * Block until [timeoutMs] elapses, returning all distinct SyncInfo records resolved
     * during that window. Optionally filter to a specific appInstanceId — return as soon
     * as that one is found instead of waiting for the full timeout.
     */
    suspend fun browse(
        timeoutMs: Long,
        targetAppInstanceId: String? = null,
        pollIntervalMs: Long = 200,
    ): List<SyncInfo> {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (targetAppInstanceId != null && resolved.containsKey(targetAppInstanceId)) {
                break
            }
            delay(pollIntervalMs)
        }
        return resolved.values.toList()
    }

    fun snapshot(): Map<String, SyncInfo> = resolved.toMap()

    fun close() {
        runCatching {
            jmdnsInstances.forEach { jm ->
                jm.removeServiceListener(SERVICE_TYPE, listener)
                jm.close()
            }
            events.close()
        }.onFailure { e ->
            logger.debug(e) { "Error closing DiscoveryDriver" }
        }
    }

    private fun createJmdnsInstances(): List<JmDNS> {
        val addresses = NetworkUtils.enumerateLanInet4().map { it.first }
        if (addresses.isEmpty()) {
            logger.warn { "No usable LAN IPv4 interfaces found; falling back to default JmDNS." }
            return listOf(JmDNS.create())
        }
        val instances =
            addresses.mapNotNull { addr ->
                runCatching { JmDNS.create(addr) }
                    .onSuccess { logger.info { "JmDNS bound to ${addr.hostAddress}" } }
                    .onFailure { e -> logger.warn(e) { "Failed to bind JmDNS to ${addr.hostAddress}" } }
                    .getOrNull()
            }
        return instances.ifEmpty { listOf(JmDNS.create()) }
    }
}
