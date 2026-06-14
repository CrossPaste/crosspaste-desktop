package com.crosspaste.e2e.scenario

import com.crosspaste.db.sync.HostInfo
import com.crosspaste.e2e.net.NetworkUtils
import com.crosspaste.e2e.peer.BonjourAdvertiser
import com.crosspaste.e2e.peer.buildPeerSyncInfo
import com.crosspaste.net.SyncInfoHeaderCodec
import com.crosspaste.utils.HEADER_APP_INSTANCE_ID
import com.crosspaste.utils.HostAndPort
import com.crosspaste.utils.buildUrl
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

/**
 * Validates the `/sync/telnet` identity + address-push protocol (#4499 / #4500, #4509 phase 3).
 *
 * Two things shipped on this endpoint that earlier phases of the harness never exercised:
 *  1. **Identity in the response** — the probe response carries the target's appInstanceId in
 *     the [HEADER_APP_INSTANCE_ID] header so discovery can vet a candidate atomically with the
 *     version check. Fully observable: we assert the header is present and (when the target id
 *     is known) matches it. A target predating #4500 omits the header, which this flags.
 *  2. **Address push in the request** — the caller may attach its current [SyncInfo] in the
 *     [SyncInfoHeaderCodec.HEADER] header so a known peer's new address propagates without
 *     waiting for the next mDNS round. The server merges it into its nearby map, but only for
 *     an already-paired peer, and returns the same 200/version regardless. That merge is not
 *     observable from a black-box client, so we exercise the real (web-compatible) codec
 *     against the live endpoint after pairing and assert it still answers 200 with its
 *     identity — a regression guard against the endpoint rejecting a valid header.
 */
class TelnetScenario : Scenario {
    override val name: String = "telnet"

    override suspend fun run(ctx: ScenarioContext): ScenarioResult {
        val target = resolveOrDiscover(ctx) ?: return ScenarioResult.Fail("Could not resolve target.")
        println("[telnet] Target: ${target.appInstanceId} at ${target.host}:${target.port}")

        // Probe 1: plain telnet — verify the version body and the identity response header.
        val plain = observe(probe(ctx, target, syncInfoHeader = null), target)
        val firstSeen =
            plain.observation
                ?: return ScenarioResult.Fail(plain.failure ?: "telnet probe failed")

        // Probe 2: address push. The server only merges the pushed SyncInfo for a peer it has
        // already paired with, so pair first to drive the real (gated) code path end-to-end.
        ensureTrust(ctx, target)?.let { return it }
        val header =
            peerSyncInfoHeader(ctx)
                ?: return ScenarioResult.Fail(
                    "No usable LAN interface to advertise; cannot build the address-push header.",
                )
        val pushed = observe(probe(ctx, target, syncInfoHeader = header), target)
        if (pushed.observation == null) {
            return ScenarioResult.Fail("Address-push probe failed: ${pushed.failure}")
        }

        return ScenarioResult.Pass(
            "telnet identity=${firstSeen.advertisedId} version=${firstSeen.version}; " +
                "address-push header accepted (server-side merge not observable).",
        )
    }

    private suspend fun probe(
        ctx: ScenarioContext,
        target: TargetSpec,
        syncInfoHeader: String?,
    ): HttpResponse =
        ctx.peer.pasteClient.get(
            headersBuilder = {
                syncInfoHeader?.let { append(SyncInfoHeaderCodec.HEADER, it) }
            },
        ) {
            buildUrl(HostAndPort(target.host, target.port))
            buildUrl("sync", "telnet")
        }

    private suspend fun observe(
        response: HttpResponse,
        target: TargetSpec,
    ): TelnetProbe {
        if (!response.status.isSuccess()) {
            return TelnetProbe.fail("telnet returned HTTP ${response.status.value}")
        }
        val body = response.bodyAsText()
        val version =
            body.toIntOrNull()
                ?: return TelnetProbe.fail("telnet body is not a version int: '$body'")
        val advertisedId =
            response.headers[HEADER_APP_INSTANCE_ID]
                ?: return TelnetProbe.fail(
                    "telnet response missing '$HEADER_APP_INSTANCE_ID' header (target predates #4500?)",
                )
        target.appInstanceId?.let { expected ->
            if (advertisedId != expected) {
                return TelnetProbe.fail("telnet identity '$advertisedId' != expected '$expected'")
            }
        }
        return TelnetProbe.ok(TelnetObservation(version, advertisedId))
    }

    private fun peerSyncInfoHeader(ctx: ScenarioContext): String? {
        val hostInfoList =
            NetworkUtils.enumerateLanInet4().map { (addr, prefix) ->
                HostInfo(networkPrefixLength = prefix, hostAddress = addr.hostAddress)
            }
        if (hostInfoList.isEmpty()) return null
        val syncInfo =
            buildPeerSyncInfo(ctx.peer.appInfo, hostInfoList, BonjourAdvertiser.ADVERTISED_PORT)
        return SyncInfoHeaderCodec.encode(syncInfo)
    }
}

private data class TelnetObservation(
    val version: Int,
    val advertisedId: String,
)

private class TelnetProbe private constructor(
    val observation: TelnetObservation?,
    val failure: String?,
) {
    companion object {
        fun ok(observation: TelnetObservation): TelnetProbe = TelnetProbe(observation, null)

        fun fail(reason: String): TelnetProbe = TelnetProbe(null, reason)
    }
}
