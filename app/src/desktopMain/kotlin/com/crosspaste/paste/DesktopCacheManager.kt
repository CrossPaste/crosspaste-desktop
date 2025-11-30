package com.crosspaste.paste

import com.crosspaste.db.paste.PasteDao
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.presist.FilesIndex
import com.crosspaste.utils.DateUtils
import com.crosspaste.utils.getDateUtils
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.TimeUnit

class DesktopCacheManager(
    override val pasteDao: PasteDao,
    override val userDataPathProvider: UserDataPathProvider,
) : CacheManager {

    private val logger = KotlinLogging.logger {}

    override val dateUtils: DateUtils = getDateUtils()

    private val filesIndexCache: LoadingCache<Long, FilesIndex?> =
        Caffeine
            .newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build(::loadKey)

    override suspend fun getFilesIndex(id: Long): FilesIndex? =
        runCatching {
            filesIndexCache.get(id)
        }.onFailure { e ->
            logger.warn(e) { "getFilesIndex failed: $id" }
        }.getOrNull()
}
