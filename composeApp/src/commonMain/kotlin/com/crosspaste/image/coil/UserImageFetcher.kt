package com.crosspaste.image.coil

import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.crosspaste.image.ImageCreator
import com.crosspaste.image.ThumbnailLoader
import com.crosspaste.utils.ioDispatcher
import kotlinx.coroutines.withContext
import okio.FileSystem

class UserImageFetcher(
    private val data: ImageItem,
    private val thumbnailLoader: ThumbnailLoader,
    private val imageCreator: ImageCreator,
) : Fetcher {
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
                                    FileSystem.SYSTEM,
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
                                    image = imageCreator.createBitmap(it).asImage(shareable = true),
                                )
                            }
                        } else {
                            val path = pasteFileCoordinate.filePath
                            return@withContext ImageFetchResult(
                                dataSource = DataSource.MEMORY_CACHE,
                                isSampled = false,
                                image = imageCreator.createBitmap(path).asImage(shareable = true),
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
    private val imageCreator: ImageCreator,
) : Fetcher.Factory<ImageItem> {
    override fun create(
        data: ImageItem,
        options: Options,
        imageLoader: ImageLoader,
    ): Fetcher {
        return UserImageFetcher(data, thumbnailLoader, imageCreator)
    }
}
