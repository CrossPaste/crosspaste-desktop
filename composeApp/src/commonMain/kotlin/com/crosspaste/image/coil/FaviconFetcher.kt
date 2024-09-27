package com.crosspaste.image.coil

import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import com.crosspaste.image.FaviconLoader
import com.crosspaste.image.ImageCreator
import com.crosspaste.paste.item.PasteFileCoordinate
import com.crosspaste.paste.item.PasteUrl
import com.crosspaste.realm.paste.PasteType
import com.crosspaste.utils.ioDispatcher
import kotlinx.coroutines.withContext

class FaviconFetcher(
    private val data: PasteDataItem,
    private val faviconLoader: FaviconLoader,
    private val imageCreator: ImageCreator,
) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        return withContext(ioDispatcher) {
            val pasteData = data.pasteData
            if (pasteData.pasteType == PasteType.URL) {
                pasteData.getPasteItem()?.let {
                    it as PasteUrl
                    faviconLoader.load(it.url)?.let { path ->
                        val pasteFileCoordinate =
                            PasteFileCoordinate(pasteData.getPasteCoordinate(), path)
                        return@withContext ImageFetchResult(
                            dataSource = DataSource.MEMORY_CACHE,
                            isSampled = false,
                            image = imageCreator.createBitmap(pasteFileCoordinate.filePath).asImage(shareable = true),
                        )
                    }
                }
            }
            return@withContext null
        }
    }
}

class FaviconFactory(
    private val faviconLoader: FaviconLoader,
    private val imageCreator: ImageCreator,
) : Fetcher.Factory<PasteDataItem> {
    override fun create(
        data: PasteDataItem,
        options: Options,
        imageLoader: ImageLoader,
    ): Fetcher {
        return FaviconFetcher(data, faviconLoader, imageCreator)
    }
}
