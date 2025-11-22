package com.crosspaste.image.coil

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.crosspaste.image.FaviconLoader
import com.crosspaste.paste.item.PasteFileCoordinate
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.withContext

class FaviconFetcher(
    private val data: UrlItem,
    private val faviconLoader: FaviconLoader,
) : Fetcher {

    private val logger = KotlinLogging.logger {}

    private val fileUtils = getFileUtils()

    override suspend fun fetch(): FetchResult? =
        withContext(ioDispatcher) {
            runCatching {
                faviconLoader.load(data.url)?.let { path ->
                    val pasteFileCoordinate =
                        PasteFileCoordinate(data.pasteCoordinate, path)
                    val path = pasteFileCoordinate.filePath
                    if (fileUtils.existFile(path)) {
                        SourceFetchResult(
                            ImageSource(
                                file = path,
                                fileSystem = fileUtils.fileSystem,
                            ),
                            mimeType = null,
                            dataSource = DataSource.MEMORY_CACHE,
                        )
                    } else {
                        null
                    }
                }
            }.onFailure { e ->
                logger.error(e) { "Error while fetching favicon" }
            }.getOrNull()
        }
}

class FaviconFactory(
    private val faviconLoader: FaviconLoader,
) : Fetcher.Factory<UrlItem> {
    override fun create(
        data: UrlItem,
        options: Options,
        imageLoader: ImageLoader,
    ): Fetcher = FaviconFetcher(data, faviconLoader)
}
