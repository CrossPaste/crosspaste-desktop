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

    override suspend fun diagnose(): NetworkDiagnosis {
        if (!platform.isWindows()) {
            return NetworkDiagnosis.NOT_APPLICABLE
        }
        return withContext(ioDispatcher) { runDetection() }
    }

    private fun runDetection(): NetworkDiagnosis =
        runCatching {
            val command =
                listOf(
                    "powershell.exe",
                    "-NoProfile",
                    "-NonInteractive",
                    "-Command",
                    POWERSHELL_SCRIPT,
                )
            val process =
                ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start()
            try {
                val finished = process.waitFor(POWERSHELL_TIMEOUT.inWholeSeconds, TimeUnit.SECONDS)
                if (!finished) {
                    logger.warn { "Network diagnosis PowerShell timed out" }
                    return@runCatching NetworkDiagnosis(NetworkProfile.UNKNOWN, mDnsAllowed = false)
                }
                val output = process.inputStream.bufferedReader(Charsets.UTF_8).readText()
                logger.info { "Network diagnosis raw output: <<<$output>>>" }
                val diagnosis = parseDiagnosisOutput(output)
                logger.info { "Network diagnosis parsed: $diagnosis" }
                diagnosis
            } finally {
                process.destroyForcibly()
            }
        }.getOrElse { e ->
            logger.warn(e) { "Failed to run Windows network diagnosis" }
            NetworkDiagnosis(NetworkProfile.UNKNOWN, mDnsAllowed = false)
        }

    private fun parseDiagnosisOutput(output: String): NetworkDiagnosis {
        var profile = NetworkProfile.UNKNOWN
        var mDnsAllowed = false
        output
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { line ->
                when {
                    line.startsWith(KEY_PROFILE) -> {
                        profile = parseProfile(line.removePrefix(KEY_PROFILE).trim())
                    }
                    line.startsWith(KEY_MDNS_ALLOWED) -> {
                        mDnsAllowed =
                            line
                                .removePrefix(KEY_MDNS_ALLOWED)
                                .trim()
                                .equals("True", ignoreCase = true)
                    }
                }
            }
        return NetworkDiagnosis(profile, mDnsAllowed)
    }

    private fun parseProfile(value: String): NetworkProfile =
        when {
            value.equals("Public", ignoreCase = true) -> NetworkProfile.PUBLIC
            value.equals("Private", ignoreCase = true) -> NetworkProfile.PRIVATE
            value.equals("DomainAuthenticated", ignoreCase = true) ||
                value.equals("Domain", ignoreCase = true) -> NetworkProfile.DOMAIN_AUTHENTICATED
            else -> NetworkProfile.UNKNOWN
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

        private const val KEY_PROFILE = "PROFILE="

        private const val KEY_MDNS_ALLOWED = "MDNS_ALLOWED="

        private val POWERSHELL_TIMEOUT = 5.seconds

        // Returns two key=value lines:
        //   PROFILE=<Public|Private|DomainAuthenticated|Unknown>
        //   MDNS_ALLOWED=<True|False>
        //
        // The profile is reduced to the most restrictive across active interfaces (Public wins).
        // mDNS is considered "allowed" when at least one enabled inbound Allow rule whose Name
        // starts with `mDNS` is scoped to the active profile (or to Any).
        // Forces UTF-8 output so the JVM can decode it reliably across non-English Windows locales.
        private val POWERSHELL_SCRIPT =
            """
            ${'$'}ErrorActionPreference = 'SilentlyContinue'
            [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
            ${'$'}OutputEncoding = [System.Text.Encoding]::UTF8

            ${'$'}category = 'Unknown'
            ${'$'}cats = @(Get-NetConnectionProfile | Select-Object -ExpandProperty NetworkCategory)
            if (${'$'}cats -contains 'Public') { ${'$'}category = 'Public' }
            elseif (${'$'}cats -contains 'Private') { ${'$'}category = 'Private' }
            elseif (${'$'}cats -contains 'DomainAuthenticated') { ${'$'}category = 'DomainAuthenticated' }

            ${'$'}mDnsAllowed = ${'$'}false
            ${'$'}rules = Get-NetFirewallRule -Name 'mDNS*' -Direction Inbound -Enabled True -ErrorAction SilentlyContinue
            foreach (${'$'}rule in ${'$'}rules) {
                if (${'$'}rule.Action -ne 'Allow') { continue }
                ${'$'}p = "${'$'}(${'$'}rule.Profile)"
                if (${'$'}p -match '\bAny\b' -or ${'$'}p -match "\b${'$'}category\b") {
                    ${'$'}mDnsAllowed = ${'$'}true
                    break
                }
            }

            Write-Output "PROFILE=${'$'}category"
            Write-Output "MDNS_ALLOWED=${'$'}mDnsAllowed"
            """.trimIndent()
    }
}
