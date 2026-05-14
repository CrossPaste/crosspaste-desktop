package com.crosspaste.net

import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.platform.Platform
import com.crosspaste.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

class DesktopNetworkProfileService(
    private val platform: Platform,
    private val notificationManager: NotificationManager,
) : NetworkProfileService {

    private val logger = KotlinLogging.logger {}

    override suspend fun getCurrentProfile(): NetworkProfile {
        if (!platform.isWindows()) {
            return NetworkProfile.NOT_APPLICABLE
        }
        return withContext(ioDispatcher) { runPowerShellDetection() }
    }

    private fun runPowerShellDetection(): NetworkProfile =
        runCatching {
            val command =
                listOf(
                    "powershell.exe",
                    "-NoProfile",
                    "-NonInteractive",
                    "-Command",
                    "Get-NetConnectionProfile | Select-Object -ExpandProperty NetworkCategory",
                )
            val process =
                ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start()
            try {
                val finished = process.waitFor(POWERSHELL_TIMEOUT.inWholeSeconds, TimeUnit.SECONDS)
                if (!finished) {
                    logger.warn { "Get-NetConnectionProfile timed out" }
                    return@runCatching NetworkProfile.UNKNOWN
                }
                val output = process.inputStream.bufferedReader().readText()
                parseProfileOutput(output)
            } finally {
                process.destroyForcibly()
            }
        }.getOrElse { e ->
            logger.warn(e) { "Failed to detect Windows network profile" }
            NetworkProfile.UNKNOWN
        }

    private fun parseProfileOutput(output: String): NetworkProfile {
        val categories =
            output
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toList()

        if (categories.isEmpty()) {
            return NetworkProfile.UNKNOWN
        }
        // Pick the most restrictive: if any active interface is Public, treat the host as Public.
        return when {
            categories.any { it.equals("Public", ignoreCase = true) } -> NetworkProfile.PUBLIC
            categories.any { it.equals("Private", ignoreCase = true) } -> NetworkProfile.PRIVATE
            categories.any {
                it.equals("DomainAuthenticated", ignoreCase = true) ||
                    it.equals("Domain", ignoreCase = true)
            } -> NetworkProfile.DOMAIN_AUTHENTICATED
            else -> NetworkProfile.UNKNOWN
        }
    }

    override fun openNetworkSettings() {
        if (!platform.isWindows()) {
            return
        }
        runCatching {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI(MS_SETTINGS_NETWORK))
                return
            }
            ProcessBuilder("cmd", "/c", "start", MS_SETTINGS_NETWORK).start()
        }.onFailure { e ->
            logger.warn(e) { "Failed to open Windows network settings" }
            notificationManager.sendNotification(
                title = { it.getText("failed_to_open_browser") },
                message = { MS_SETTINGS_NETWORK },
                messageType = MessageType.Error,
            )
        }
    }

    companion object {
        private const val MS_SETTINGS_NETWORK = "ms-settings:network"

        private val POWERSHELL_TIMEOUT = 5.seconds
    }
}
