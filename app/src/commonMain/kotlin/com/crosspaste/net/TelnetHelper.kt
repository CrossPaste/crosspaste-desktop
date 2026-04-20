package com.crosspaste.net

import com.crosspaste.db.sync.HostInfo
import com.crosspaste.utils.HostAndPort
import com.crosspaste.utils.buildUrl
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.statement.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

class TelnetHelper(
    private val pasteClient: PasteClient,
    private val syncApi: SyncApi,
) {

    companion object {
        const val FAST_TIMEOUT = 500L
        const val SLOW_TIMEOUT = 2000L
    }

    private val logger = KotlinLogging.logger {}

    suspend fun switchHost(
        hostInfoList: List<HostInfo>,
        port: Int,
        timeout: Long = FAST_TIMEOUT,
    ): Pair<HostInfo, VersionRelation>? {
        if (hostInfoList.isEmpty()) return null

        return withTimeoutOrNull(timeout.milliseconds) {
            supervisorScope {
                val result = CompletableDeferred<Pair<HostInfo, VersionRelation>?>()
                val mutex = Mutex()

                hostInfoList.forEach { hostInfo ->
                    launch(CoroutineName("SwitchHost")) {
                        runCatching {
                            telnet(hostInfo.hostAddress, port, timeout)?.let {
                                mutex.withLock {
                                    if (!result.isCompleted) {
                                        result.complete(Pair(hostInfo, it))
                                    }
                                }
                            }
                        }.onFailure { e ->
                            logger.debug(e) { "switchHost telnet failed for ${hostInfo.hostAddress}:$port" }
                        }
                    }
                }

                result.await().also { coroutineContext.cancelChildren() }
            }
        }
    }

    suspend fun telnet(
        hostAddress: String,
        port: Int,
        timeout: Long = FAST_TIMEOUT,
    ): VersionRelation? =
        runCatching {
            val hostAndPort = HostAndPort(hostAddress, port)
            val httpResponse =
                pasteClient.get(timeout = timeout) {
                    buildUrl(hostAndPort)
                    buildUrl("sync", "telnet")
                }
            logger.info { "httpResponse.status = ${httpResponse.status.value} $hostAddress:$port" }

            if (httpResponse.status.value == 200) {
                val result = httpResponse.bodyAsText()
                result.toIntOrNull()?.let {
                    syncApi.compareVersion(it)
                }
            } else {
                null
            }
        }.onFailure {
            logger.debug(it) { "telnet $hostAddress fail" }
        }.getOrNull()
}
