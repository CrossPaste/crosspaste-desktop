package com.crosspaste.image.coil.fetch

import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import com.crosspaste.image.ImageCreator
import com.crosspaste.image.coil.item.Html2ImageItem
import com.crosspaste.utils.getFileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class Html2ImageFetcher(private val data: Html2ImageItem, private val imageCreator: ImageCreator) : Fetcher {

    private val fileUtils = getFileUtils()

    override suspend fun fetch(): FetchResult? {
        return withContext(Dispatchers.IO) {
            var waitTime = 0
            while (waitTime < 10000) {
                if (fileUtils.existFile(data.html2ImagePath)) {
                    return@withContext ImageFetchResult(
                        dataSource = DataSource.MEMORY_CACHE,
                        isSampled = false,
                        image = imageCreator.createBitmap(data.html2ImagePath).asImage(shareable = true),
                    )
                } else {
                    delay(100)
                    waitTime += 100
                }
            }
            return@withContext null
        }
    }
}

class Html2ImageFactory(private val imageCreator: ImageCreator) : Fetcher.Factory<Html2ImageItem> {
    override fun create(
        data: Html2ImageItem,
        options: Options,
        imageLoader: ImageLoader,
    ): Fetcher {
        return Html2ImageFetcher(data, imageCreator)
    }
}
