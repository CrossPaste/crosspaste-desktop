package com.crosspaste.image.coil

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.crosspaste.image.FileExtImageLoader
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.ioDispatcher
import com.crosspaste.utils.isDirectory
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.withContext

class FileExtFetcher(
    private val data: FileExtItem,
    private val fileExtLoader: FileExtImageLoader,
) : Fetcher {

    private val logger = KotlinLogging.logger {}

    private val fileUtils = getFileUtils()

    override suspend fun fetch(): FetchResult? =
        withContext(ioDispatcher) {
            runCatching {
                val path = data.path

                if (fileUtils.existFile(path)) {
                    if (!path.isDirectory) {
                        fileExtLoader.load(path)?.let { iconPath ->
                            SourceFetchResult(
                                source =
                                    ImageSource(
                                        file = iconPath,
                                        fileSystem = fileUtils.fileSystem,
                                    ),
                                mimeType = null,
                                dataSource = DataSource.MEMORY_CACHE,
                            )
                        }
                    } else {
                        null
                    }
                } else {
                    null
                }
            }.onFailure { e ->
                logger.error(e) { "Error while fetching file ext" }
            }.getOrNull()
        }
}

class FileExtFactory(
    private val fileExtLoader: FileExtImageLoader,
) : Fetcher.Factory<FileExtItem> {
    override fun create(
        data: FileExtItem,
        options: Options,
        imageLoader: ImageLoader,
    ): Fetcher = FileExtFetcher(data, fileExtLoader)
}
