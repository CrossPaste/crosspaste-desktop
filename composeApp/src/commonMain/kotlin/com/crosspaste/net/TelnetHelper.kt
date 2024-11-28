package com.crosspaste.net

import com.crosspaste.realm.sync.HostInfo
import com.crosspaste.utils.buildUrl
import com.crosspaste.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.statement.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

class TelnetHelper(
    private val pasteClient: PasteClient,
    private val syncApi: SyncApi,
) {

    private val logger = KotlinLogging.logger {}

    suspend fun switchHost(
        hostInfoList: List<HostInfo>,
        port: Int,
        timeout: Long = 500L,
    ): Pair<HostInfo, VersionRelation>? {
        if (hostInfoList.isEmpty()) return null

        val result = CompletableDeferred<Pair<HostInfo, VersionRelation>?>()
        val mutex = Mutex()
        val scope = CoroutineScope(ioDispatcher)

        hostInfoList.forEach { hostInfo ->
            scope.launch(CoroutineName("SwitchHost")) {
                try {
                    telnet(hostInfo, port, timeout)?.let {
                        mutex.withLock {
                            if (!result.isCompleted) {
                                result.complete(Pair(hostInfo, it))
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }

        return try {
            withTimeout(timeout) { result.await() }
        } catch (_: TimeoutCancellationException) {
            null
        } finally {
            scope.cancel()
        }
    }

    private suspend fun telnet(
        hostInfo: HostInfo,
        port: Int,
        timeout: Long,
    ): VersionRelation? {
        return try {
            val httpResponse =
                pasteClient.get(timeout = timeout) {
                    buildUrl(hostInfo.hostAddress, port)
                    buildUrl("sync", "telnet")
                }
            logger.info { "httpResponse.status = ${httpResponse.status.value} $hostInfo:$port" }

            if (httpResponse.status.value == 200) {
                val result = httpResponse.bodyAsText()
                syncApi.compareVersion(result.toIntOrNull() ?: -1)
            } else {
                null
            }
        } catch (e: Exception) {
            logger.debug(e) { "telnet $hostInfo fail" }
            null
        }
    }
}
