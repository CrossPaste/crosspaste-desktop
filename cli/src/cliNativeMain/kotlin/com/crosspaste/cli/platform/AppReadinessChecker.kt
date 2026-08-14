package com.crosspaste.cli.platform

import com.crosspaste.cli.api.CliEndpoint
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.unixSocket
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.FileSystem
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

enum class AppLiveness {
    RUNNING,
    STARTING,
    NOT_RUNNING,
}

/**
 * Implements the design D5 liveness sequence over the app's cli-endpoint.json
 * discovery file: no readable file means the app is not running; a 200 from
 * GET /cli/status over the declared socket whose appInstanceId matches the
 * file means it is running; otherwise the recorded pid disambiguates an app
 * that is still starting up from a stale crash leftover.
 *
 * [socketProber] and [processAlive] are injectable for tests only.
 */
class AppReadinessChecker(
    private val configReader: CliConfigReader,
    private val socketProber: suspend (CliEndpoint) -> Boolean = ::probeOverUnixSocket,
    private val processAlive: (Long) -> Boolean = ::isProcessAlive,
) {

    companion object {
        private val POLL_INTERVAL = 500.milliseconds
        private val READY_TIMEOUT = 30.seconds
    }

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    suspend fun probe(): AppLiveness {
        val endpoint = readEndpoint() ?: return AppLiveness.NOT_RUNNING
        if (socketProber(endpoint)) {
            return AppLiveness.RUNNING
        }
        return if (processAlive(endpoint.pid)) AppLiveness.STARTING else AppLiveness.NOT_RUNNING
    }

    /**
     * Polls until the app's CLI endpoint is ready, bounded by a hard 30s
     * deadline regardless of how long individual probes take. The endpoint
     * file is re-read every round — the app writes it only once the socket
     * is live, and a restart may change its contents.
     */
    suspend fun waitForAppReady(): Boolean =
        withTimeoutOrNull(READY_TIMEOUT) {
            while (!isEndpointReady()) {
                delay(POLL_INTERVAL)
            }
            true
        } ?: false

    private suspend fun isEndpointReady(): Boolean {
        val endpoint = readEndpoint() ?: return false
        return socketProber(endpoint)
    }

    private fun readEndpoint(): CliEndpoint? =
        try {
            val content =
                FileSystem.SYSTEM.read(configReader.resolveEndpointFilePath()) { readUtf8() }
            json.decodeFromString<CliEndpoint>(content)
        } catch (_: Exception) {
            null
        }
}

/**
 * One probe = one short-lived request; the 500ms timeout (design D5) keeps a
 * single probe from stalling the caller's overall deadline.
 */
private suspend fun probeOverUnixSocket(endpoint: CliEndpoint): Boolean {
    val client =
        HttpClient(CIO) {
            engine {
                requestTimeout = 500
            }
        }
    return try {
        val response =
            client.get("http://localhost/cli/status") {
                unixSocket(endpoint.socketPath)
            }
        response.status == HttpStatusCode.OK &&
            matchesEndpointInstance(response.bodyAsText(), endpoint)
    } catch (_: Exception) {
        false
    } finally {
        client.close()
    }
}

/**
 * D5: a 200 only counts as "running" when the responder is the instance the
 * endpoint file describes — a stale file must not vouch for a different peer.
 */
internal fun matchesEndpointInstance(
    statusBody: String,
    endpoint: CliEndpoint,
): Boolean =
    try {
        instanceJson.decodeFromString<StatusInstance>(statusBody).appInstanceId == endpoint.appInstanceId
    } catch (_: Exception) {
        false
    }

private val instanceJson =
    Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

@Serializable
private data class StatusInstance(
    val appInstanceId: String,
)
