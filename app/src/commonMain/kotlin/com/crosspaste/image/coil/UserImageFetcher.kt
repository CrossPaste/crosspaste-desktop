package com.crosspaste.image.coil

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.crosspaste.image.ThumbnailLoader
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.withContext

class UserImageFetcher(
    private val data: ImageItem,
    private val thumbnailLoader: ThumbnailLoader,
) : Fetcher {

    private val logger = KotlinLogging.logger {}

    private val fileUtils = getFileUtils()

    override suspend fun fetch(): FetchResult? =
        withContext(ioDispatcher) {
            runCatching {
                val pasteFileCoordinate = data.pasteFileCoordinate
                val path = pasteFileCoordinate.filePath
                if (fileUtils.existFile(path)) {
                    when (path.name.substringAfterLast(".")) {
                        "svg" -> {
                            SourceFetchResult(
                                dataSource = DataSource.MEMORY_CACHE,
                                source =
                                    ImageSource(
                                        path,
                                        fileUtils.fileSystem,
                                    ),
                                mimeType = "image/svg+xml",
                            )
                        }

                        else -> {
                            if (data.useThumbnail) {
                                thumbnailLoader.load(pasteFileCoordinate)?.let {
                                    SourceFetchResult(
                                        dataSource = DataSource.MEMORY_CACHE,
                                        source =
                                            ImageSource(
                                                it,
                                                fileUtils.fileSystem,
                                            ),
                                        mimeType = null,
                                    )
                                }
                            } else {
                                SourceFetchResult(
                                    dataSource = DataSource.MEMORY_CACHE,
                                    source =
                                        ImageSource(
                                            path,
                                            fileUtils.fileSystem,
                                        ),
                                    mimeType = null,
                                )
                            }
                        }
                    }
                } else {
                    null
                }
            }.onFailure { e ->
                logger.error(e) { "Error while fetching user image" }
            }.getOrNull()
        }
}

class UserImageFactory(
    private val thumbnailLoader: ThumbnailLoader,
) : Fetcher.Factory<ImageItem> {
    override fun create(
        data: ImageItem,
        options: Options,
        imageLoader: ImageLoader,
    ): Fetcher = UserImageFetcher(data, thumbnailLoader)
}
