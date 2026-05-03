package com.crosspaste.e2e.protocol

import com.crosspaste.dto.sync.SyncInfo
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
    }

    private val logger = KotlinLogging.logger {}

    private val jmdns: JmDNS = JmDNS.create()

    private val resolved: ConcurrentMap<String, SyncInfo> = ConcurrentMap()

    private val events: Channel<SyncInfo> = Channel(Channel.UNLIMITED)

    val syncInfoFlow: Flow<SyncInfo> = events.consumeAsFlow()

    private val listener =
        object : ServiceListener {
            override fun serviceAdded(event: ServiceEvent) {
                jmdns.requestServiceInfo(SERVICE_TYPE, event.info.name)
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
        jmdns.addServiceListener(SERVICE_TYPE, listener)
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
            jmdns.removeServiceListener(SERVICE_TYPE, listener)
            jmdns.close()
            events.close()
        }.onFailure { e ->
            logger.debug(e) { "Error closing DiscoveryDriver" }
        }
    }
}
