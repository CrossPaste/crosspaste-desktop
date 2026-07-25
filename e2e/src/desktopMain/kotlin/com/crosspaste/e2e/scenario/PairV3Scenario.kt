package com.crosspaste.e2e.scenario

import com.crosspaste.db.sync.HostInfo
import com.crosspaste.e2e.net.NetworkUtils
import com.crosspaste.e2e.peer.BonjourAdvertiser
import com.crosspaste.e2e.peer.buildPeerSyncInfo
import com.crosspaste.net.SyncApi
import com.crosspaste.net.clientapi.SuccessResult
import com.crosspaste.pairing.v3.PairingV3PinResult
import com.crosspaste.pairing.v3.PairingV3StartResult
import com.crosspaste.utils.HostAndPort
import com.crosspaste.utils.buildUrl
import io.ktor.http.URLBuilder

/**
 * Drives the production pairing-v3 protocol service and real HTTP transport from
 * a headless initiator. The harness uses the BC backend for interoperability
 * validation only; the desktop application's production DI remains fail closed.
 */
class PairV3Scenario : Scenario {
    override val name: String = "pair-v3"

    override suspend fun run(ctx: ScenarioContext): ScenarioResult {
        val target = resolveOrDiscover(ctx) ?: return ScenarioResult.Fail("Could not resolve target.")
        val targetAppId = target.appInstanceId ?: return ScenarioResult.Fail("Target has no appInstanceId.")
        if (target.pairingVersion != null && !SyncApi.supportsPairingV3(target.pairingVersion)) {
            return ScenarioResult.Fail(
                "Target advertises pairing version ${target.pairingVersion}; " +
                    "pair-v3 never falls back to legacy pairing.",
            )
        }
        val service =
            ctx.peer.pairingProtocolV3Service
                ?: return ScenarioResult.Fail("The E2E peer is not configured for pairing v3.")
        val toTarget: URLBuilder.() -> Unit = {
            buildUrl(HostAndPort(target.host, target.port))
        }

        println("[pair-v3] Target: $targetAppId at ${target.host}:${target.port}")
        val started =
            when (
                val result =
                    service.startPairing(
                        targetAppInstanceId = targetAppId,
                        targetDisplayName = target.displayName ?: targetAppId,
                        toUrl = toTarget,
                    )
            ) {
                is PairingV3StartResult.Started -> result
                is PairingV3StartResult.Refused ->
                    return ScenarioResult.Fail("Pairing v3 intent refused: ${result.code}")
                is PairingV3StartResult.NetworkError ->
                    return ScenarioResult.Fail(
                        "Pairing v3 intent failed: ${describeFailure(result.failure)}",
                    )
            }

        println(
            "[pair-v3] Session ready; target signing-key fingerprint " +
                started.peerKeyFingerprintDisplay,
        )
        val pin =
            try {
                ctx.pinProvider()
            } catch (e: Exception) {
                service.cancelSession(started.sessionId, toTarget)
                throw e
            }
        val paired =
            try {
                service.submitPin(started.sessionId, pin, toTarget)
            } finally {
                pin.fill('\u0000')
            }
        when (paired) {
            is PairingV3PinResult.Paired -> Unit
            is PairingV3PinResult.Refused -> {
                service.cancelSession(started.sessionId, toTarget)
                return ScenarioResult.Fail("Pairing v3 proof/commit refused: ${paired.code}")
            }
            is PairingV3PinResult.NetworkError -> {
                service.cancelSession(started.sessionId, toTarget)
                return ScenarioResult.Fail(
                    "Pairing v3 proof/commit failed " +
                        "(commitPending=${paired.commitPending}): ${describeFailure(paired.failure)}",
                )
            }
        }

        if (!ctx.peer.secureIO.existCryptPublicKey(targetAppId)) {
            return ScenarioResult.Fail(
                "Pairing v3 completed but the target crypt public key was not persisted.",
            )
        }
        val heartbeat =
            ctx.peer.syncClientApi.heartbeat(
                syncInfo = heartbeatSyncInfo(ctx),
                targetAppInstanceId = targetAppId,
                toUrl = toTarget,
            )
        if (heartbeat !is SuccessResult) {
            return ScenarioResult.Fail(
                "Encrypted heartbeat after pairing v3 failed: ${describeFailure(heartbeat)}",
            )
        }
        return ScenarioResult.Pass(
            "Paired with $targetAppId via v3; peer key persisted and encrypted heartbeat succeeded.",
        )
    }

    private fun heartbeatSyncInfo(ctx: ScenarioContext) =
        buildPeerSyncInfo(
            appInfo = ctx.peer.appInfo,
            hostInfoList =
                NetworkUtils.enumerateLanInet4().map { (address, prefixLength) ->
                    HostInfo(
                        networkPrefixLength = prefixLength,
                        hostAddress = address.hostAddress,
                    )
                },
            port = BonjourAdvertiser.ADVERTISED_PORT,
        )
}
