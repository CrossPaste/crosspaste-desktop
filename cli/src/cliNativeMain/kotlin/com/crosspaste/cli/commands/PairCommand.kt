package com.crosspaste.cli.commands

import com.crosspaste.cli.CliContext
import com.crosspaste.cli.api.CliClient
import com.crosspaste.cli.platform.readLineNoEcho
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.mordant.rendering.TextColors
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

// Mirrors of the app-side pair DTOs (CliApi.kt); parsed with ignoreUnknownKeys.
@Serializable
internal data class NearbyDeviceSummary(
    val appInstanceId: String,
    val deviceName: String,
    val platform: String,
    val appVersion: String,
    val pairingVersion: Int? = null,
    val credentialType: String = "",
)

@Serializable
internal data class PairInitiateRequest(
    val appInstanceId: String,
)

@Serializable
internal data class PairSessionResponse(
    val sessionId: String,
    val appInstanceId: String,
    val deviceName: String,
    val credentialType: String,
    val peerFingerprint: String? = null,
    val pinExpiresAt: Long? = null,
)

@Serializable
internal data class PairSubmitRequest(
    val sessionId: String,
    val code: String,
)

@Serializable
internal data class PairSubmitResponse(
    val paired: Boolean,
    val retryable: Boolean,
    val message: String,
)

@Serializable
internal data class PairCancelRequest(
    val sessionId: String,
)

/**
 * Interactive initiator-side pairing (P6.2, D-P6-2/D-P6-3): this machine picks
 * a nearby device and types the 6-digit code the target displays. Pairing is a
 * human confirmation by design, so a non-interactive stdin is a usage error.
 *
 * A SIGINT mid-session skips the cancel in the finally block; the peer's own
 * pairing session TTLs reclaim it (server-side timeout is the second half of
 * the double insurance).
 */
internal class PairCommand(
    private val secretReader: () -> String? = ::readLineNoEcho,
    private val lineReader: () -> String? = { readlnOrNull() },
) : CliktCommand(name = "pair") {

    companion object {
        // Server-side waits dominate these calls; see CliPairingService
        private const val NEARBY_TIMEOUT_MILLIS = 20_000L
        private const val INITIATE_TIMEOUT_MILLIS = 30_000L
        private const val SUBMIT_TIMEOUT_MILLIS = 60_000L

        private const val MAX_CODE_ATTEMPTS = 5

        private val SIX_DIGITS = Regex("\\d{6}")
    }

    override fun help(context: Context): String = "Pair with a nearby device by entering the code it displays"

    private val ctx by requireObject<CliContext>()

    private val target by option(
        "--target",
        help = "App instance id of the device to pair with (skips the interactive device list)",
    )

    override fun run() {
        if (!terminal.terminalInfo.inputInteractive) {
            throw usageError(
                "pair requires an interactive terminal: the pairing code shown " +
                    "on the target device has to be typed in.",
            )
        }
        runCli { client ->
            val targetId = target ?: selectDevice(client)
            val session = initiate(client, targetId)
            var paired = false
            try {
                paired = enterCodeLoop(client, session)
            } finally {
                if (!paired) {
                    // Best effort: a terminal failure already closed the session
                    // server-side, so a 404 here is expected and ignored
                    runCatching {
                        client.postBody(
                            "/cli/pair/cancel",
                            cliJson.encodeToString(
                                PairCancelRequest.serializer(),
                                PairCancelRequest(session.sessionId),
                            ),
                            MessageResponse.serializer(),
                        )
                    }
                }
            }
            if (!paired) {
                throw ProgramResult(1)
            }
        }
    }

    private suspend fun selectDevice(client: CliClient): String {
        echo("Searching for nearby devices...", err = true)
        val nearby =
            client.getBody(
                "/cli/pair/nearby?refresh=true",
                ListSerializer(NearbyDeviceSummary.serializer()),
                timeoutMillis = NEARBY_TIMEOUT_MILLIS,
            )
        // Devices already known but never trusted (Unverified) are equally
        // pairable; the nearby list deliberately excludes them
        val unverified =
            client
                .getBody("/cli/devices", ListSerializer(DeviceSummary.serializer()))
                .filter { it.connectState == 4 }
                .filter { device -> nearby.none { it.appInstanceId == device.appInstanceId } }
        val candidates =
            nearby.map { device ->
                Candidate(
                    appInstanceId = device.appInstanceId,
                    displayName = device.deviceName,
                    platform = device.platform,
                    appVersion = device.appVersion,
                    unsupported = device.credentialType == "QR_BEARER_TOKEN",
                )
            } +
                unverified.map { device ->
                    Candidate(
                        appInstanceId = device.appInstanceId,
                        displayName = device.noteName ?: device.deviceName,
                        platform = device.platform,
                        appVersion = device.appVersion,
                        unsupported = false,
                    )
                }
        if (candidates.isEmpty()) {
            echo(
                "Error: No devices available for pairing. Make sure CrossPaste is running " +
                    "on the other device and both are on the same network.",
                err = true,
            )
            throw ProgramResult(1)
        }
        echo("", err = true)
        candidates.forEachIndexed { index, candidate ->
            val marker = if (candidate.unsupported) TextColors.red(" (app too old for CLI pairing)") else ""
            echo(
                "  ${index + 1}. ${candidate.displayName} " +
                    "(${candidate.platform} v${candidate.appVersion})$marker",
                err = true,
            )
        }
        echo("", err = true)
        while (true) {
            echo("Select a device [1-${candidates.size}], or q to cancel: ", trailingNewline = false, err = true)
            val answer = lineReader()?.trim() ?: throw ProgramResult(1)
            if (answer.equals("q", ignoreCase = true)) {
                throw ProgramResult(1)
            }
            val index = answer.toIntOrNull()
            if (index != null && index in 1..candidates.size) {
                return candidates[index - 1].appInstanceId
            }
            echo("Please enter a number between 1 and ${candidates.size}.", err = true)
        }
    }

    private suspend fun initiate(
        client: CliClient,
        appInstanceId: String,
    ): PairSessionResponse {
        echo("Contacting the device...", err = true)
        val session =
            client.postBody(
                "/cli/pair/initiate",
                cliJson.encodeToString(
                    PairInitiateRequest.serializer(),
                    PairInitiateRequest(appInstanceId),
                ),
                PairSessionResponse.serializer(),
                timeoutMillis = INITIATE_TIMEOUT_MILLIS,
            )
        echo("", err = true)
        echo(
            "'${session.deviceName}' now shows a 6-digit ${session.codeLabel()}. " +
                "Confirm on that device if prompted.",
            err = true,
        )
        session.peerFingerprint
            ?.takeIf { it.isNotEmpty() }
            ?.let { echo("Peer key fingerprint: $it", err = true) }
        return session
    }

    /** Returns true when pairing completed; false when the user gave up. */
    private suspend fun enterCodeLoop(
        client: CliClient,
        session: PairSessionResponse,
    ): Boolean {
        var attempts = 0
        while (attempts < MAX_CODE_ATTEMPTS) {
            echo(
                "Enter the ${session.codeLabel()} (input hidden, q to cancel): ",
                trailingNewline = false,
                err = true,
            )
            val input = secretReader()?.trim()
            // Enter is not echoed while echo is off; keep the prompt tidy
            echo("", err = true)
            if (input == null || input.equals("q", ignoreCase = true)) {
                echo("Pairing cancelled.", err = true)
                return false
            }
            if (!SIX_DIGITS.matches(input)) {
                echo("The ${session.codeLabel()} must be exactly 6 digits.", err = true)
                continue
            }
            attempts++
            val result =
                client.postBody(
                    "/cli/pair/submit",
                    cliJson.encodeToString(
                        PairSubmitRequest.serializer(),
                        PairSubmitRequest(session.sessionId, input),
                    ),
                    PairSubmitResponse.serializer(),
                    timeoutMillis = SUBMIT_TIMEOUT_MILLIS,
                )
            if (result.paired) {
                if (ctx.json) {
                    echo(cliJson.encodeToString(PairSubmitResponse.serializer(), result))
                } else {
                    echo(result.message)
                }
                return true
            }
            echo(result.message, err = true)
            if (!result.retryable) {
                return false
            }
        }
        echo("Error: Too many failed attempts.", err = true)
        return false
    }

    private fun PairSessionResponse.codeLabel(): String = if (credentialType == "V3_PIN") "PIN" else "code"

    private data class Candidate(
        val appInstanceId: String,
        val displayName: String,
        val platform: String,
        val appVersion: String,
        val unsupported: Boolean,
    )
}
