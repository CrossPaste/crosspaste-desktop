package com.crosspaste.image.coil

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.crosspaste.image.ThumbnailLoader
import com.crosspaste.utils.getCoilUtils
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.ioDispatcher
import kotlinx.coroutines.withContext

class UserImageFetcher(
    private val data: ImageItem,
    private val thumbnailLoader: ThumbnailLoader,
) : Fetcher {

    private val coilUtils = getCoilUtils()
    private val fileUtils = getFileUtils()

    override suspend fun fetch(): FetchResult? {
        return withContext(ioDispatcher) {
            val pasteFileCoordinate = data.pasteFileCoordinate
            val fileName = pasteFileCoordinate.filePath.name
            try {
                when (fileName.substringAfterLast(".")) {
                    "svg" -> {
                        return@withContext SourceFetchResult(
                            dataSource = DataSource.MEMORY_CACHE,
                            source =
                                ImageSource(
                                    pasteFileCoordinate.filePath,
                                    fileUtils.fileSystem,
                                ),
                            mimeType = "image/svg+xml",
                        )
                    }
                    else -> {
                        if (data.useThumbnail) {
                            thumbnailLoader.load(pasteFileCoordinate)?.let {
                                return@withContext ImageFetchResult(
                                    dataSource = DataSource.MEMORY_CACHE,
                                    isSampled = false,
                                    image = coilUtils.createImage(it),
                                )
                            }
                        } else {
                            val path = pasteFileCoordinate.filePath
                            return@withContext ImageFetchResult(
                                dataSource = DataSource.MEMORY_CACHE,
                                isSampled = false,
                                image = coilUtils.createImage(path),
                            )
                        }
                    }
                }
            } catch (ignore: Exception) {
                return@withContext null
            }
        }
    }
}

class UserImageFactory(
    private val thumbnailLoader: ThumbnailLoader,
) : Fetcher.Factory<ImageItem> {
    override fun create(
        data: ImageItem,
        options: Options,
        imageLoader: ImageLoader,
    ): Fetcher {
        return UserImageFetcher(data, thumbnailLoader)
    }
}
