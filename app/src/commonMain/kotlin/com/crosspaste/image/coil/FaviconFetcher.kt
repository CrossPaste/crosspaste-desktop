package com.crosspaste.image.coil

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import com.crosspaste.image.FaviconLoader
import com.crosspaste.paste.item.PasteFileCoordinate
import com.crosspaste.paste.item.PasteUrl
import com.crosspaste.utils.getCoilUtils
import com.crosspaste.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.withContext

class FaviconFetcher(
    private val data: PasteDataItem,
    private val faviconLoader: FaviconLoader,
) : Fetcher {

    private val logger = KotlinLogging.logger {}

    private val coilUtils = getCoilUtils()

    override suspend fun fetch(): FetchResult? {
        return withContext(ioDispatcher) {
            val pasteData = data.pasteData
            runCatching {
                pasteData.getPasteItem(PasteUrl::class)?.let {
                    faviconLoader.load(it.url)?.let { path ->
                        val pasteFileCoordinate =
                            PasteFileCoordinate(pasteData.getPasteCoordinate(), path)
                        ImageFetchResult(
                            dataSource = DataSource.MEMORY_CACHE,
                            isSampled = false,
                            image = coilUtils.createImage(pasteFileCoordinate.filePath),
                        )
                    }
                }
            }.onFailure {
                logger.error(it) { "Error while fetching favicon" }
            }.getOrNull()
        }
    }
}

class FaviconFactory(
    private val faviconLoader: FaviconLoader,
) : Fetcher.Factory<PasteDataItem> {
    override fun create(
        data: PasteDataItem,
        options: Options,
        imageLoader: ImageLoader,
    ): Fetcher {
        return FaviconFetcher(data, faviconLoader)
    }
}
