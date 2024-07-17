package com.crosspaste.icon

import com.crosspaste.app.AppFileType
import com.crosspaste.net.DesktopProxy
import com.crosspaste.path.DesktopPathProvider
import com.crosspaste.path.PathProvider
import com.crosspaste.utils.ConcurrentPlatformMap
import com.crosspaste.utils.PlatformLock
import com.crosspaste.utils.createConcurrentPlatformMap
import io.github.oshai.kotlinlogging.KotlinLogging
import okio.Path
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.ProxySelector
import java.net.URL
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

object DesktopFaviconLoader : ConcurrentLoader, FaviconLoader {

    private val logger = KotlinLogging.logger {}

    private val pathProvider: PathProvider = DesktopPathProvider

    override val lockMap: ConcurrentPlatformMap<Path, PlatformLock> = createConcurrentPlatformMap()

    private fun getGoogleIconUrl(host: String): String {
        return "https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=http://$host&size=32"
    }

    private fun getDefaultIcoUrl(host: String): String {
        return "https://$host/favicon.ico"
    }

    private fun saveIco(
        url: String,
        path: Path,
    ): Path? {
        val httpsUrl = URL(url)

        val uri = httpsUrl.toURI()

        val proxy = DesktopProxy.getProxy(uri)

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
                FileOutputStream(path.toFile()).use { output ->
                    response.body().use { input ->
                        input.copyTo(output)
                    }
                }
                return path
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to save favicon for $url" }
        }
        return null
    }

    override fun resolve(key: String): Path {
        return pathProvider.resolve("$key.ico", AppFileType.FAVICON)
    }

    override fun loggerWarning(
        key: String,
        e: Exception,
    ) {
        logger.warn(e) { "Failed to get favicon for $key" }
    }

    override fun save(
        key: String,
        path: Path,
    ) {
        saveIco(getDefaultIcoUrl(key), path)?.let {
            return
        } ?: run {
            saveIco(getGoogleIconUrl(key), path)
        }
    }
}
