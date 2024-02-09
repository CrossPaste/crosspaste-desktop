package com.clipevery.utils

import com.clipevery.dao.sync.HostInfo
import com.clipevery.net.ClipClient
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.selects.select

class TelnetUtils(private val clipClient: ClipClient) {

    private val logger = KotlinLogging.logger {}

    suspend fun switchHost(hostInfoList: List<HostInfo>, port: Int, timeout: Long = 500L): HostInfo? {
        if (hostInfoList.isEmpty()) {
            return null
        }
        val deferredArray = hostInfoList.map { hostInfo ->
            CoroutineScope(ioDispatcher).async {
                if (telnet(hostInfo, port, timeout)) hostInfo else null
            }
        }

        var result: HostInfo? = null
        select {
            deferredArray.forEach { deferred ->
                deferred.onAwait { hostInfo ->
                    if (hostInfo != null) {
                        result = hostInfo
                        deferredArray.forEach { it.cancel() }
                    }
                }
            }
        }
        return result
    }

    private suspend fun telnet(hostInfo: HostInfo, port: Int, timeout: Long): Boolean {
        return try {
            val httpResponse = clipClient.get(timeout = timeout) { urlBuilder ->
                buildUrl(urlBuilder, hostInfo, port, "sync", "telnet")
            }
            logger.info { "httpResponse.status = ${httpResponse.status.value} ${hostInfo.hostAddress}:$port" }

            httpResponse.status.value == 200
        } catch (e: Exception) {
            logger.debug(e) { "telnet $hostInfo fail" }
            false
        }
    }
}