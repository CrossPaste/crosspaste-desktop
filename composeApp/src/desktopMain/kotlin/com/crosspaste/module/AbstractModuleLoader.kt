package com.crosspaste.module

import com.crosspaste.net.DesktopProxy
import com.crosspaste.utils.getCodecsUtils
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.getRetryUtils
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import okio.Path
import java.net.InetSocketAddress
import java.net.ProxySelector
import java.net.URL
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

abstract class AbstractModuleLoader : ModuleLoader {

    private val logger: KLogger = KotlinLogging.logger {}

    override val retryUtils = getRetryUtils()

    override val fileUtils = getFileUtils()

    override val codecsUtils = getCodecsUtils()

    override fun downloadModule(
        url: String,
        path: Path,
    ): Boolean {
        return try {
            fileUtils.deleteFile(path)

            val httpsUrl = URL(url)
            val uri = httpsUrl.toURI()

            // Get proxy for the given URI
            val proxy = DesktopProxy.getProxy(uri)

            val clientBuilder =
                HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)

            // Apply proxy if available
            (proxy.address() as? InetSocketAddress)?.let { address ->
                clientBuilder.proxy(ProxySelector.of(address))
            }

            val client = clientBuilder.build()

            val request =
                HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofMinutes(30)) // Increased timeout for large files
                    .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())

            if (response.statusCode() == 200) {
                val contentLength =
                    response.headers()
                        .firstValue("Content-Length")
                        .orElse("-1")
                        .toLong()
                var bytesRead = 0L

                response.body().use { input ->
                    path.toFile().outputStream().buffered().use { output ->
                        val buffer = ByteArray(8192) // 8KB buffer
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            bytesRead += read
                            val progress = if (contentLength > 0) bytesRead * 100 / contentLength else -1
                            logger.info { "Downloaded: $bytesRead bytes ($progress%)" }
                        }
                    }
                }
                logger.info { "Download completed: $path" }
                true
            } else {
                logger.error { "Failed to download. Status code: ${response.statusCode()}" }
                false
            }
        } catch (e: Exception) {
            logger.error { "Error during download: ${e.message}" }
            false
        }
    }
}
