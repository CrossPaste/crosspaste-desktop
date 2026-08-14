package com.crosspaste.cli.platform

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.unixSocket
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.FileSystem
import kotlin.time.Duration.Companion.milliseconds

/**
 * Polls until the app's CLI endpoint is ready: the endpoint file must exist
 * (re-read every round — the app writes it only once the socket is live) and
 * GET /cli/status over that socket must answer 200.
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

    suspend fun waitForAppReady(): Boolean {
        repeat(MAX_POLLS) {
            if (isReady()) {
                return true
            }
            delay(POLL_INTERVAL)
        }
        return false
    }

    private suspend fun isReady(): Boolean {
        val socketPath = readSocketPath() ?: return false
        val client =
            HttpClient(CIO) {
                engine {
                    requestTimeout = 2000
                }
            }
        return try {
            val response =
                client.get("http://localhost/cli/status") {
                    unixSocket(socketPath)
                }
            response.status == HttpStatusCode.OK
        } catch (_: Exception) {
            false
        } finally {
            client.close()
        }
    }

    private fun readSocketPath(): String? =
        try {
            val content =
                FileSystem.SYSTEM.read(configReader.resolveEndpointFilePath()) { readUtf8() }
            json.decodeFromString<EndpointSocket>(content).socketPath
        } catch (_: Exception) {
            null
        }
}

@Serializable
private data class EndpointSocket(
    val socketPath: String,
)
