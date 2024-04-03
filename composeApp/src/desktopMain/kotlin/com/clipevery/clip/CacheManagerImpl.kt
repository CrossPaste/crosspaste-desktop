package com.clipevery.clip

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.clipevery.clip.item.ClipFiles
import com.clipevery.dao.clip.ClipDao
import com.clipevery.dto.pull.PullFilesKey
import com.clipevery.path.DesktopPathProvider
import com.clipevery.presist.FilesIndex
import com.clipevery.presist.FilesIndexBuilder
import com.clipevery.task.PullFileTaskExecutor
import com.clipevery.utils.DateUtils
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import org.jetbrains.skia.Image
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.readBytes

class CacheManagerImpl(private val clipDao: ClipDao): CacheManager {

    override val filesIndexCache: LoadingCache<PullFilesKey, FilesIndex> = CacheBuilder.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .build(
            object : CacheLoader<PullFilesKey, FilesIndex>() {
                override fun load(key: PullFilesKey): FilesIndex {
                    val appInstanceId = key.appInstanceId
                    val clipId = key.clipId
                    clipDao.getClipData(appInstanceId, clipId)?.let { clipData ->
                        val dateString = DateUtils.getYYYYMMDD(
                            DateUtils.convertRealmInstantToLocalDateTime(clipData.createTime)
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
            }
        )

    override val imagesCaches: LoadingCache<Path, ImageBitmap> = CacheBuilder.newBuilder()
        .maximumSize(100)
        .build(
            object : CacheLoader<Path, ImageBitmap>() {
                override fun load(key: Path): ImageBitmap {
                    return Image.makeFromEncoded(key.readBytes()).toComposeImageBitmap()
                }
            }
        )

}