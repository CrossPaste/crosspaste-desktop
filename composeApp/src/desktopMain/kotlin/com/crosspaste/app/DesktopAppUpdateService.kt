package com.crosspaste.app

import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.net.DesktopProxy
import com.crosspaste.ui.base.MessageType
import com.crosspaste.ui.base.NotificationManager
import com.crosspaste.ui.base.UISupport
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.z4kn4fein.semver.Version
import java.io.ByteArrayInputStream
import java.net.InetSocketAddress
import java.net.ProxySelector
import java.net.URL
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Properties

class DesktopAppUpdateService(
    appInfo: AppInfo,
    private val appUrls: AppUrls,
    private val copywriter: GlobalCopywriter,
    private val uiSupport: UISupport,
    private val notificationManager: NotificationManager,
) : AppUpdateService {

    private val logger = KotlinLogging.logger {}

    override var currentVersion: Version = Version.parse(appInfo.appVersion)

    override var lastVersion: Version? = null

    private val desktopProxy = DesktopProxy

    override fun checkForUpdate() {
        lastVersion = readLastVersion()
    }

    override fun existNewVersion(): Boolean {
        return lastVersion?.let { it > currentVersion } ?: false
    }

    override fun jumpDownload() {
        if (existNewVersion()) {
            uiSupport.openCrossPasteWebInBrowser(path = "download")
        } else {
            notificationManager.addNotification(
                message = copywriter.getText("no_new_version_available"),
                messageType = MessageType.Info,
            )
        }
    }

    private fun readLastVersion(): Version? {
        val httpsUrl = URL(appUrls.checkMetadataUrl)

        val uri = httpsUrl.toURI()

        val proxy = desktopProxy.getProxy(uri)

        try {
            val builder = HttpClient.newBuilder()

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

            if (response.statusCode() == 200) {
                val bytes = response.body().readBytes()
                val inputStream = ByteArrayInputStream(bytes)
                val properties = Properties()
                properties.load(inputStream)
                properties.getProperty("app.version")?.let { versionString ->
                    return@readLastVersion Version.parse(versionString)
                }
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to get last version" }
        }
        return null
    }
}
