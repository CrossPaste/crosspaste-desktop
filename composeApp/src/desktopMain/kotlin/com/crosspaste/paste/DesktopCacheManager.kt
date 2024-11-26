package com.crosspaste.paste

import com.crosspaste.dto.pull.PullFilesKey
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.presist.FilesIndex
import com.crosspaste.realm.paste.PasteRealm
import com.crosspaste.utils.DateUtils
import com.crosspaste.utils.getDateUtils
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.TimeUnit

class DesktopCacheManager(
    override val pasteRealm: PasteRealm,
    override val userDataPathProvider: UserDataPathProvider,
) : CacheManager {

    private val logger = KotlinLogging.logger {}

    override val dateUtils: DateUtils = getDateUtils()

    private val filesIndexCache: LoadingCache<PullFilesKey, FilesIndex> =
        CacheBuilder.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build(
                object : CacheLoader<PullFilesKey, FilesIndex>() {
                    override fun load(key: PullFilesKey): FilesIndex {
                        return loadKey(key)
                    }
                },
            )

    override suspend fun getFilesIndex(pullFilesKey: PullFilesKey): FilesIndex? {
        return try {
            filesIndexCache.get(pullFilesKey)
        } catch (e: Exception) {
            logger.warn(e) { "getFilesIndex failed: $pullFilesKey" }
            null
        }
    }
}
