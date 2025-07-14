package com.crosspaste.image.coil

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import com.crosspaste.image.FileExtImageLoader
import com.crosspaste.utils.getCoilUtils
import com.crosspaste.utils.ioDispatcher
import com.crosspaste.utils.isDirectory
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.withContext

class FileExtFetcher(
    private val data: FileExtItem,
    private val fileExtLoader: FileExtImageLoader,
) : Fetcher {

    private val logger = KotlinLogging.logger {}

    private val coilUtils = getCoilUtils()

    override suspend fun fetch(): FetchResult? =
        withContext(ioDispatcher) {
            val path = data.path
            runCatching {
                if (!path.isDirectory) {
                    fileExtLoader.load(path)?.let {
                        ImageFetchResult(
                            dataSource = DataSource.MEMORY_CACHE,
                            isSampled = false,
                            image = coilUtils.createImage(it),
                        )
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
