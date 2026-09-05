package com.crosspaste.net

import com.crosspaste.app.AttentionSurface
import com.crosspaste.app.UserAttentionService
import com.crosspaste.app.attentionOn
import com.crosspaste.app.launchWhileAttentive
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.platform.Platform
import com.crosspaste.platform.windows.api.WindowsNetworkApi
import com.crosspaste.utils.ioDispatcher
import com.crosspaste.utils.namedScope
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds

class DesktopNetworkProfileService(
    private val configManager: DesktopConfigManager,
    private val notificationManager: NotificationManager,
    private val platform: Platform,
    private val userAttentionService: UserAttentionService,
    private val scope: CoroutineScope = namedScope(ioDispatcher, "DesktopNetworkProfileService"),
    private val detect: () -> NetworkDiagnosis = ::queryWindowsDiagnosis,
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
        }.stateIn(scope, SharingStarted.Eagerly, false)

    private val _isWarningDialogVisible = MutableStateFlow(false)
    override val isWarningDialogVisible: StateFlow<Boolean> = _isWarningDialogVisible.asStateFlow()

    init {
        if (platform.isWindows()) {
            // Gate polling on the main window — that's the only surface that
            // can render the warning dialog (`NetworkWarningDialogHost`) or
            // the menu-bar warning entry, so polling while no one can see
            // the result wastes COM enumerations every 60s. The 3s startup
            // delay is preserved so we don't race the rest of init even if
            // the main window comes up immediately.
            scope.launch {
                delay(STARTUP_DELAY)
                launchWhileAttentive(
                    attention = userAttentionService.attentionOn(AttentionSurface.MAIN_WINDOW),
                    interval = REFRESH_INTERVAL,
                ) { runDetection() }
            }
        }
    }

    override suspend fun refresh() {
        if (!platform.isWindows()) {
            _diagnosis.value = NetworkDiagnosis.NOT_APPLICABLE
            return
        }
        withContext(ioDispatcher) { runDetection() }
    }

    override fun dismissWarning() {
        _isWarningDialogVisible.value = false
        val current = _diagnosis.value
        if (!current.isLikelyBlocking()) return
        configManager.updateConfig("networkBlockingDismissedFingerprint", current.fingerprint())
    }

    override fun showWarning() {
        _isWarningDialogVisible.value = true
    }

    private fun runDetection() {
        runCatching { detect() }
            .onSuccess { diagnosis ->
                logger.info { "Network diagnosis: $diagnosis" }
                publishDetected(diagnosis)
            }.onFailure { e ->
                logger.warn(e) { "Failed to run Windows network diagnosis" }
                _diagnosis.value = NetworkDiagnosis(NetworkProfile.UNKNOWN, mDnsAllowed = null)
            }
    }

    /**
     * Publishes a real detection result and reconciles it with the persisted dismissal.
     *
     * Only a conclusive detection may invalidate the stored fingerprint. The
     * `NOT_APPLICABLE` placeholder the flow starts with, and the `UNKNOWN` / `null`
     * that `WindowsNetworkApi.query()` returns when a COM, profile or firewall query
     * fails, are not network states the user could have dismissed. Treating them as a
     * change would wipe the dismissal on startup or on a transient firewall read
     * failure and re-open the dialog once the same blocking state is read back.
     *
     * The dialog is surfaced synchronously here rather than from a `combine` over
     * `_diagnosis` and [isWarningDismissed]: those two flows update independently, so
     * the combine could observe the new blocking diagnosis together with a stale
     * `dismissed = false` and pop the dialog for a state the user already dismissed.
     */
    private fun publishDetected(diagnosis: NetworkDiagnosis) {
        val fingerprint = diagnosis.fingerprint()
        val stored = configManager.getCurrentConfig().networkBlockingDismissedFingerprint
        // The user fixed the network or moved to a different blocking state: forget the
        // dismissal so the next blocking event surfaces a fresh warning.
        if (diagnosis.isConclusive() && stored.isNotEmpty() && stored != fingerprint) {
            configManager.updateConfig("networkBlockingDismissedFingerprint", "")
        }

        val previous = _diagnosis.value
        _diagnosis.value = diagnosis

        // Auto-surface the dialog the first time a new blocking state is detected.
        val isNewState = previous.fingerprint() != fingerprint
        if (diagnosis.isLikelyBlocking() && isNewState && stored != fingerprint) {
            _isWarningDialogVisible.value = true
        }
    }

    override fun openNetworkSettings() {
        if (!platform.isWindows()) {
            return
        }
        runCatching {
            // Jumps straight to Control Panel -> Network and Sharing Center ->
            // Advanced sharing settings, which exposes the Network Discovery
            // toggle and per-profile sharing options — exactly the controls the
            // user needs. The classic Control Panel surface is still shipped
            // on Windows 10/11 even though Microsoft has been gradually moving
            // pages into the Settings app.
            ProcessBuilder(
                "control.exe",
                "/name",
                "Microsoft.NetworkAndSharingCenter",
                "/page",
                "Advanced",
            ).start()
        }.onFailure { e ->
            logger.warn(e) { "Failed to open Windows network settings" }
            notificationManager.sendNotification(
                title = { it.getText("failed_to_open_browser") },
                message = { OPEN_NETWORK_SETTINGS_COMMAND },
                messageType = MessageType.Error,
            )
        }
    }

    companion object {
        private fun queryWindowsDiagnosis(): NetworkDiagnosis {
            val snapshot = WindowsNetworkApi.query()
            return NetworkDiagnosis(snapshot.profile, snapshot.mDnsAllowed)
        }

        private const val OPEN_NETWORK_SETTINGS_COMMAND =
            "control.exe /name Microsoft.NetworkAndSharingCenter /page Advanced"

        private val STARTUP_DELAY = 3.seconds

        private val REFRESH_INTERVAL = 60.seconds
    }
}
