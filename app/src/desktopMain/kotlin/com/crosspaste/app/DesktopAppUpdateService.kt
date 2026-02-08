package com.crosspaste.app

import com.crosspaste.net.ResourcesClient
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.ui.base.UISupport
import com.crosspaste.utils.ioDispatcher
import dev.hydraulic.conveyor.control.SoftwareUpdateController
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.z4kn4fein.semver.Version
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Properties
import java.util.concurrent.TimeUnit

class DesktopAppUpdateService(
    appInfo: AppInfo,
    private val appUrls: AppUrls,
    private val uiSupport: UISupport,
    private val notificationManager: NotificationManager,
    private val resourcesClient: ResourcesClient,
) : AppUpdateService {

    private val logger = KotlinLogging.logger {}

    private val controller: SoftwareUpdateController? = SoftwareUpdateController.getInstance()

    private val coroutineScope = CoroutineScope(ioDispatcher + SupervisorJob())

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

        val updateTriggered = controller?.tryTriggerUpdateUI() ?: false

        if (!updateTriggered) {
            uiSupport.openCrossPasteWebInBrowser(path = "download")
        }
    }

    private fun SoftwareUpdateController.tryTriggerUpdateUI(): Boolean =
        try {
            when (canTriggerUpdateCheckUI()) {
                SoftwareUpdateController.Availability.AVAILABLE -> {
                    triggerUpdateCheckUI()
                    true
                }
                else -> {
                    logger.warn { "SoftwareUpdateController is not available, cannot trigger update check UI" }
                    false
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to trigger update check UI" }
            false
        }

    private suspend fun readLastVersion(): Version? =
        resourcesClient
            .request(
                url = appUrls.checkMetadataUrl,
            ).getOrNull()
            ?.let { response ->
                response.getBody().toInputStream().use { inputStream ->
                    val properties = Properties()
                    properties.load(inputStream)
                    properties.getProperty("app.version")?.let { versionString ->
                        Version.parse(versionString)
                    }
                }
            }
}
