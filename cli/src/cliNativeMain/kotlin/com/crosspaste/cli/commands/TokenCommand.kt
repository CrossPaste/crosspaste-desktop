package com.crosspaste.cli.commands

import com.crosspaste.cli.CliContext
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
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

// Mirrors of the app-side pair token DTOs (CliApi.kt); parsed with ignoreUnknownKeys.
@Serializable
internal data class PairRequestSummary(
    val appInstanceId: String,
    val deviceName: String? = null,
    val token: String = "",
    // "v2-sas" or "v3-pin"; an older app omits it, which means v2.
    val credentialType: String = CREDENTIAL_V2_SAS,
    // v3 only: epoch millis when the current PIN generation expires.
    val pinExpiresAt: Long? = null,
    // v3 only: the rotation generation the PIN belongs to.
    val tokenGeneration: Long? = null,
)

@Serializable
internal data class PairTokenSnapshot(
    val requests: List<PairRequestSummary> = listOf(),
)

internal const val CREDENTIAL_V2_SAS = "v2-sas"
internal const val CREDENTIAL_V3_PIN = "v3-pin"

/** Which acceptance-window arming a token fetch carries (the `arm` query param). */
internal enum class TokenFetchArm(
    val query: String?,
) {
    /** Plain read: no arming (one-shot `token` without --wait). */
    NONE(null),

    /** First fetch of a --wait invocation: opens the v3 acceptance window. */
    START("start"),

    /** Subsequent --wait polls: extends the window without resetting budgets. */
    RENEW("renew"),
}

/** How the terminal-facing token flow reports back; injectable for tests. */
internal class TokenIo(
    val stdout: (String) -> Unit,
    val stderr: (String) -> Unit,
)

internal val TOKEN_POLL_INTERVAL = 500.milliseconds

/**
 * Acceptor-side counterpart of `pair` (issue #4858): while `pair` types the
 * code the OTHER device displays, `token` displays THIS device's code so a
 * headless daemon can be paired with at all (its SAS otherwise only lands in
 * the daemon log). Codes go to stdout alone — `CODE=$(crosspaste token)`
 * works — and all context lines go to stderr.
 *
 * Each request carries its own code (the SAS of that peer's key exchange or
 * the current v3 PIN of that peer's session), so concurrent pairings can never
 * caption one device's name with another's code. Print-once-and-exit stays the
 * contract for both: a SAS is deterministic for a given device pair, and a v3
 * PIN is read live per invocation — it rotates every 30 seconds, so rerun (or
 * poll with --json) for the current code.
 *
 * `--wait` additionally arms the v3 acceptance window while it runs: invoking
 * the command is a local operator gesture, the headless equivalent of opening
 * the Add Device screen. Without it a headless acceptor refuses v3 intents
 * (PAIRING_DISABLED).
 */
internal class TokenCommand : CliktCommand(name = "token") {

    override fun help(context: Context): String = "Show the pairing code when another device is pairing with this one"

    private val ctx by requireObject<CliContext>()

    private val wait by option(
        "--wait",
        help =
            "Wait for a pairing request to arrive instead of failing when there is none, " +
                "and accept v3 pairing while waiting",
    ).flag()

    private val timeout by option(
        "--timeout",
        help = "How long --wait waits, in seconds",
    ).int().restrictTo(min = 1).default(600)

    override fun run() {
        // The deadline is fixed before runCli so an app restart mid-wait
        // (runCli re-runs the whole block) resumes the SAME wall-clock budget
        // instead of granting a fresh one
        val deadline = TimeSource.Monotonic.markNow() + timeout.seconds
        runCli { client ->
            val exitCode =
                executeToken(
                    wait = wait,
                    timeoutSeconds = timeout,
                    deadline = deadline,
                    json = ctx.json,
                    io = TokenIo(stdout = { echo(it) }, stderr = { echo(it, err = true) }),
                    fetch = { arm ->
                        val query = arm.query?.let { "?arm=$it" } ?: ""
                        client.getBody("/cli/pair/token$query", PairTokenSnapshot.serializer())
                    },
                )
            if (exitCode != 0) {
                throw ProgramResult(exitCode)
            }
        }
    }
}

/**
 * The whole token flow behind the Clikt/runCli shell, so tests can drive it
 * with a fake [fetch] and captured [io]. Returns the exit code; exceptions
 * other than the old-app 404 propagate to runCli's uniform handling.
 */
internal suspend fun executeToken(
    wait: Boolean,
    timeoutSeconds: Int,
    deadline: ComparableTimeMark,
    json: Boolean,
    io: TokenIo,
    pollInterval: Duration = TOKEN_POLL_INTERVAL,
    sleep: suspend (Duration) -> Unit = { delay(it) },
    fetch: suspend (TokenFetchArm) -> PairTokenSnapshot,
): Int =
    try {
        executeTokenInner(wait, timeoutSeconds, deadline, json, io, pollInterval, sleep, fetch)
    } catch (e: CliClientException) {
        if (isTokenRouteMissing(e)) {
            io.stderr("Error: The running CrossPaste app is too old for this command; update it first.")
            1
        } else {
            throw e
        }
    }

private suspend fun executeTokenInner(
    wait: Boolean,
    timeoutSeconds: Int,
    deadline: ComparableTimeMark,
    json: Boolean,
    io: TokenIo,
    pollInterval: Duration,
    sleep: suspend (Duration) -> Unit,
    fetch: suspend (TokenFetchArm) -> PairTokenSnapshot,
): Int {
    // With --wait the FIRST fetch already counts against the wall-clock
    // budget: it can block for its own HTTP timeout, and a runCli retry can
    // re-enter here with the deadline already expired — in both cases the
    // budget must hold (a late active answer past the deadline is discarded,
    // not reported as success). Without --wait there is no time contract and
    // the single fetch runs unbounded as before.
    //
    // Arming follows the invocation shape: the first --wait fetch is the
    // operator's gesture (opens the v3 acceptance window), the poll loop only
    // renews it, and a plain read never arms.
    var snapshot =
        if (wait) {
            fetchWithinDeadline(deadline) { fetch(TokenFetchArm.START) } ?: PairTokenSnapshot()
        } else {
            fetch(TokenFetchArm.NONE)
        }
    if (wait && snapshot.requests.isEmpty() && deadline.hasNotPassedNow()) {
        io.stderr("Waiting for a pairing request... (Ctrl-C to stop)")
        awaitPairingRequests(deadline, pollInterval, sleep) { fetch(TokenFetchArm.RENEW) }?.let { snapshot = it }
    }
    // A timed-out --wait falls through with the empty snapshot, so the JSON
    // contract is uniform: stdout always carries one snapshot and the exit
    // code alone reports failure
    if (json) {
        io.stdout(cliJson.encodeToString(PairTokenSnapshot.serializer(), snapshot))
        return if (snapshot.requests.isEmpty()) 1 else 0
    }
    if (snapshot.requests.isEmpty()) {
        io.stderr("Error: ${noPairingMessage(waited = wait, timeoutSeconds = timeoutSeconds)}")
        return 1
    }
    for (request in snapshot.requests) {
        io.stderr("Pairing request from '${requesterLabel(request)}':")
        io.stdout(request.token)
    }
    io.stderr(
        if (snapshot.requests.size == 1) {
            "Enter this code on the initiating device."
        } else {
            "Enter each code on its initiating device."
        },
    )
    if (snapshot.requests.any { it.credentialType == CREDENTIAL_V3_PIN }) {
        io.stderr("Codes rotate every 30 seconds; rerun token for the current code.")
    }
    return 0
}

/** Runs [fetch] under the remaining budget; null when the budget is spent. */
private suspend fun fetchWithinDeadline(
    deadline: ComparableTimeMark,
    fetch: suspend () -> PairTokenSnapshot,
): PairTokenSnapshot? {
    if (!deadline.hasNotPassedNow()) {
        return null
    }
    return withTimeoutOrNull(-deadline.elapsedNow()) { fetch() }
}

/**
 * Polls [fetch] every [pollInterval] until a pairing request appears, or null
 * once [deadline] passes. The deadline is a true wall-clock bound: each round
 * (sleep AND the fetch itself, which can block for its own HTTP timeout) runs
 * under `withTimeoutOrNull` of the remaining budget. The first fetch happens
 * after one interval — the caller has already seen an empty snapshot.
 */
internal suspend fun awaitPairingRequests(
    deadline: ComparableTimeMark,
    pollInterval: Duration = TOKEN_POLL_INTERVAL,
    sleep: suspend (Duration) -> Unit = { delay(it) },
    fetch: suspend () -> PairTokenSnapshot,
): PairTokenSnapshot? {
    while (deadline.hasNotPassedNow()) {
        val remaining = -deadline.elapsedNow()
        val snapshot =
            withTimeoutOrNull(remaining) {
                sleep(pollInterval)
                fetch()
            } ?: return null
        if (snapshot.requests.isNotEmpty()) {
            return snapshot
        }
    }
    return null
}

/**
 * Display label for a requester, sanitized at the terminal boundary: names
 * and instance ids arrive from unauthenticated peers (mDNS announcements,
 * exchange headers) and could otherwise smuggle ESC/OSC/BEL sequences into
 * the terminal. JSON output keeps the raw values — encoding escapes them.
 */
internal fun requesterLabel(request: PairRequestSummary): String {
    val name =
        request.deviceName
            ?.let { sanitizeTerminalText(it) }
            ?.takeIf { it.isNotBlank() }
    return name
        ?: sanitizeTerminalText(request.appInstanceId).ifBlank { "unknown device" }
}

/**
 * A 404 without a server message means the route itself does not exist — the
 * running app predates this command (see [CliClientException.hasServerMessage];
 * the endpoint's own no-requests answer is a 200, never a 404).
 */
internal fun isTokenRouteMissing(e: CliClientException): Boolean = e.statusCode == 404 && !e.hasServerMessage

/** Error line for a missing code: timeout wording after --wait, guidance otherwise. */
internal fun noPairingMessage(
    waited: Boolean,
    timeoutSeconds: Int,
): String =
    if (waited) {
        "No pairing request arrived within ${timeoutSeconds}s."
    } else {
        "No pairing in progress. Start pairing from the other device " +
            "(crosspaste pair), or rerun with --wait."
    }
