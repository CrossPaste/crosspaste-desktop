package com.crosspaste.net

import com.crosspaste.db.sync.HostInfo
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Builds the `crosspaste-sync-info` routing hint we piggyback onto requests so a peer
 * learns where to reach us back without waiting for an mDNS round. Shared by the
 * `/sync/telnet` probe (#4509 phase 3) and the pairing v3 commit, so a peer that never
 * receives our multicast (one-way mDNS: VM NAT, AP isolation) still ends the pairing
 * with a usable address for us.
 */
class SyncInfoAdvertiser(
    private val networkInterfaceService: NetworkInterfaceService,
    private val syncInfoFactory: SyncInfoFactory,
) {

    companion object {
        // Building the hint waits on our own server port (createSyncInfo blocks on
        // portFlow.first { it > 0 }), which is instant once the server is up but stalls
        // during cold start. The probe caps it tightly so a not-yet-ready server just
        // means "no hint this round".
        val PROBE_BUILD_TIMEOUT: Duration = 100.milliseconds
    }

    private val logger = KotlinLogging.logger {}

    /** Every address we currently listen on. */
    fun allHostInfo(): List<HostInfo> =
        networkInterfaceService
            .getCurrentUseNetworkInterfaces()
            .map { it.toHostInfo() }

    /**
     * The local address(es) the peer at [peerAddress] should use to reach us: our
     * interface(s) on the peer's subnet. Reuses [HostInfo.filter] — the same subnet match
     * the trust flow uses to pick a reachable address. Empty when no local interface shares
     * the peer's subnet (cross-subnet / offline). Cheap and non-blocking.
     */
    fun subnetMatchedHostInfo(peerAddress: String): List<HostInfo> = allHostInfo().filter { it.filter(peerAddress) }

    /**
     * Encode [hostInfoList] into the value for [SyncInfoHeaderCodec.HEADER]. Best-effort:
     * swallows non-cancellation failures and is bounded by [timeout], so building the hint
     * can never fail or stall the request it rides on.
     */
    suspend fun buildHeader(
        hostInfoList: List<HostInfo>,
        timeout: Duration = PROBE_BUILD_TIMEOUT,
    ): String? =
        runCatching {
            // withTimeoutOrNull returns null on its own timeout (no throw) but still
            // propagates a real parent cancellation — exactly the best-effort semantics
            // we want for the hint.
            withTimeoutOrNull(timeout) {
                SyncInfoHeaderCodec.encode(syncInfoFactory.createSyncInfo(hostInfoList))
            }
        }.onFailure {
            if (it is CancellationException) throw it
            logger.debug(it) { "failed to build advertise header for $hostInfoList" }
        }.getOrNull()
}
