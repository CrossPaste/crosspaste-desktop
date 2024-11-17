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
import kotlinx.coroutines.withContext

class FileExtFetcher(
    private val data: FileExtItem,
    private val fileExtLoader: FileExtImageLoader,
) : Fetcher {

    private val coilUtils = getCoilUtils()

    override suspend fun fetch(): FetchResult? {
        return withContext(ioDispatcher) {
            val path = data.path
            try {
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
            } catch (_: Exception) {
                null
            }
        }
    }
}

class FileExtFactory(
    private val fileExtLoader: FileExtImageLoader,
) : Fetcher.Factory<FileExtItem> {
    override fun create(
        data: FileExtItem,
        options: Options,
        imageLoader: ImageLoader,
    ): Fetcher {
        return FileExtFetcher(data, fileExtLoader)
    }
}
