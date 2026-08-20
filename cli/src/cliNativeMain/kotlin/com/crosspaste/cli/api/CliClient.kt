package com.crosspaste.cli.api

import com.crosspaste.cli.platform.CliConfigReader
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.FileSystem

/**
 * Version of the local /cli API this binary speaks. Mirrors CLI_API_VERSION
 * on the app side; a mismatch produces a warning, not a failure.
 */
const val CLI_API_VERSION = 1

/** Default per-request timeout; long-running pair calls override it per call. */
internal const val DEFAULT_REQUEST_TIMEOUT_MILLIS = 5000L

/**
 * Mirror of the app's CliEndpoint (see design D5): the app atomically writes
 * this discovery record when its CLI socket is ready and deletes it on exit.
 */
@Serializable
data class CliEndpoint(
    val pid: Long,
    val socketPath: String,
    val apiVersion: Int,
    val appInstanceId: String,
)

class CliClientException(
    message: String,
    cause: Throwable? = null,
    /** HTTP status of a failed response, null for transport-level failures. */
    val statusCode: Int? = null,
    /**
     * Whether the failure body carried a structured server message. A 404
     * WITHOUT one means the route itself does not exist (an older app);
     * callers must branch on this, never on the message text.
     */
    val hasServerMessage: Boolean = false,
) : Exception(message, cause)

class AppNotRunningException : Exception("CrossPaste is not running. Please start the application first.")

/**
 * Thin HTTP client for the app's /cli API over its Unix domain socket.
 * The CLI has no database or clipboard access of its own; every command
 * goes through the local peer process (design D1).
 */
class CliClient(
    private val configReader: CliConfigReader,
) : AutoCloseable {

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    val endpoint: CliEndpoint = readEndpoint()

    private val httpClient =
        HttpClient(CIO) {
            engine {
                // Disable the engine-level cap so the HttpTimeout plugin below
                // is the single authority (some pair calls need more than the
                // engine default of 15s)
                requestTimeout = 0
            }
            install(HttpTimeout) {
                requestTimeoutMillis = DEFAULT_REQUEST_TIMEOUT_MILLIS
            }
        }

    fun hasApiVersionMismatch(): Boolean = endpoint.apiVersion != CLI_API_VERSION

    private fun readEndpoint(): CliEndpoint {
        val endpointPath = configReader.resolveEndpointFilePath()
        val content =
            try {
                FileSystem.SYSTEM.read(endpointPath) { readUtf8() }
            } catch (_: Exception) {
                throw AppNotRunningException()
            }
        return try {
            json.decodeFromString<CliEndpoint>(content)
        } catch (_: Exception) {
            // A corrupted endpoint file is indistinguishable from a stale one
            throw AppNotRunningException()
        }
    }

    suspend fun <T> getBody(
        path: String,
        deserializer: DeserializationStrategy<T>,
        timeoutMillis: Long? = null,
    ): T = decode(request(HttpMethod.Get, path, timeoutMillis = timeoutMillis), deserializer)

    suspend fun <T> postBody(
        path: String,
        body: String,
        deserializer: DeserializationStrategy<T>,
        timeoutMillis: Long? = null,
    ): T = decode(request(HttpMethod.Post, path, body, timeoutMillis), deserializer)

    suspend fun <T> putBody(
        path: String,
        body: String,
        deserializer: DeserializationStrategy<T>,
    ): T = decode(put(path, body), deserializer)

    suspend fun <T> deleteBody(
        path: String,
        deserializer: DeserializationStrategy<T>,
    ): T = decode(delete(path), deserializer)

    suspend fun get(path: String): HttpResponse = request(HttpMethod.Get, path)

    suspend fun post(
        path: String,
        body: String,
    ): HttpResponse = request(HttpMethod.Post, path, body)

    suspend fun put(
        path: String,
        body: String,
    ): HttpResponse = request(HttpMethod.Put, path, body)

    suspend fun delete(path: String): HttpResponse = request(HttpMethod.Delete, path)

    private suspend fun request(
        httpMethod: HttpMethod,
        path: String,
        body: String? = null,
        timeoutMillis: Long? = null,
    ): HttpResponse =
        try {
            httpClient.request("http://localhost$path") {
                method = httpMethod
                unixSocket(endpoint.socketPath)
                timeoutMillis?.let { millis ->
                    timeout {
                        requestTimeoutMillis = millis
                    }
                }
                body?.let {
                    contentType(ContentType.Application.Json)
                    setBody(it)
                }
            }
        } catch (e: CancellationException) {
            // Cancelling a caller's coroutine must stay a cancellation:
            // wrapping it would make a deliberately-abandoned request look
            // like a request FAILURE to the caller's error handling
            throw e
        } catch (e: Exception) {
            if (isAppUnreachable(e)) {
                throw AppNotRunningException()
            }
            throw CliClientException("Request failed: ${e.message}", e)
        }

    private suspend fun <T> decode(
        response: HttpResponse,
        deserializer: DeserializationStrategy<T>,
    ): T {
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            val serverMessage = extractMessage(text)
            throw CliClientException(
                serverMessage ?: "Request failed with status ${response.status}",
                statusCode = response.status.value,
                hasServerMessage = serverMessage != null,
            )
        }
        return try {
            json.decodeFromString(deserializer, text)
        } catch (e: Exception) {
            throw CliClientException("Unexpected response from CrossPaste: ${e.message}", e)
        }
    }

    private fun extractMessage(body: String): String? =
        runCatching {
            json.decodeFromString<ErrorMessage>(body).message
        }.getOrNull()

    private fun isAppUnreachable(e: Exception): Boolean {
        val message = e.message ?: ""
        return message.contains("Connection refused", ignoreCase = true) ||
            message.contains("ConnectException", ignoreCase = true) ||
            // K/Native CIO wording for a dead or missing unix socket
            message.contains("Failed to connect", ignoreCase = true) ||
            message.contains("No such file or directory", ignoreCase = true) ||
            message.contains("ENOENT", ignoreCase = true) ||
            message.contains("ECONNREFUSED", ignoreCase = true)
    }

    override fun close() {
        httpClient.close()
    }
}

@Serializable
private data class ErrorMessage(
    val message: String,
)
