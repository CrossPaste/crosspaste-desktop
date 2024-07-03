package com.crosspaste.clip

import com.crosspaste.clip.item.ClipFiles
import com.crosspaste.dao.clip.ClipDao
import com.crosspaste.dto.pull.PullFilesKey
import com.crosspaste.path.DesktopPathProvider
import com.crosspaste.presist.FilesIndex
import com.crosspaste.presist.FilesIndexBuilder
import com.crosspaste.task.PullFileTaskExecutor
import com.crosspaste.utils.DateUtils
import com.crosspaste.utils.getDateUtils
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.TimeUnit

class CacheManagerImpl(private val clipDao: ClipDao) : CacheManager {

    private val logger = KotlinLogging.logger {}

    private val dateUtils: DateUtils = getDateUtils()

    private val filesIndexCache: LoadingCache<PullFilesKey, FilesIndex> =
        CacheBuilder.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build(
                object : CacheLoader<PullFilesKey, FilesIndex>() {
                    override fun load(key: PullFilesKey): FilesIndex {
                        val appInstanceId = key.appInstanceId
                        val clipId = key.clipId
                        clipDao.getClipData(appInstanceId, clipId)?.let { clipData ->
                            val dateString =
                                dateUtils.getYYYYMMDD(
                                    dateUtils.convertRealmInstantToLocalDateTime(clipData.createTime),
                                )
                            val filesIndexBuilder = FilesIndexBuilder(PullFileTaskExecutor.CHUNK_SIZE)
                            val fileItems = clipData.getClipAppearItems().filter { it is ClipFiles }
                            for (clipAppearItem in fileItems) {
                                val clipFiles = clipAppearItem as ClipFiles
                                DesktopPathProvider.resolve(appInstanceId, dateString, clipId, clipFiles, false, filesIndexBuilder)
                            }
                            return filesIndexBuilder.build()
                        }
                        throw IllegalStateException("clip data not found: $appInstanceId, $clipId")
                    }
                },
            )

    override fun getFilesIndex(pullFilesKey: PullFilesKey): FilesIndex? {
        return try {
            filesIndexCache.get(pullFilesKey)
        } catch (e: Exception) {
            logger.warn(e) { "getFilesIndex failed: $pullFilesKey" }
            null
        }
    }
}
