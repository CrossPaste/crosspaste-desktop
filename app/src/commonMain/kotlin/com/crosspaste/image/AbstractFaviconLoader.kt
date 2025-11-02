package com.crosspaste.image

import com.crosspaste.app.AppFileType
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.utils.ConcurrentLoader
import com.crosspaste.utils.StripedMutex
import com.crosspaste.utils.getFileUtils
import io.github.oshai.kotlinlogging.KLogger
import io.ktor.http.*
import okio.Path

abstract class AbstractFaviconLoader(
    private val userDataPathProvider: UserDataPathProvider,
) : ConcurrentLoader<String, Path>,
    FaviconLoader {

    protected abstract val logger: KLogger

    protected val fileUtils = getFileUtils()

    override val mutex = StripedMutex()

    private fun getGoogleIconUrl(host: String): String =
        "https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=http://$host&size=256"

    private fun getDefaultIcoUrl(host: String): String = "https://$host/favicon.ico"

    abstract suspend fun saveIco(
        url: String,
        path: Path,
    ): Path?

    override fun resolve(
        key: String,
        value: String,
    ): Path = userDataPathProvider.resolve("$key.ico", AppFileType.FAVICON)

    override fun exist(result: Path): Boolean = fileUtils.existFile(result)

    override fun loggerWarning(
        value: String,
        e: Throwable,
    ) {
        logger.warn(e) { "Failed to get favicon for $value" }
    }

    override suspend fun save(
        key: String,
        value: String,
        result: Path,
    ) {
        saveIco(getDefaultIcoUrl(key), result)
            ?: saveIco(getGoogleIconUrl(key), result)
    }

    override fun convertToKey(value: String): String = Url(value).host
}
