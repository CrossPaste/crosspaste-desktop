package com.crosspaste.net

import com.crosspaste.db.sync.HostInfo
import com.crosspaste.utils.HEADER_APP_INSTANCE_ID
import com.crosspaste.utils.HostAndPort
import com.crosspaste.utils.buildUrl
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.statement.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds

/**
 * Outcome of a `/sync/telnet` probe: the peer's protocol [versionRelation] plus the
 * [peerAppInstanceId] it advertised in the response header (null when the peer is an
 * older build that does not send the header).
 */
data class TelnetResult(
    val versionRelation: VersionRelation,
    val peerAppInstanceId: String?,
) {
    /**
     * Whether this peer may be admitted as a connection candidate for [expected].
     *
     * The telnet header identity is unauthenticated and used only to gate the
     * "candidate" set — trust is still granted by the ECDH heartbeat. We admit a peer
     * whose identity is unknown (old build, no header) and leave it to the encrypted
     * heartbeat to vet; we reject only a positively *different* identity, which is a
     * ghost occupying a historical IP (#4499). A null [expected] disables filtering.
     */
    fun identityAccepted(expected: String?): Boolean =
        expected == null || peerAppInstanceId == null || peerAppInstanceId == expected
}

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
        expectedAppInstanceId: String? = null,
        timeout: Long = FAST_TIMEOUT,
    ): Pair<HostInfo, TelnetResult>? {
        if (hostInfoList.isEmpty()) return null

        return withTimeoutOrNull(timeout.milliseconds) {
            supervisorScope {
                val result = CompletableDeferred<Pair<HostInfo, TelnetResult>?>()
                val mutex = Mutex()

                val probeJobs =
                    hostInfoList.map { hostInfo ->
                        launch(CoroutineName("SwitchHost")) {
                            runCatching {
                                telnet(hostInfo.hostAddress, port, timeout)?.let { telnetResult ->
                                    // Identity filtering happens here, inside the race: a
                                    // reachable-but-mismatched ghost must not win and crowd
                                    // out the real peer that advertised the right identity.
                                    if (telnetResult.identityAccepted(expectedAppInstanceId)) {
                                        mutex.withLock {
                                            if (!result.isCompleted) {
                                                result.complete(Pair(hostInfo, telnetResult))
                                            }
                                        }
                                    } else {
                                        logger.info {
                                            "switchHost skip ${hostInfo.hostAddress}:$port " +
                                                "identity mismatch (${telnetResult.peerAppInstanceId} != $expectedAppInstanceId)"
                                        }
                                    }
                                }
                            }.onFailure { e ->
                                // Never swallow cancellation — let structured concurrency
                                // tear the probe down instead of marking it "completed".
                                if (e is CancellationException) throw e
                                logger.debug(e) { "switchHost telnet failed for ${hostInfo.hostAddress}:$port" }
                            }
                        }
                    }

                // When every probe finishes without a success, complete with null so
                // result.await() returns immediately instead of blocking until the
                // outer withTimeoutOrNull expires.
                launch {
                    probeJobs.joinAll()
                    if (!result.isCompleted) {
                        result.complete(null)
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
    ): TelnetResult? =
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
                result.toIntOrNull()?.let { version ->
                    // Identity rides back on the same response (header), so version +
                    // identity are obtained atomically with no extra round-trip. Older
                    // peers omit the header -> peerAppInstanceId is null.
                    TelnetResult(
                        versionRelation = syncApi.compareVersion(version),
                        peerAppInstanceId = httpResponse.headers[HEADER_APP_INSTANCE_ID],
                    )
                }
            } else {
                null
            }
        }.onFailure {
            // Don't convert a coroutine cancellation into a null "unreachable" result —
            // rethrow so cancellation propagates (matches SyncResolver.processEvent and the
            // #4503 fix). Other failures genuinely mean the host is unreachable.
            if (it is CancellationException) throw it
            logger.debug(it) { "telnet $hostAddress fail" }
        }.getOrNull()
}
