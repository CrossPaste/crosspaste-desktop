package com.crosspaste.net

import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.utils.TxtRecordUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceListener
import javax.jmdns.impl.util.ByteWrangler

internal class DesktopServiceListener(
    private val isActive: () -> Boolean,
    private val onResolved: (serviceName: String, syncInfo: SyncInfo) -> Unit,
    private val onRemoved: (serviceName: String, appInstanceId: String) -> Unit,
) : ServiceListener {

    private val logger = KotlinLogging.logger {}

    override fun serviceAdded(event: ServiceEvent) {
        if (isActive()) {
            logger.debug { "Service added: " + event.info }
        }
    }

    override fun serviceRemoved(event: ServiceEvent) {
        if (!isActive()) return

        runCatching {
            val serviceName = event.info.name
            val appInstanceId = appInstanceIdFromServiceName(serviceName) ?: return
            logger.debug { "Processing service removed: $serviceName" }
            onRemoved(serviceName, appInstanceId)
        }.onFailure { e ->
            logger.debug(e) { "Failed to process service removed event: ${event.info}" }
        }
    }

    override fun serviceResolved(event: ServiceEvent) {
        if (!isActive()) return

        val textBytes = event.info.textBytes
        if (textBytes == null || textBytes.isEmpty()) {
            logger.debug { "Service resolved with empty textBytes: ${event.info.name}" }
            return
        }
        runCatching {
            val map: Map<String, ByteArray> = mutableMapOf()
            ByteWrangler.readProperties(map, textBytes)
            val syncInfo = TxtRecordUtils.decodeFromTxtRecordDict<SyncInfo>(map)
            if (isActive()) {
                onResolved(event.info.name, syncInfo)
            }
        }.onFailure { e ->
            logger.debug(e) { "Failed to decode service resolved event: ${event.info}" }
        }
    }
}

internal fun appInstanceIdFromServiceName(serviceName: String): String? {
    val parts = serviceName.split("@")
    return parts
        .takeIf {
            it.size == 3 &&
                it[0] == "crosspaste" &&
                it[1].isNotEmpty() &&
                it[2].isNotEmpty()
        }?.get(1)
}
