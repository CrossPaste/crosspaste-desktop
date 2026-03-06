package com.crosspaste.cli.platform

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.delay
import okio.FileSystem

class AppReadinessChecker(
    private val configReader: CliConfigReader,
) {

    companion object {
        private const val POLL_INTERVAL_MS = 500L
        private const val MAX_WAIT_MS = 30_000L
    }

    suspend fun waitForAppReady(): Boolean {
        val elapsed = 0L
        var current = elapsed
        while (current < MAX_WAIT_MS) {
            if (isTokenAvailable() && isHttpReady()) {
                return true
            }
            delay(POLL_INTERVAL_MS)
            current += POLL_INTERVAL_MS
        }
        return false
    }

    private fun isTokenAvailable(): Boolean {
        val tokenPath = configReader.resolveTokenPath()
        return try {
            FileSystem.SYSTEM.exists(tokenPath)
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun isHttpReady(): Boolean {
        val port = configReader.resolvePort()
        val token =
            try {
                FileSystem.SYSTEM.read(configReader.resolveTokenPath()) { readUtf8() }.trim()
            } catch (_: Exception) {
                return false
            }

        val client =
            HttpClient(CIO) {
                engine {
                    requestTimeout = 2000
                }
            }
        return try {
            val response =
                client.get("http://127.0.0.1:$port/cli/status") {
                    header("Authorization", "Bearer $token")
                }
            response.status == HttpStatusCode.OK
        } catch (_: Exception) {
            false
        } finally {
            client.close()
        }
    }
}
