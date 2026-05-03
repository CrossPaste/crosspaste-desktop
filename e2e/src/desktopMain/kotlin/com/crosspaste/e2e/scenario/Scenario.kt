package com.crosspaste.e2e.scenario

import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.e2e.peer.HeadlessPeer

sealed class ScenarioResult {
    data class Pass(
        val message: String = "",
    ) : ScenarioResult()

    data class Fail(
        val reason: String,
        val cause: Throwable? = null,
    ) : ScenarioResult()
}

interface Scenario {
    val name: String

    suspend fun run(ctx: ScenarioContext): ScenarioResult
}

data class TargetSpec(
    val host: String,
    val port: Int,
    val appInstanceId: String?,
)

data class ScenarioContext(
    val peer: HeadlessPeer,
    val target: TargetSpec?,
    val targetAppInstanceId: String?,
    val discoveryTimeoutMs: Long,
    val tokenProvider: suspend () -> Int,
)

/**
 * Resolve a target connection from either an explicit [ScenarioContext.target] or by
 * walking [discovered] for a matching appInstanceId. Returns null if no match.
 */
fun resolveTarget(
    ctx: ScenarioContext,
    discovered: List<SyncInfo>,
): TargetSpec? {
    ctx.target?.let { return it }
    val match =
        when (val id = ctx.targetAppInstanceId) {
            null -> discovered.firstOrNull()
            else -> discovered.firstOrNull { it.appInfo.appInstanceId == id }
        } ?: return null
    val host =
        match.endpointInfo.hostInfoList
            .firstOrNull()
            ?.hostAddress ?: return null
    return TargetSpec(
        host = host,
        port = match.endpointInfo.port,
        appInstanceId = match.appInfo.appInstanceId,
    )
}
