package com.crosspaste.image

import com.crosspaste.app.AppFileType
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.utils.PlatformLock
import com.crosspaste.utils.extension
import com.crosspaste.utils.getFileUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.util.collections.*
import okio.Path

abstract class AbstractFileExtImageLoader(
    private val userDataPathProvider: UserDataPathProvider,
) : ConcurrentLoader<Path, Path>, FileExtImageLoader {

    private val logger = KotlinLogging.logger {}

    override val lockMap: ConcurrentMap<String, PlatformLock> = ConcurrentMap()

    private val fileUtils = getFileUtils()

    override fun resolve(
        key: String,
        value: Path,
    ): Path {
        return userDataPathProvider.resolve("$key.png", AppFileType.FILE_EXT_ICON)
    }

    override fun exist(result: Path): Boolean {
        return fileUtils.existFile(result)
    }

    override fun loggerWarning(
        value: Path,
        e: Exception,
    ) {
        logger.warn { "Failed to load icon for file extension: $value" }
    }

    override fun convertToKey(value: Path): String {
        return value.extension
    }
}
