package com.clipevery.net

import com.clipevery.app.AppFileType
import com.clipevery.path.DesktopPathProvider
import com.clipevery.path.PathProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.ProxySelector
import java.net.URL
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.time.Duration

object DesktopFaviconLoader : FaviconLoader {

    private val logger = KotlinLogging.logger {}

    private val pathProvider: PathProvider = DesktopPathProvider

    private val desktopProxy = DesktopProxy

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

        val proxy = desktopProxy.getProxy(uri)

        (proxy.address() as InetSocketAddress?).let { address ->
            try {
                val client =
                    HttpClient.newBuilder()
                        .proxy(ProxySelector.of(address))
                        .build()

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
                    return@saveIco path
                }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to save favicon for $url" }
            }
        }
        return null
    }

    override fun getFaviconPath(url: String): Path? {
        try {
            Url(url).host.let {
                val path = pathProvider.resolve("$it.ico", AppFileType.FAVICON)
                val file = path.toFile()
                if (file.exists()) {
                    return@getFaviconPath path
                }

                saveIco(getGoogleIconUrl(it), path)?.let {
                    return@getFaviconPath path
                } ?: run {
                    saveIco(getDefaultIcoUrl(it), path)?.let {
                        return@getFaviconPath path
                    }
                }
                return null
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to get favicon for $url" }
            return null
        }
    }
}
