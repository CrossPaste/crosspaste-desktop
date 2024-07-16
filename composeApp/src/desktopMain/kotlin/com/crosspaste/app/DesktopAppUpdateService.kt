package com.crosspaste.app

import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.net.DesktopProxy
import com.crosspaste.ui.base.MessageType
import com.crosspaste.ui.base.Toast
import com.crosspaste.ui.base.ToastManager
import com.crosspaste.ui.base.UISupport
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.z4kn4fein.semver.Version
import java.net.InetSocketAddress
import java.net.ProxySelector
import java.net.URL
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class DesktopAppUpdateService(
    appInfo: AppInfo,
    private val copywriter: GlobalCopywriter,
    private val uiSupport: UISupport,
    private val toastManager: ToastManager,
) : AppUpdateService {

    private val logger = KotlinLogging.logger {}

    override var currentVersion: Version = Version.parse(appInfo.appVersion)

    override var lastVersion: Version? = null

    private val desktopProxy = DesktopProxy

    private val downloadUrl = "https://crosspaste.com/download"

    private val checkLastVersionUrl = "https://crosspaste.com/downloadDesktop/lastVersion"

    override fun checkForUpdate() {
        lastVersion = readLastVersion()
    }

    override fun existNewVersion(): Boolean {
        return lastVersion?.let { it > currentVersion } ?: false
    }

    override fun jumpDownload() {
        if (existNewVersion()) {
            uiSupport.openUrlInBrowser(downloadUrl)
        } else {
            toastManager.setToast(
                Toast(
                    messageType = MessageType.Info,
                    message = copywriter.getText("no_new_version_available"),
                ),
            )
        }
    }

    private fun readLastVersion(): Version? {
        val httpsUrl = URL(checkLastVersionUrl)

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
                val versionString = String(response.body().readBytes(), Charsets.UTF_8)
                return Version.parse(versionString)
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to get last version" }
        }
        return null
    }
}
