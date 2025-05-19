package com.crosspaste.module

import com.crosspaste.net.DesktopProxy
import com.crosspaste.utils.RetryUtils
import com.crosspaste.utils.getCodecsUtils
import com.crosspaste.utils.getFileUtils
import io.github.oshai.kotlinlogging.KLogger
import okio.Path
import java.net.InetSocketAddress
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant

abstract class AbstractModuleLoader : ModuleLoader {

    abstract val logger: KLogger

    override val retryUtils = RetryUtils

    override val fileUtils = getFileUtils()

    override val codecsUtils = getCodecsUtils()

    override fun downloadModule(
        url: String,
        path: Path,
    ): Boolean {
        return runCatching {
            fileUtils.deleteFile(path)

            logger.info { "Downloading: $url" }

            val uri = URI(url)
            val proxy = DesktopProxy.getProxy(uri)

            val clientBuilder =
                HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)

            (proxy.address() as? InetSocketAddress)?.let { address ->
                logger.info { "Using proxy: $address" }
                clientBuilder.proxy(ProxySelector.of(address))
            }

            val client = clientBuilder.build()
            val request =
                HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofMinutes(30))
                    .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())

            if (response.statusCode() == 200) {
                val contentLength =
                    response.headers()
                        .firstValue("Content-Length")
                        .orElse("-1").toLong()
                var bytesRead = 0L
                var lastLogTime = Instant.EPOCH
                var lastLoggedProgress = -1L

                response.body().use { input ->
                    path.toFile().outputStream().buffered().use { output ->
                        val buffer = ByteArray(8192) // 8KB buffer
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            bytesRead += read

                            val currentTime = Instant.now()
                            val progress = if (contentLength > 0) bytesRead * 100 / contentLength else -1

                            if (shouldLogProgress(currentTime, progress, lastLogTime, lastLoggedProgress)) {
                                logger.info { "Downloaded: $bytesRead bytes ($progress%)" }
                                lastLogTime = currentTime
                                lastLoggedProgress = progress
                            }
                        }
                    }
                }
                logger.info { "Download completed: $path" }
                true
            } else {
                logger.error { "Failed to download. Status code: ${response.statusCode()}" }
                false
            }
        }.getOrElse { e ->
            logger.error(e) { "Error during download" }
            false
        }
    }

    private fun shouldLogProgress(
        currentTime: Instant,
        progress: Long,
        lastLogTime: Instant,
        lastLoggedProgress: Long,
    ): Boolean {
        val logInterval = Duration.ofSeconds(5)
        val timeSinceLastLog = Duration.between(lastLogTime, currentTime)
        val progressDelta = progress - lastLoggedProgress

        return timeSinceLastLog >= logInterval ||
            (progressDelta >= 5 && timeSinceLastLog >= Duration.ofSeconds(1)) ||
            progress == 100L
    }
}
