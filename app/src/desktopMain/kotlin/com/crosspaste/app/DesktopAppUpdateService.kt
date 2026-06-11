package com.crosspaste.app

import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.ui.base.UISupport
import com.crosspaste.utils.ioDispatcher
import com.crosspaste.utils.namedScope
import dev.hydraulic.conveyor.control.SoftwareUpdateController
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.z4kn4fein.semver.Version
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities

private const val MICROSOFT_STORE_URI = "ms-windows-store://pdp?productId=9P6X7D7DMCCR"

class DesktopAppUpdateService(
    appInfo: AppInfo,
    private val appUrls: AppUrls,
    private val uiSupport: UISupport,
    private val notificationManager: NotificationManager,
    private val metadataFetcher: UpdateMetadataFetcher,
    private val windowsZipUpdater: WindowsZipUpdater,
    private val appWindowManager: DesktopAppWindowManager,
) : AppUpdateService {

    private val logger = KotlinLogging.logger {}

    private val controller: SoftwareUpdateController? = SoftwareUpdateController.getInstance()

    private val coroutineScope = namedScope(ioDispatcher, "DesktopAppUpdateService")

    private val _currentVersion: MutableStateFlow<Version> =
        MutableStateFlow(
            Version.parse(appInfo.appVersion),
        )

    override val currentVersion: StateFlow<Version> = _currentVersion

    private val _lastVersion: MutableStateFlow<Version?> = MutableStateFlow(null)

    override val lastVersion: StateFlow<Version?> = _lastVersion

    private var checkUpdate: Job? = null

    private fun startPeriodicUpdateCheck(): Job =
        coroutineScope.launch {
            while (true) {
                checkForUpdate()
                delay(TimeUnit.HOURS.toMillis(2))
            }
        }

    override suspend fun checkForUpdate() {
        _lastVersion.value = readLastVersion()
    }

    override fun existNewVersion(): Flow<Boolean> =
        combine(currentVersion, lastVersion) { current, last ->
            last?.let { it > current } == true
        }

    override fun start() {
        checkUpdate = startPeriodicUpdateCheck()
    }

    override fun stop() {
        checkUpdate?.cancel()
    }

    override fun tryTriggerUpdate() {
        val hasNewVersion = lastVersion.value?.let { it > currentVersion.value } ?: false

        if (!hasNewVersion) {
            notificationManager.sendNotification(
                title = { it.getText("no_new_version_available") },
                messageType = MessageType.Info,
            )
            return
        }

        when (windowsZipUpdater.channel) {
            // Store-installed apps can only be updated by the Store; deep-link there.
            WindowsUpdateChannel.STORE -> {
                notificationManager.sendNotification(
                    title = { it.getText("new_version_available") },
                    messageType = MessageType.Info,
                )
                openMicrosoftStore()
            }
            // Portable zip: re-arm and surface the blocking update dialog in the main
            // window; the user confirms the download there. A manual check never starts
            // the download on its own.
            WindowsUpdateChannel.PORTABLE_ZIP -> {
                windowsZipUpdater.resetUpdatePrompt()
                appWindowManager.showMainWindow(WindowTrigger.MENU)
            }
            // Conveyor installer (Windows) handles its own UI; non-Windows falls
            // through to Conveyor's Sparkle controller, then the browser.
            else -> {
                val updateTriggered = controller?.tryTriggerUpdateUI() ?: false
                if (!updateTriggered) {
                    uiSupport.openCrossPasteWebInBrowser(path = "download")
                }
            }
        }
    }

    private fun openMicrosoftStore() {
        runCatching {
            ProcessBuilder(listOf("cmd", "/c", "start", "", MICROSOFT_STORE_URI)).start()
        }.onFailure { e ->
            logger.error(e) { "Failed to open Microsoft Store" }
            uiSupport.openCrossPasteWebInBrowser(path = "download")
        }
    }

    private fun SoftwareUpdateController.tryTriggerUpdateUI(): Boolean =
        try {
            var result = false
            val trigger =
                Runnable {
                    result =
                        when (canTriggerUpdateCheckUI()) {
                            SoftwareUpdateController.Availability.AVAILABLE -> {
                                triggerUpdateCheckUI()
                                true
                            }
                            else -> {
                                logger.warn {
                                    "SoftwareUpdateController is not available, cannot trigger update check UI"
                                }
                                false
                            }
                        }
                }
            // Conveyor requires the trigger to run on the EDT, but the menu click that
            // gets us here already runs there — invokeAndWait from the EDT throws
            // java.lang.Error, which the catch below would not even see.
            if (SwingUtilities.isEventDispatchThread()) {
                trigger.run()
            } else {
                SwingUtilities.invokeAndWait(trigger)
            }
            result
        } catch (e: Throwable) {
            logger.error(e) { "Failed to trigger update check UI" }
            false
        }

    private suspend fun readLastVersion(): Version? {
        // Honor the portable-zip updater's test override (CROSSPASTE_UPDATE_BASE_URL):
        // a test bucket advertising a newer version then drives the "new version
        // available" banner too, and that base stays the single source (no
        // crosspaste.com fallback). Normal builds check GitHub first and fall back
        // to crosspaste.com's version API when GitHub is unreachable.
        val override = windowsZipUpdater.overrideMetadataUrl
        return metadataFetcher
            .fetchLatest(
                metadataPropertiesUrl = override ?: appUrls.checkMetadataUrl,
                versionApiUrl = if (override != null) null else DesktopAppUrls.versionApiUrl,
            )?.let { Version.parse(it.version) }
    }
}
