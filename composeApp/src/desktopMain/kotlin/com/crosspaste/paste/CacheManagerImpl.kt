package com.crosspaste.paste

import com.crosspaste.dao.paste.PasteDao
import com.crosspaste.dto.pull.PullFilesKey
import com.crosspaste.paste.item.PasteFiles
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

class CacheManagerImpl(private val pasteDao: PasteDao) : CacheManager {

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
                        val pasteId = key.pasteId
                        pasteDao.getPasteData(appInstanceId, pasteId)?.let { pasteData ->
                            val dateString =
                                dateUtils.getYYYYMMDD(
                                    dateUtils.convertRealmInstantToLocalDateTime(pasteData.createTime),
                                )
                            val filesIndexBuilder = FilesIndexBuilder(PullFileTaskExecutor.CHUNK_SIZE)
                            val fileItems = pasteData.getPasteAppearItems().filter { it is PasteFiles }
                            for (pasteAppearItem in fileItems) {
                                val pasteFiles = pasteAppearItem as PasteFiles
                                DesktopPathProvider.resolve(appInstanceId, dateString, pasteId, pasteFiles, false, filesIndexBuilder)
                            }
                            return filesIndexBuilder.build()
                        }
                        throw IllegalStateException("paste data not found: $appInstanceId, $pasteId")
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
