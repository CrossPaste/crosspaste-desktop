package com.crosspaste.e2e.scenario

import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.e2e.peer.HeadlessPeer
import com.crosspaste.net.clientapi.ClientApiResult
import com.crosspaste.net.clientapi.ConnectionRefused
import com.crosspaste.net.clientapi.DecryptFail
import com.crosspaste.net.clientapi.EncryptFail
import com.crosspaste.net.clientapi.FailureResult
import com.crosspaste.net.clientapi.RequestTimeout
import com.crosspaste.net.clientapi.UnknownError

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

/**
 * Holds a discovered target across scenarios so the second-onwards run in a multi-scenario
 * batch skips a redundant 1.5–10s mDNS round.
 */
class TargetCache {
    @Volatile
    var resolved: TargetSpec? = null
}

data class ScenarioContext(
    val peer: HeadlessPeer,
    val target: TargetSpec?,
    val targetAppInstanceId: String?,
    val discoveryTimeoutMs: Long,
    val tokenProvider: suspend () -> Int,
    val targetCache: TargetCache = TargetCache(),
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

fun describeFailure(result: ClientApiResult): String =
    when (result) {
        is FailureResult ->
            "FailureResult(code=${result.exception.getErrorCode().code}, " +
                "name=${result.exception.getErrorCode().name}, " +
                "message='${result.exception.message ?: ""}')"
        is ConnectionRefused -> "ConnectionRefused"
        is EncryptFail -> "EncryptFail"
        is DecryptFail -> "DecryptFail"
        is RequestTimeout -> "RequestTimeout"
        is UnknownError -> "UnknownError"
        else -> result::class.simpleName ?: result.toString()
    }
