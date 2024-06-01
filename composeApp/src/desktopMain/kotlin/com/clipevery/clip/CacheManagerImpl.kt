package com.clipevery.clip

import com.clipevery.clip.item.ClipFiles
import com.clipevery.dao.clip.ClipDao
import com.clipevery.dto.pull.PullFilesKey
import com.clipevery.path.DesktopPathProvider
import com.clipevery.presist.FilesIndex
import com.clipevery.presist.FilesIndexBuilder
import com.clipevery.task.PullFileTaskExecutor
import com.clipevery.utils.DateUtils
import com.clipevery.utils.getDateUtils
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
