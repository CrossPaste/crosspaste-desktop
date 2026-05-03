package com.crosspaste.e2e.scenario

import com.crosspaste.net.clientapi.SuccessResult
import com.crosspaste.paste.PasteCollection
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteType
import com.crosspaste.paste.item.CreatePasteItemHelper
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.utils.HostAndPort
import com.crosspaste.utils.buildUrl

internal suspend fun pushPasteData(
    ctx: ScenarioContext,
    pasteType: PasteType,
    item: PasteItem,
    summary: (String) -> String,
): ScenarioResult {
    val target = resolveOrDiscover(ctx) ?: return ScenarioResult.Fail("Could not resolve target.")
    val targetAppId = target.appInstanceId ?: return ScenarioResult.Fail("Target has no appInstanceId.")

    ensureTrust(ctx, target)?.let { return it }

    val pasteData =
        PasteData(
            appInstanceId = ctx.peer.appInfo.appInstanceId,
            pasteAppearItem = item,
            pasteCollection = PasteCollection(emptyList()),
            pasteType = pasteType.type,
            size = item.size,
            hash = item.hash,
        )

    val result =
        ctx.peer.pasteClientApi.sendPaste(
            pasteData = pasteData,
            targetAppInstanceId = targetAppId,
            toUrl = { buildUrl(HostAndPort(target.host, target.port)) },
        )

    if (result !is SuccessResult) {
        return ScenarioResult.Fail("sendPaste failed: ${describeFailure(result)}")
    }
    return ScenarioResult.Pass(summary(targetAppId))
}

class PushTextScenario(
    private val text: String,
) : Scenario {
    override val name: String = "push-text"

    override suspend fun run(ctx: ScenarioContext): ScenarioResult {
        val item = CreatePasteItemHelper.createTextPasteItem(text = text)
        return pushPasteData(ctx, PasteType.TEXT_TYPE, item) { id ->
            "Pushed text (${item.size} bytes) to $id."
        }
    }
}

class PushUrlScenario(
    private val url: String,
) : Scenario {
    override val name: String = "push-url"

    override suspend fun run(ctx: ScenarioContext): ScenarioResult {
        val item = CreatePasteItemHelper.createUrlPasteItem(url = url)
        return pushPasteData(ctx, PasteType.URL_TYPE, item) { id ->
            "Pushed url ($url) to $id."
        }
    }
}

class PushHtmlScenario(
    private val html: String,
) : Scenario {
    override val name: String = "push-html"

    override suspend fun run(ctx: ScenarioContext): ScenarioResult {
        val item = CreatePasteItemHelper.createHtmlPasteItem(html = html)
        return pushPasteData(ctx, PasteType.HTML_TYPE, item) { id ->
            "Pushed html (${item.size} bytes) to $id."
        }
    }
}

class PushRtfScenario(
    private val rtf: String,
) : Scenario {
    override val name: String = "push-rtf"

    override suspend fun run(ctx: ScenarioContext): ScenarioResult {
        val item = CreatePasteItemHelper.createRtfPasteItem(rtf = rtf)
        return pushPasteData(ctx, PasteType.RTF_TYPE, item) { id ->
            "Pushed rtf (${item.size} bytes) to $id."
        }
    }
}

class PushColorScenario(
    private val color: Int,
) : Scenario {
    override val name: String = "push-color"

    override suspend fun run(ctx: ScenarioContext): ScenarioResult {
        val item = CreatePasteItemHelper.createColorPasteItem(color = color)
        return pushPasteData(ctx, PasteType.COLOR_TYPE, item) { id ->
            "Pushed color (0x${color.toUInt().toString(16).padStart(8, '0')}) to $id."
        }
    }
}
