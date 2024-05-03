package com.clipevery.utils

import com.clipevery.app.AppFileType
import com.clipevery.path.DesktopPathProvider
import com.clipevery.path.PathProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import java.io.FileOutputStream
import java.net.URL
import java.nio.file.Path

actual fun getFaviconUtils(): FaviconUtils {
    return DesktopFaviconUtils
}

object DesktopFaviconUtils : FaviconUtils {

    private val logger = KotlinLogging.logger {}

    private val pathProvider: PathProvider = DesktopPathProvider

    override fun getFaviconPath(url: String): Path? {
        try {
            Url(url).host.let {
                val path = pathProvider.resolve("$it.ico", AppFileType.FAVICON)
                val file = path.toFile()
                if (file.exists()) {
                    return path
                }

                val iconUrl =
                    "https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=http://$it&size=32"
                val input = URL(iconUrl).openStream()

                FileOutputStream(path.toFile()).use { output ->
                    input.copyTo(output)
                }
                return path
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to get favicon for $url" }
            return null
        }
    }
}
