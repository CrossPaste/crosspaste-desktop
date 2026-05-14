package com.crosspaste.net

import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.platform.Platform
import com.crosspaste.utils.ioDispatcher
import com.crosspaste.utils.namedScope
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.net.URI
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

class DesktopNetworkProfileService(
    private val configManager: DesktopConfigManager,
    private val notificationManager: NotificationManager,
    private val platform: Platform,
    private val scope: CoroutineScope = namedScope(ioDispatcher, "DesktopNetworkProfileService"),
) : NetworkProfileService {

    private val logger = KotlinLogging.logger {}

    private val _diagnosis = MutableStateFlow(NetworkDiagnosis.NOT_APPLICABLE)
    override val diagnosis: StateFlow<NetworkDiagnosis> = _diagnosis.asStateFlow()

    override val isWarningDismissed: StateFlow<Boolean> =
        combine(
            _diagnosis,
            configManager.config.map { it.networkBlockingDismissedFingerprint }.distinctUntilChanged(),
        ) { current, storedFingerprint ->
            storedFingerprint.isNotEmpty() && storedFingerprint == current.fingerprint()
        }.stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, false)

    init {
        if (platform.isWindows()) {
            scope.launch {
                kotlinx.coroutines.delay(STARTUP_DELAY)
                while (isActive) {
                    runDetection()
                    kotlinx.coroutines.delay(REFRESH_INTERVAL)
                }
            }
        }

        // When the diagnosis fingerprint diverges from the stored dismissed fingerprint
        // (e.g. user fixed the network, or moved to a different blocking state), forget
        // the dismissal so a future blocking event surfaces a fresh banner.
        _diagnosis
            .onEach { current ->
                val stored = configManager.getCurrentConfig().networkBlockingDismissedFingerprint
                if (stored.isNotEmpty() && stored != current.fingerprint()) {
                    configManager.updateConfig("networkBlockingDismissedFingerprint", "")
                }
            }.launchIn(scope)
    }

    override suspend fun refresh() {
        if (!platform.isWindows()) {
            _diagnosis.value = NetworkDiagnosis.NOT_APPLICABLE
            return
        }
        withContext(ioDispatcher) { runDetection() }
    }

    override fun dismissWarning() {
        val current = _diagnosis.value
        if (!current.isLikelyBlocking()) return
        configManager.updateConfig("networkBlockingDismissedFingerprint", current.fingerprint())
    }

    override fun showWarning() {
        configManager.updateConfig("networkBlockingDismissedFingerprint", "")
    }

    private fun runDetection() {
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
                    _diagnosis.value = NetworkDiagnosis(NetworkProfile.UNKNOWN, mDnsAllowed = false)
                    return@runCatching
                }
                val bytes = process.inputStream.readBytes()
                val output = decodeProcessOutput(bytes)
                logger.info { "Network diagnosis raw output: <<<$output>>>" }
                val parsed = parseDiagnosisOutput(output)
                logger.info { "Network diagnosis parsed: $parsed" }
                _diagnosis.value = parsed
            } finally {
                process.destroyForcibly()
            }
        }.onFailure { e ->
            logger.warn(e) { "Failed to run Windows network diagnosis" }
            _diagnosis.value = NetworkDiagnosis(NetworkProfile.UNKNOWN, mDnsAllowed = false)
        }
    }

    // PowerShell 5.1 redirects stdout in inconsistent encodings depending on the host: sometimes
    // UTF-16 LE (with or without BOM), sometimes the legacy ANSI codepage, sometimes UTF-8. The
    // detection output is pure ASCII ("PROFILE=...", "MDNS_ALLOWED=..."), so try several decoders
    // and pick the first one that produces our marker.
    private fun decodeProcessOutput(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val candidates =
            listOf(
                Charsets.UTF_8,
                Charsets.UTF_16LE,
                Charsets.UTF_16BE,
                Charset.defaultCharset(),
            )
        for (charset in candidates) {
            val decoded = String(bytes, charset).trimStart('﻿')
            if (decoded.contains(KEY_PROFILE)) {
                logger.info { "Network diagnosis decoded with $charset" }
                return decoded
            }
        }
        // Nothing matched — log all candidates so we can diagnose the actual encoding.
        candidates.forEach { charset ->
            logger.warn {
                "Network diagnosis decode attempt with $charset: <<<${String(bytes, charset)}>>>"
            }
        }
        return String(bytes, Charsets.UTF_8)
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

        private val STARTUP_DELAY = 3.seconds

        private val REFRESH_INTERVAL = 60.seconds

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
