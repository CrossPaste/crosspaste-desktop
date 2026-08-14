package com.crosspaste.cli.platform

import com.crosspaste.cli.api.CliEndpoint
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.unixSocket
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import okio.FileSystem
import kotlin.time.Duration.Companion.milliseconds

enum class AppLiveness {
    RUNNING,
    STARTING,
    NOT_RUNNING,
}

/**
 * Implements the design D5 liveness sequence over the app's cli-endpoint.json
 * discovery file: no readable file means the app is not running; a 200 from
 * GET /cli/status over the declared socket means it is running; otherwise the
 * recorded pid disambiguates an app that is still starting up from a stale
 * crash leftover.
 */
class AppReadinessChecker(
    private val configReader: CliConfigReader,
) {

    companion object {
        private val POLL_INTERVAL = 500.milliseconds
        private const val MAX_POLLS = 60
    }

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    suspend fun probe(): AppLiveness {
        val endpoint = readEndpoint() ?: return AppLiveness.NOT_RUNNING
        val client = createClient()
        try {
            if (isReady(client, endpoint.socketPath)) {
                return AppLiveness.RUNNING
            }
        } finally {
            client.close()
        }
        return if (isProcessAlive(endpoint.pid)) AppLiveness.STARTING else AppLiveness.NOT_RUNNING
    }

    /**
     * Polls until the app's CLI endpoint is ready: the endpoint file must exist
     * (re-read every round — the app writes it only once the socket is live) and
     * GET /cli/status over that socket must answer 200.
     */
    suspend fun waitForAppReady(): Boolean {
        val client = createClient()
        try {
            repeat(MAX_POLLS) {
                val endpoint = readEndpoint()
                if (endpoint != null && isReady(client, endpoint.socketPath)) {
                    return true
                }
                delay(POLL_INTERVAL)
            }
            return false
        } finally {
            client.close()
        }
    }

    private fun createClient(): HttpClient =
        HttpClient(CIO) {
            engine {
                requestTimeout = 2000
            }
        }

    private suspend fun isReady(
        client: HttpClient,
        socketPath: String,
    ): Boolean =
        try {
            val response =
                client.get("http://localhost/cli/status") {
                    unixSocket(socketPath)
                }
            response.status == HttpStatusCode.OK
        } catch (_: Exception) {
            false
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
