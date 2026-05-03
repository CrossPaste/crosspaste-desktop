package com.crosspaste.e2e.scenario

import com.crosspaste.e2e.protocol.DiscoveryDriver
import com.crosspaste.net.clientapi.SuccessResult
import com.crosspaste.utils.HostAndPort
import com.crosspaste.utils.buildUrl

class PairScenario : Scenario {
    override val name: String = "pair"

    override suspend fun run(ctx: ScenarioContext): ScenarioResult {
        val target = resolveOrDiscover(ctx) ?: return ScenarioResult.Fail("Could not resolve target.")
        val targetAppId = target.appInstanceId ?: return ScenarioResult.Fail("Target has no appInstanceId.")
        println("[pair] Target: $targetAppId at ${target.host}:${target.port}")
        val token = ctx.tokenProvider()
        return performTrust(ctx, target, targetAppId, token)
    }
}

internal suspend fun performTrust(
    ctx: ScenarioContext,
    target: TargetSpec,
    targetAppId: String,
    token: Int,
): ScenarioResult {
    val result =
        ctx.peer.syncClientApi.trust(
            targetAppInstanceId = targetAppId,
            host = target.host,
            token = token,
            toUrl = { buildUrl(HostAndPort(target.host, target.port)) },
        )

    if (result !is SuccessResult) {
        return ScenarioResult.Fail("Trust call failed: $result")
    }
    if (!ctx.peer.secureIO.existCryptPublicKey(targetAppId)) {
        return ScenarioResult.Fail("Trust returned success but target's public key was not stored.")
    }
    return ScenarioResult.Pass("Paired with $targetAppId; target public key persisted.")
}

/**
 * Pair with [target] if not already trusted. Returns null on success, or a Fail to
 * propagate. Prompts for a token via [ScenarioContext.tokenProvider] when needed.
 */
internal suspend fun ensureTrust(
    ctx: ScenarioContext,
    target: TargetSpec,
): ScenarioResult? {
    val id = target.appInstanceId ?: return ScenarioResult.Fail("Target has no appInstanceId.")
    if (ctx.peer.secureIO.existCryptPublicKey(id)) return null
    println("[ensureTrust] Not paired with $id yet — pairing first.")
    val token = ctx.tokenProvider()
    val result = performTrust(ctx, target, id, token)
    return if (result is ScenarioResult.Fail) result else null
}

internal suspend fun resolveOrDiscover(ctx: ScenarioContext): TargetSpec? {
    if (ctx.target != null) return ctx.target
    val driver = DiscoveryDriver()
    return try {
        driver.start()
        val found = driver.browse(ctx.discoveryTimeoutMs, ctx.targetAppInstanceId)
        resolveTarget(ctx, found)
    } finally {
        driver.close()
    }
}
