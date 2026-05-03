package com.crosspaste.e2e.scenario

import com.crosspaste.net.clientapi.SuccessResult
import com.crosspaste.utils.HostAndPort
import com.crosspaste.utils.buildUrl

class PullIconScenario(
    private val source: String,
) : Scenario {
    override val name: String = "pull-icon"

    override suspend fun run(ctx: ScenarioContext): ScenarioResult {
        if (source.isBlank()) {
            return ScenarioResult.Fail("--icon-source is required for pull-icon.")
        }
        val target = resolveOrDiscover(ctx) ?: return ScenarioResult.Fail("Could not resolve target.")

        val result =
            ctx.peer.pullClientApi.pullIcon(
                source = source,
                toUrl = { buildUrl(HostAndPort(target.host, target.port)) },
            )

        if (result !is SuccessResult) {
            return ScenarioResult.Fail("pullIcon failed: ${describeFailure(result)}")
        }
        return ScenarioResult.Pass("Icon source='$source' fetched from ${target.host}:${target.port}.")
    }
}
