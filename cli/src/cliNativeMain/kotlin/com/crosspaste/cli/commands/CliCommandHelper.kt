package com.crosspaste.cli.commands

import com.crosspaste.cli.api.AppNotRunningException
import com.crosspaste.cli.api.CLI_API_VERSION
import com.crosspaste.cli.api.CliClient
import com.crosspaste.cli.platform.CliConfigReader
import com.crosspaste.cli.platform.createNativePlatformPathProvider
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

val cliJson =
    Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

/**
 * Runs [block] with a connected [CliClient]. Every command goes through this:
 * it discovers the app's socket, surfaces "app not running" uniformly, and
 * warns (without failing) when the CLI and app disagree on the API version.
 */
fun CliktCommand.runCli(block: suspend (CliClient) -> Unit) {
    runBlocking {
        var client: CliClient? = null
        try {
            client = CliClient(CliConfigReader(createNativePlatformPathProvider()))
            if (client.hasApiVersionMismatch()) {
                echo(
                    "Warning: CLI API version $CLI_API_VERSION does not match the running " +
                        "app's version ${client.endpoint.apiVersion}; consider updating both.",
                    err = true,
                )
            }
            block(client)
        } catch (e: ProgramResult) {
            throw e
        } catch (e: AppNotRunningException) {
            echo("Error: ${e.message}", err = true)
            throw ProgramResult(1)
        } catch (e: Exception) {
            echo("Error: ${e.message}", err = true)
            throw ProgramResult(1)
        } finally {
            client?.close()
        }
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
