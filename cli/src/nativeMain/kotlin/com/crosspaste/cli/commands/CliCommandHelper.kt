package com.crosspaste.cli.commands

import com.crosspaste.cli.api.AppNotRunningException
import com.crosspaste.cli.api.CliClient
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import io.ktor.client.call.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.koin.mp.KoinPlatform

const val CLI_VERSION = "1.2.7"

val cliJson =
    Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

fun CliktCommand.runWithClient(block: suspend (CliClient) -> Unit) {
    val client =
        try {
            KoinPlatform.getKoin().get<CliClient>()
        } catch (_: AppNotRunningException) {
            echo("CrossPaste is not running.", err = true)
            throw ProgramResult(1)
        }

    runBlocking {
        client.use {
            try {
                block(it)
            } catch (e: AppNotRunningException) {
                echo("CrossPaste is not running.", err = true)
                throw ProgramResult(1)
            } catch (e: ProgramResult) {
                throw e
            } catch (e: Exception) {
                echo("Error: ${e.message}", err = true)
                throw ProgramResult(1)
            }
        }
    }
}

suspend fun CliktCommand.handleResponse(
    response: HttpResponse,
    onSuccess: suspend (HttpResponse) -> Unit,
) {
    if (response.status == HttpStatusCode.OK) {
        onSuccess(response)
    } else {
        val body = response.body<String>()
        echo("Error: ${response.status} - $body", err = true)
        throw ProgramResult(1)
    }
}

@OptIn(ExperimentalForeignApi::class)
fun formatRelativeTime(epochMillis: Long): String {
    val now = platform.posix.time(null) * 1000L
    val diff = now - epochMillis
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        seconds < 60 -> "${seconds}s ago"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 30 -> "${days}d ago"
        else -> {
            val months = days / 30
            "${months}mo ago"
        }
    }
}

fun formatSize(bytes: Long): String =
    when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "${bytes / 1024}KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)}MB"
        else -> "${bytes / (1024 * 1024 * 1024)}GB"
    }
