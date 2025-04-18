package com.crosspaste.image

import com.crosspaste.app.AppFileType
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.utils.getFileUtils
import io.github.oshai.kotlinlogging.KLogger
import io.ktor.http.*
import kotlinx.coroutines.sync.Mutex
import okio.Path

abstract class AbstractFaviconLoader(
    private val userDataPathProvider: UserDataPathProvider,
) : ConcurrentLoader<String, Path>, FaviconLoader {

    protected abstract val logger: KLogger

    protected val fileUtils = getFileUtils()

    override val mutex = Mutex()

    private fun getGoogleIconUrl(host: String): String {
        return "https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=http://$host&size=32"
    }

    private fun getDefaultIcoUrl(host: String): String {
        return "https://$host/favicon.ico"
    }

    abstract fun saveIco(
        url: String,
        path: Path,
    ): Path?

    override fun resolve(
        key: String,
        value: String,
    ): Path {
        return userDataPathProvider.resolve("$key.ico", AppFileType.FAVICON)
    }

    override fun exist(result: Path): Boolean {
        return fileUtils.existFile(result)
    }

    override fun loggerWarning(
        value: String,
        e: Throwable,
    ) {
        logger.warn(e) { "Failed to get favicon for $value" }
    }

    override fun save(
        key: String,
        value: String,
        result: Path,
    ) {
        saveIco(getDefaultIcoUrl(key), result)
            ?: saveIco(getGoogleIconUrl(key), result)
    }

    override fun convertToKey(value: String): String {
        return Url(value).host
    }
}
