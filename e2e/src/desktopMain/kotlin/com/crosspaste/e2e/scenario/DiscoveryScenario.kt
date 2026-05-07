package com.crosspaste.e2e.scenario

import com.crosspaste.e2e.protocol.DiscoveryDriver

class DiscoveryScenario : Scenario {
    override val name: String = "discovery"

    override suspend fun run(ctx: ScenarioContext): ScenarioResult {
        val driver = DiscoveryDriver()
        try {
            driver.start()
            val found = driver.browse(ctx.discoveryTimeoutMs, ctx.targetAppInstanceId)
            if (found.isEmpty()) {
                return ScenarioResult.Fail(
                    "No CrossPaste services discovered within ${ctx.discoveryTimeoutMs}ms.",
                )
            }
            val expected = ctx.targetAppInstanceId
            if (expected != null && found.none { it.appInfo.appInstanceId == expected }) {
                val seen = found.joinToString { it.appInfo.appInstanceId }
                return ScenarioResult.Fail(
                    "Target appInstanceId '$expected' not seen. Discovered: [$seen]",
                )
            }
            resolveTarget(ctx, found)?.let { ctx.targetCache.resolved.compareAndSet(null, it) }
            val summary =
                found.joinToString { si ->
                    "${si.appInfo.appInstanceId}@${si.endpointInfo.hostInfoList.firstOrNull()?.hostAddress}:${si.endpointInfo.port}"
                }
            return ScenarioResult.Pass("Discovered ${found.size} peer(s): $summary")
        } finally {
            driver.close()
        }
    }
}
