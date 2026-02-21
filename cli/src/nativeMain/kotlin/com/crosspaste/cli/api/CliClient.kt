package com.crosspaste.cli.api

import com.crosspaste.cli.platform.CliConfigReader
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import okio.FileSystem

class CliClientException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class AppNotRunningException : Exception("CrossPaste is not running. Please start the application first.")

class CliClient(
    private val configReader: CliConfigReader,
) : AutoCloseable {

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    private val port: Int
    private val token: String

    private val httpClient =
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(json)
            }
            engine {
                requestTimeout = 5000
            }
        }

    init {
        port = configReader.resolvePort()
        token = readToken()
    }

    private fun readToken(): String {
        val tokenPath = configReader.resolveTokenPath()
        println("tokenPath = $tokenPath")
        return try {
            FileSystem.SYSTEM.read(tokenPath) { readUtf8() }.trim()
        } catch (_: Exception) {
            throw AppNotRunningException()
        }
    }

    suspend fun get(path: String): HttpResponse =
        try {
            httpClient.get("http://127.0.0.1:$port$path") {
                header("Authorization", "Bearer $token")
            }
        } catch (e: Exception) {
            if (isConnectionRefused(e)) {
                throw AppNotRunningException()
            }
            throw CliClientException("Request failed: ${e.message}", e)
        }

    suspend fun post(
        path: String,
        body: String,
    ): HttpResponse =
        try {
            httpClient.post("http://127.0.0.1:$port$path") {
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        } catch (e: Exception) {
            if (isConnectionRefused(e)) {
                throw AppNotRunningException()
            }
            throw CliClientException("Request failed: ${e.message}", e)
        }

    suspend fun put(
        path: String,
        body: String = "",
    ): HttpResponse =
        try {
            httpClient.put("http://127.0.0.1:$port$path") {
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        } catch (e: Exception) {
            if (isConnectionRefused(e)) {
                throw AppNotRunningException()
            }
            throw CliClientException("Request failed: ${e.message}", e)
        }

    suspend fun delete(path: String): HttpResponse =
        try {
            httpClient.delete("http://127.0.0.1:$port$path") {
                header("Authorization", "Bearer $token")
            }
        } catch (e: Exception) {
            if (isConnectionRefused(e)) {
                throw AppNotRunningException()
            }
            throw CliClientException("Request failed: ${e.message}", e)
        }

    private fun isConnectionRefused(e: Exception): Boolean {
        val message = e.message ?: ""
        return message.contains("Connection refused", ignoreCase = true) ||
            message.contains("ConnectException", ignoreCase = true)
    }

    override fun close() {
        httpClient.close()
    }
}
