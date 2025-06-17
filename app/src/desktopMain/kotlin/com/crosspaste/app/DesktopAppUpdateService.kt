package com.crosspaste.app

import com.crosspaste.net.DesktopProxy
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.ui.base.UISupport
import com.crosspaste.utils.cpuDispatcher
import dev.hydraulic.conveyor.control.SoftwareUpdateController
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.z4kn4fein.semver.Version
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.net.InetSocketAddress
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Properties
import java.util.concurrent.TimeUnit

class DesktopAppUpdateService(
    appInfo: AppInfo,
    private val appUrls: AppUrls,
    private val uiSupport: UISupport,
    private val notificationManager: NotificationManager,
) : AppUpdateService {

    private val logger = KotlinLogging.logger {}

    private val controller: SoftwareUpdateController? = SoftwareUpdateController.getInstance()

    private val coroutineScope = CoroutineScope(cpuDispatcher)

    private val _currentVersion: MutableStateFlow<Version> =
        MutableStateFlow(
            Version.parse(appInfo.appVersion),
        )

    override val currentVersion: StateFlow<Version> = _currentVersion

    private val _lastVersion: MutableStateFlow<Version?> = MutableStateFlow(null)

    override val lastVersion: StateFlow<Version?> = _lastVersion

    private val desktopProxy = DesktopProxy

    private var checkUpdate: Job? = null

    private fun startPeriodicUpdateCheck(): Job {
        return coroutineScope.launch {
            while (true) {
                checkForUpdate()
                delay(TimeUnit.HOURS.toMillis(2))
            }
        }
    }

    override fun checkForUpdate() {
        _lastVersion.value = readLastVersion()
    }

    override fun existNewVersion(): Flow<Boolean> {
        return combine(currentVersion, lastVersion) { current, last ->
            last?.let { it > current } == true
        }
    }

    override fun start() {
        checkUpdate = startPeriodicUpdateCheck()
    }

    override fun stop() {
        checkUpdate?.cancel()
    }

    override fun tryTriggerUpdate() {
        val last = lastVersion.value
        val current = currentVersion.value
        val nowExistNewVersion = last?.let { it > current } == true

        if (nowExistNewVersion) {
            controller?.let {
                if (it.canTriggerUpdateCheckUI() == SoftwareUpdateController.Availability.AVAILABLE) {
                    it.triggerUpdateCheckUI()
                } else {
                    logger.warn { "SoftwareUpdateController is not available for update check UI" }
                    uiSupport.openCrossPasteWebInBrowser(path = "download")
                }
            } ?: run {
                logger.warn { "SoftwareUpdateController is null, cannot trigger update check UI" }
                uiSupport.openCrossPasteWebInBrowser(path = "download")
            }
        } else {
            notificationManager.sendNotification(
                title = { it.getText("no_new_version_available") },
                messageType = MessageType.Info,
            )
        }
    }

    private fun readLastVersion(): Version? {
        val uri = URI(appUrls.checkMetadataUrl)

        val proxy = desktopProxy.getProxy(uri)

        runCatching {
            val builder =
                HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL) // Enable following redirects

            (proxy.address() as InetSocketAddress?)?.let { address ->
                builder.proxy(ProxySelector.of(address))
            }

            val client = builder.build()

            val request =
                HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(5))
                    .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())

            if (response.statusCode() in 200..299) {
                val bytes = response.body().readBytes()
                val inputStream = ByteArrayInputStream(bytes)
                val properties = Properties()
                properties.load(inputStream)
                properties.getProperty("app.version")?.let { versionString ->
                    return@readLastVersion Version.parse(versionString)
                }
            }
        }.onFailure { e ->
            logger.warn(e) { "Failed to get last version" }
        }
        return null
    }
}
