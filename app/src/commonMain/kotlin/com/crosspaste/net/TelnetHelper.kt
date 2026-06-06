package com.crosspaste.net

import com.crosspaste.db.sync.HostInfo
import com.crosspaste.utils.HEADER_APP_INSTANCE_ID
import com.crosspaste.utils.HostAndPort
import com.crosspaste.utils.buildUrl
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.statement.*
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
    private val networkInterfaceService: NetworkInterfaceService,
    private val pasteClient: PasteClient,
    private val syncApi: SyncApi,
    private val syncInfoFactory: SyncInfoFactory,
) {

    companion object {
        const val FAST_TIMEOUT = 500L
        const val SLOW_TIMEOUT = 2000L

        // Building the advertise hint must never stall the probe. createSyncInfo waits
        // for our own server port (portFlow.first { it > 0 }), which is instant once the
        // server is up but blocks during the cold-start window before it is. Cap it so a
        // not-yet-ready local server just means "no hint this round", not a delayed probe.
        private val ADVERTISE_BUILD_TIMEOUT = 100.milliseconds
    }

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
            val advertiseHostInfo = currentAdvertiseHostInfo(hostAddress)
            val advertiseHeader =
                if (advertiseHostInfo.isNotEmpty() && lastAdvertised[hostAddress] != advertiseHostInfo) {
                    buildAdvertiseHeader(advertiseHostInfo)
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
            logger.info { "httpResponse.status = ${httpResponse.status.value} $hostAddress:$port" }

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

    /**
     * The local address(es) the peer at [peerAddress] should use to reach us: our
     * interface(s) on the peer's subnet (#4509 phase 3). Reuses [HostInfo.filter] — the same
     * subnet match the trust flow uses to pick a reachable address. Empty when no local
     * interface shares the peer's subnet (cross-subnet / offline). Cheap and non-blocking,
     * so it can drive the "did our address change?" check on every probe.
     */
    private fun currentAdvertiseHostInfo(peerAddress: String): List<HostInfo> =
        networkInterfaceService
            .getCurrentUseNetworkInterfaces()
            .map { it.toHostInfo() }
            .filter { it.filter(peerAddress) }

    /**
     * Encode [hostInfoList] into the value for [SyncInfoHeaderCodec.HEADER]. Best-effort:
     * swallows non-cancellation failures so building the hint can never fail the probe, and
     * is time-bounded because [SyncInfoFactory.createSyncInfo] waits on our own server port
     * ([ADVERTISE_BUILD_TIMEOUT]) — a not-yet-ready server just means "no hint this round".
     */
    private suspend fun buildAdvertiseHeader(hostInfoList: List<HostInfo>): String? =
        runCatching {
            // withTimeoutOrNull returns null on its own timeout (no throw) but still
            // propagates a real parent cancellation — exactly the best-effort semantics
            // we want for the hint.
            withTimeoutOrNull(ADVERTISE_BUILD_TIMEOUT) {
                SyncInfoHeaderCodec.encode(syncInfoFactory.createSyncInfo(hostInfoList))
            }
        }.onFailure {
            if (it is CancellationException) throw it
            logger.debug(it) { "failed to build advertise header for $hostInfoList" }
        }.getOrNull()
}
