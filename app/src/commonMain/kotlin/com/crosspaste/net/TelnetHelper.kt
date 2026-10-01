package com.crosspaste.net

import com.crosspaste.db.sync.HostInfo
import com.crosspaste.utils.HEADER_APP_INSTANCE_ID
import com.crosspaste.utils.HostAndPort
import com.crosspaste.utils.buildUrl
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.statement.*
import io.ktor.http.isSuccess
import io.ktor.util.collections.*
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
    networkInterfaceService: NetworkInterfaceService,
    private val pasteClient: PasteClient,
    private val syncApi: SyncApi,
    syncInfoFactory: SyncInfoFactory,
) {

    companion object {
        const val FAST_TIMEOUT = 500L
        const val SLOW_TIMEOUT = 2000L
    }

    private val advertiser = SyncInfoAdvertiser(networkInterfaceService, syncInfoFactory)

    private val logger = KotlinLogging.logger {}

    // Last address set we successfully advertised to each peer (keyed by the probed host
    // address). We push our address once per peer and then stay quiet until it actually
    // changes — re-advertising on every probe would re-run the server-side addDevice (and
    // its trackSignificantAction) far more often than mDNS does. A real IP change recomputes
    // a different list here, so the next probe naturally re-advertises (#4518 review).
    //
    // Keyed by probed address (not peer identity, which we only learn from the response):
    // switchHost racing a peer's N candidate addresses therefore advertises once per
    // candidate on first contact (N <= MAX_RECENT_HOST_INFO, each correct for its own
    // subnet), then never again until our address changes. That bounded, one-time fan-out
    // is acceptable.
    private val lastAdvertised: MutableMap<String, List<HostInfo>> = ConcurrentMap()

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
                                        // INFO: reached this address but it is the wrong peer
                                        // (stale/reused IP, ghost). The only signal that tells a
                                        // failed switch apart from a genuinely unreachable peer.
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
            // Piggyback our current address onto the probe so the peer learns where to
            // reach us back without waiting for the next mDNS round (#4509 phase 3). We
            // advertise only the local interface(s) on the peer's subnet — the address it
            // can actually route to — and only when it differs from what we last delivered
            // to this peer, so a steady network tells each peer exactly once. Best-effort:
            // a failure here must never turn a reachable host into an "unreachable" result.
            val advertiseHostInfo = advertiser.subnetMatchedHostInfo(hostAddress)
            val advertiseHeader =
                if (advertiseHostInfo.isNotEmpty() && lastAdvertised[hostAddress] != advertiseHostInfo) {
                    advertiser.buildHeader(advertiseHostInfo)
                } else {
                    null
                }
            val httpResponse =
                pasteClient.get(
                    timeout = timeout,
                    headersBuilder = {
                        advertiseHeader?.let { append(SyncInfoHeaderCodec.HEADER, it) }
                    },
                ) {
                    buildUrl(hostAndPort)
                    buildUrl("sync", "telnet")
                }
            // A 200 is the steady-state norm and runs every poll while disconnected, so keep it at
            // debug; surface any non-200 at info — it means we reached the host but its server
            // answered abnormally, which "no reachable host" alone can't distinguish.
            if (httpResponse.status.isSuccess()) {
                logger.debug { "httpResponse.status = ${httpResponse.status.value} $hostAddress:$port" }
            } else {
                logger.info { "httpResponse.status = ${httpResponse.status.value} $hostAddress:$port" }
            }

            if (httpResponse.status.value == 200) {
                // Mark as delivered only on a 200 we actually sent the header on, so a
                // failed probe re-advertises next time instead of going silent.
                if (advertiseHeader != null) {
                    lastAdvertised[hostAddress] = advertiseHostInfo
                }
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
