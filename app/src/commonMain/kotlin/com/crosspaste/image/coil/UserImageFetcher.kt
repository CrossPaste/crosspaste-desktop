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
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.withContext

class UserImageFetcher(
    private val data: ImageItem,
    private val thumbnailLoader: ThumbnailLoader,
) : Fetcher {

    private val logger = KotlinLogging.logger {}

    private val coilUtils = getCoilUtils()
    private val fileUtils = getFileUtils()

    override suspend fun fetch(): FetchResult? {
        return withContext(ioDispatcher) {
            val pasteFileCoordinate = data.pasteFileCoordinate
            val fileName = pasteFileCoordinate.filePath.name
            runCatching {
                when (fileName.substringAfterLast(".")) {
                    "svg" -> {
                        SourceFetchResult(
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
                                ImageFetchResult(
                                    dataSource = DataSource.MEMORY_CACHE,
                                    isSampled = false,
                                    image = coilUtils.createImage(it),
                                )
                            }
                        } else {
                            val path = pasteFileCoordinate.filePath
                            ImageFetchResult(
                                dataSource = DataSource.MEMORY_CACHE,
                                isSampled = false,
                                image = coilUtils.createImage(path),
                            )
                        }
                    }
                }
            }.onFailure {
                logger.error(it) { "Error while fetching user image" }
            }.getOrNull()
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
