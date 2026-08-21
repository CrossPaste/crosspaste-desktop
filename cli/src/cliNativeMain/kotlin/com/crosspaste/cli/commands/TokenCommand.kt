package com.crosspaste.cli.commands

import com.crosspaste.cli.CliContext
import com.crosspaste.cli.api.CliClient
import com.crosspaste.cli.api.CliClientException
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.restrictTo
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// Mirrors of the app-side pair token DTOs (CliApi.kt); parsed with ignoreUnknownKeys.
@Serializable
internal data class PairRequesterSummary(
    val appInstanceId: String,
    val deviceName: String? = null,
)

@Serializable
internal data class PairTokenSnapshot(
    val active: Boolean = false,
    val token: String? = null,
    val requesters: List<PairRequesterSummary> = listOf(),
)

/**
 * Acceptor-side counterpart of `pair` (issue #4858): while `pair` types the
 * code the OTHER device displays, `token` displays THIS device's code so a
 * headless daemon can be paired with at all (its SAS otherwise only lands in
 * the daemon log). The code goes to stdout alone — `CODE=$(crosspaste token)`
 * works — and all context lines go to stderr.
 *
 * The SAS is deterministic for a given device pair, so print-once-and-exit is
 * the whole contract; there is nothing to live-update.
 */
internal class TokenCommand : CliktCommand(name = "token") {

    companion object {
        internal val POLL_INTERVAL = 500.milliseconds
    }

    override fun help(context: Context): String = "Show the pairing code when another device is pairing with this one"

    private val ctx by requireObject<CliContext>()

    private val wait by option(
        "--wait",
        help = "Wait for a pairing request to arrive instead of failing when there is none",
    ).flag()

    private val timeout by option(
        "--timeout",
        help = "How long --wait waits, in seconds",
    ).int().restrictTo(min = 1).default(600)

    override fun run() {
        runCli { client ->
            var snapshot = fetchSnapshot(client)
            if (!snapshot.active && wait) {
                echo("Waiting for a pairing request... (Ctrl-C to stop)", err = true)
                snapshot =
                    awaitActiveSnapshot(
                        timeout = timeout.seconds,
                        fetch = { fetchSnapshot(client) },
                    ) ?: run {
                        echo("Error: No pairing request arrived within ${timeout}s.", err = true)
                        throw ProgramResult(1)
                    }
            }
            if (ctx.json) {
                echo(cliJson.encodeToString(PairTokenSnapshot.serializer(), snapshot))
                if (!snapshot.active) {
                    throw ProgramResult(1)
                }
                return@runCli
            }
            val token = snapshot.token
            if (!snapshot.active || token == null) {
                echo(
                    "Error: No pairing in progress. Start pairing from the other device " +
                        "(crosspaste pair), or rerun with --wait.",
                    err = true,
                )
                throw ProgramResult(1)
            }
            describeRequesters(snapshot.requesters)?.let { echo(it, err = true) }
            echo(token)
            echo("Enter this code on the initiating device.", err = true)
        }
    }

    private suspend fun fetchSnapshot(client: CliClient): PairTokenSnapshot =
        try {
            client.getBody("/cli/pair/token", PairTokenSnapshot.serializer())
        } catch (e: CliClientException) {
            // A 404 without a server message means the route does not exist:
            // the running app predates this command
            if (e.statusCode == 404 && !e.hasServerMessage) {
                echo(
                    "Error: The running CrossPaste app is too old for this command; update it first.",
                    err = true,
                )
                throw ProgramResult(1)
            }
            throw e
        }
}

/**
 * Polls [fetch] every [pollInterval] until it reports an active pairing
 * display, or null after [timeout] worth of intervals. The first fetch
 * happens after one interval: the caller has already seen an inactive
 * snapshot. Attempt-counted rather than clock-based so the budget is
 * deterministic under an injected [sleep]; fetch latency (a local
 * unix-socket call) is negligible against the interval.
 */
internal suspend fun awaitActiveSnapshot(
    timeout: Duration,
    pollInterval: Duration = TokenCommand.POLL_INTERVAL,
    sleep: suspend (Duration) -> Unit = { delay(it) },
    fetch: suspend () -> PairTokenSnapshot,
): PairTokenSnapshot? {
    val attempts = (timeout / pollInterval).toInt().coerceAtLeast(1)
    repeat(attempts) {
        sleep(pollInterval)
        val snapshot = fetch()
        if (snapshot.active) {
            return snapshot
        }
    }
    return null
}

/** One stderr context line naming who is asking, or null when unknown. */
internal fun describeRequesters(requesters: List<PairRequesterSummary>): String? {
    if (requesters.isEmpty()) return null
    val names = requesters.map { it.deviceName ?: it.appInstanceId }
    return "Pairing request from ${names.joinToString(", ") { "'$it'" }}."
}
