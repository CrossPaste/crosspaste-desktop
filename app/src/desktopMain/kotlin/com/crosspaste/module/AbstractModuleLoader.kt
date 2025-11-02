package com.crosspaste.module

import com.crosspaste.net.ResourcesClient
import com.crosspaste.utils.RetryUtils
import com.crosspaste.utils.getCodecsUtils
import com.crosspaste.utils.getFileUtils
import io.github.oshai.kotlinlogging.KLogger
import io.ktor.utils.io.jvm.javaio.*
import okio.Path
import java.time.Duration
import java.time.Instant

abstract class AbstractModuleLoader : ModuleLoader {

    abstract val logger: KLogger

    override val retryUtils = RetryUtils

    override val fileUtils = getFileUtils()

    override val codecsUtils = getCodecsUtils()

    abstract val resourcesClient: ResourcesClient

    override suspend fun downloadModule(
        url: String,
        path: Path,
    ): Boolean {
        fileUtils.deleteFile(path)
        logger.info { "Downloading: $url" }

        return resourcesClient.request(url).getOrNull()?.let { response ->
            val contentLength = response.getContentLength()
            var bytesRead = 0L
            var lastLogTime = Instant.EPOCH
            var lastLoggedProgress = -1L

            response.getBody().toInputStream().use { input ->
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
        } ?: false
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
