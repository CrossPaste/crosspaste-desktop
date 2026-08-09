package com.crosspaste.e2e.cli

import com.crosspaste.e2e.peer.HeadlessPeer
import com.crosspaste.e2e.scenario.DiscoveryScenario
import com.crosspaste.e2e.scenario.PairScenario
import com.crosspaste.e2e.scenario.PairV3Scenario
import com.crosspaste.e2e.scenario.PullIconScenario
import com.crosspaste.e2e.scenario.PushColorScenario
import com.crosspaste.e2e.scenario.PushHtmlScenario
import com.crosspaste.e2e.scenario.PushRtfScenario
import com.crosspaste.e2e.scenario.PushTextScenario
import com.crosspaste.e2e.scenario.PushUrlScenario
import com.crosspaste.e2e.scenario.Scenario
import com.crosspaste.e2e.scenario.ScenarioContext
import com.crosspaste.e2e.scenario.ScenarioResult
import com.crosspaste.e2e.scenario.TargetSpec
import com.crosspaste.e2e.scenario.TelnetScenario
import com.crosspaste.e2e.scenario.WrongTokenScenario
import com.crosspaste.net.SyncApi
import com.crosspaste.pairing.v3.PairingV3
import com.crosspaste.utils.getDateUtils
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.long
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import kotlin.coroutines.cancellation.CancellationException

class E2eCommand : CliktCommand(name = "crosspaste-e2e") {
    private val scenario by option(
        "--scenario",
        "-s",
        help =
            "Scenario to run: discovery, pair, wrong-token, telnet, push-text, push-url, " +
                "push-html, push-rtf, push-color, pull-icon, pair-v3, all-v3, or all.",
    ).default("discovery")

    private val target by option(
        "--target",
        help = "Target host[:port]. If omitted, mDNS discovery is used.",
    )

    private val targetAppId by option(
        "--target-app-id",
        help = "Filter discovery (or attach to --target) by appInstanceId.",
    )

    private val discoveryTimeout by option(
        "--discovery-timeout",
        help = "Seconds to wait for mDNS discovery.",
    ).long().default(10)

    private val appInstanceId by option(
        "--app-instance-id",
        help = "appInstanceId for this peer (random UUID if unset).",
    )

    private val pushText by option(
        "--push-text",
        help = "Text payload for push-text scenario.",
    )

    private val pushUrl by option(
        "--push-url",
        help = "URL payload for push-url scenario.",
    )

    private val pushHtml by option(
        "--push-html",
        help = "HTML payload for push-html scenario.",
    )

    private val pushRtf by option(
        "--push-rtf",
        help = "RTF payload for push-rtf scenario.",
    )

    private val pushColor by option(
        "--push-color",
        help = "Color (ARGB hex like 0xFFFF0000 or decimal int) for push-color scenario.",
    )

    private val iconSource by option(
        "--icon-source",
        help = "Source key for pull-icon scenario (required for pull-icon).",
    ).default("")

    private val junitXml by option(
        "--junit-xml",
        help = "Optional path to write a JUnit XML report.",
    )

    private val advertiseAddress by option(
        "--advertise-address",
        help =
            "Force mDNS advertising on this IPv4 address (optionally IP/prefixLength, default /24) " +
                "instead of auto-enumerated interfaces. Use when the peer's real interface toward the " +
                "target is filtered out as virtual — e.g. a Parallels host-only vnic when the target is " +
                "a VM reached over host-only networking. The address must be on the target's subnet so " +
                "the target can subnet-match and register a SyncHandler (required for push scenarios).",
    )

    override fun run() {
        val pairingVersion =
            if (scenario.lowercase() in V3_SCENARIOS) {
                PairingV3.PROTOCOL_VERSION
            } else {
                SyncApi.PAIRING_VERSION
            }
        val advertiseAddresses = advertiseAddress?.let { listOf(parseAdvertiseAddress(it)) }
        val peer =
            appInstanceId?.let {
                HeadlessPeer(
                    appInstanceId = it,
                    pairingVersion = pairingVersion,
                    advertiseAddresses = advertiseAddresses,
                )
            } ?: HeadlessPeer(
                pairingVersion = pairingVersion,
                advertiseAddresses = advertiseAddresses,
            )
        try {
            val ctx =
                ScenarioContext(
                    peer = peer,
                    target = target?.let(::parseTarget)?.copy(appInstanceId = targetAppId),
                    targetAppInstanceId = targetAppId,
                    discoveryTimeoutMs = discoveryTimeout * 1000,
                    tokenProvider = ::promptToken,
                    pinProvider = ::promptPin,
                    allowLegacyPairing = scenario.lowercase() !in V3_SCENARIOS,
                )

            val now = getDateUtils().nowEpochMilliseconds()
            val payloads =
                Payloads(
                    text = pushText ?: "e2e-ping $now",
                    url = pushUrl ?: "https://crosspaste.com/?e2e=$now",
                    html = pushHtml ?: "<p>e2e-ping $now</p>",
                    rtf = pushRtf ?: "{\\rtf1 e2e-ping $now}",
                    color = pushColor?.let(::parseColor) ?: 0xFFFF0000.toInt(),
                )

            val scenarios = selectScenarios(scenario, payloads, iconSource)
            val results =
                runBlocking {
                    scenarios.map { s ->
                        val started = System.nanoTime()
                        // A scenario that throws (e.g. a probe hitting an unreachable port)
                        // must not abort the whole batch — capture it as a Fail so later
                        // scenarios still run and the summary/JUnit report are still emitted.
                        val result =
                            runCatching { s.run(ctx) }
                                .getOrElse { e ->
                                    if (e is CancellationException) throw e
                                    ScenarioResult.Fail("Scenario '${s.name}' threw", e)
                                }
                        val durationMs = (System.nanoTime() - started) / 1_000_000.0
                        RunResult(s.name, result, durationMs)
                    }
                }

            printSummary(peer.appInfo.appInstanceId, results)
            junitXml?.let { writeJUnitReport(File(it), results) }

            val failed = results.count { it.result is ScenarioResult.Fail }
            if (failed > 0) kotlin.system.exitProcess(1)
        } finally {
            peer.close()
        }
    }
}

private data class Payloads(
    val text: String,
    val url: String,
    val html: String,
    val rtf: String,
    val color: Int,
)

private data class RunResult(
    val name: String,
    val result: ScenarioResult,
    val durationMs: Double,
)

private fun selectScenarios(
    name: String,
    payloads: Payloads,
    iconSource: String,
): List<Scenario> =
    when (name.lowercase()) {
        "discovery" -> listOf(DiscoveryScenario())
        "pair" -> listOf(PairScenario())
        "pair-v3" -> listOf(PairV3Scenario())
        "wrong-token" -> listOf(WrongTokenScenario())
        "telnet" -> listOf(TelnetScenario())
        "push-text" -> listOf(PushTextScenario(payloads.text))
        "push-url" -> listOf(PushUrlScenario(payloads.url))
        "push-html" -> listOf(PushHtmlScenario(payloads.html))
        "push-rtf" -> listOf(PushRtfScenario(payloads.rtf))
        "push-color" -> listOf(PushColorScenario(payloads.color))
        "pull-icon" -> listOf(PullIconScenario(iconSource))
        "all" ->
            listOf(
                // wrong-token first so the failure path runs against an untrusted state
                DiscoveryScenario(),
                WrongTokenScenario(),
                PairScenario(),
                // After pair: peer is trusted, so telnet's address-push exercises the gated
                // server merge path; its identity-header check needs no trust.
                TelnetScenario(),
                PushTextScenario(payloads.text),
                PushUrlScenario(payloads.url),
                PushHtmlScenario(payloads.html),
                PushRtfScenario(payloads.rtf),
                PushColorScenario(payloads.color),
            )
        "all-v3" ->
            listOf(
                DiscoveryScenario(),
                PairV3Scenario(),
                TelnetScenario(),
                PushTextScenario(payloads.text),
                PushUrlScenario(payloads.url),
                PushHtmlScenario(payloads.html),
                PushRtfScenario(payloads.rtf),
                PushColorScenario(payloads.color),
            )
        else -> error("Unknown scenario: $name")
    }

private fun parseTarget(spec: String): TargetSpec {
    val (host, port) =
        if (spec.contains(":")) {
            val (h, p) = spec.split(":", limit = 2)
            h to p.toInt()
        } else {
            spec to DEFAULT_PORT
        }
    return TargetSpec(host = host, port = port, appInstanceId = null)
}

/**
 * Parse an `--advertise-address` value: a bare IPv4 (`10.37.129.2`, prefix defaults to /24)
 * or IPv4 with an explicit prefix length (`10.37.129.2/24`).
 */
private fun parseAdvertiseAddress(spec: String): Pair<Inet4Address, Short> {
    val (ipPart, prefixPart) =
        if (spec.contains("/")) {
            val (ip, p) = spec.split("/", limit = 2)
            ip to p.toShort()
        } else {
            spec to 24.toShort()
        }
    val addr =
        InetAddress.getByName(ipPart) as? Inet4Address
            ?: error("--advertise-address must be an IPv4 address: $spec")
    require(prefixPart in 0..32) { "--advertise-address prefix length must be 0..32: $spec" }
    return addr to prefixPart
}

private fun parseColor(raw: String): Int {
    val trimmed = raw.trim()
    val (text, radix) =
        when {
            trimmed.startsWith("0x", ignoreCase = true) -> trimmed.substring(2) to 16
            trimmed.startsWith("#") -> trimmed.substring(1) to 16
            else -> trimmed to 10
        }
    val value = text.toUInt(radix)
    return if (radix == 16 && text.length <= 6) (value or 0xFF000000u).toInt() else value.toInt()
}

private fun promptToken(): Int {
    print("Enter the token shown on the target device: ")
    System.out.flush()
    val line = readlnOrNull()?.trim() ?: error("No token entered.")
    return line.toIntOrNull() ?: error("Invalid token: '$line' (must be an integer).")
}

private fun promptPin(): CharArray {
    val pin =
        System.console()?.readPassword("Enter the six-digit PIN shown on the target device: ")
            ?: run {
                print("Enter the six-digit PIN shown on the target device: ")
                System.out.flush()
                readlnOrNull()?.trim()?.toCharArray()
            }
            ?: error("No PIN entered.")
    if (pin.size != PairingV3.PIN_LENGTH || pin.any { it !in '0'..'9' }) {
        pin.fill('\u0000')
        error("Invalid PIN (must be exactly six decimal digits).")
    }
    return pin
}

private fun printSummary(
    peerId: String,
    results: List<RunResult>,
) {
    println()
    println("=== e2e summary (peer=$peerId) ===")
    results.forEach { run ->
        when (val result = run.result) {
            is ScenarioResult.Pass -> println("  PASS  ${run.name}  — ${result.message}")
            is ScenarioResult.Fail -> {
                println("  FAIL  ${run.name}  — ${result.reason}")
                result.cause?.let { println("        cause: ${it.message}") }
            }
        }
    }
}

private fun writeJUnitReport(
    file: File,
    results: List<RunResult>,
) {
    file.parentFile?.mkdirs()
    val totalSeconds = results.sumOf { it.durationMs } / 1000.0
    val failures = results.count { it.result is ScenarioResult.Fail }
    val xml =
        buildString {
            appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
            append("<testsuite name=\"crosspaste-e2e\" tests=\"${results.size}\"")
            append(" failures=\"$failures\" errors=\"0\" skipped=\"0\"")
            appendLine(" time=\"${"%.3f".format(totalSeconds)}\">")
            results.forEach { run ->
                val seconds = "%.3f".format(run.durationMs / 1000.0)
                append("  <testcase classname=\"crosspaste-e2e\"")
                append(" name=\"${xmlAttr(run.name)}\"")
                append(" time=\"$seconds\"")
                when (val result = run.result) {
                    is ScenarioResult.Pass -> appendLine("/>")
                    is ScenarioResult.Fail -> {
                        appendLine(">")
                        append("    <failure message=\"${xmlAttr(result.reason)}\">")
                        val causeMessage = result.cause?.let { it.message ?: it::class.qualifiedName }
                        val body =
                            buildString {
                                append(result.reason)
                                if (causeMessage != null) append("\nCause: ").append(causeMessage)
                            }
                        append(xmlText(body))
                        appendLine("</failure>")
                        appendLine("  </testcase>")
                    }
                }
            }
            appendLine("</testsuite>")
        }
    file.writeText(xml)
    println("JUnit XML report written to ${file.absolutePath}")
}

private fun xmlAttr(value: String): String =
    value
        .replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

private fun xmlText(value: String): String =
    value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

private const val DEFAULT_PORT = 13129
private val V3_SCENARIOS = setOf("pair-v3", "all-v3")

fun main(args: Array<String>) = E2eCommand().main(args)
