package com.crosspaste.image.coil

import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import com.crosspaste.image.ImageCreator
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.ioDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class Html2ImageFetcher(
    private val item: Html2ImageItem,
    private val imageCreator: ImageCreator,
) : Fetcher {

    private val fileUtils = getFileUtils()

    override suspend fun fetch(): FetchResult? {
        return withContext(ioDispatcher) {
            var waitTime = 0
            while (waitTime < 10000) {
                val path = item.path
                val preview = item.preview
                val density = item.density
                if (fileUtils.existFile(path)) {
                    val image =
                        if (preview) {
                            // todo not use hardcoded values
                            imageCreator.createBitmap(
                                path,
                                with(density) { 424.dp.toPx() }.toInt(),
                                with(density) { 100.dp.toPx() }.toInt(),
                            )
                        } else {
                            imageCreator.createBitmap(path)
                        }.asImage(shareable = true)

                    return@withContext ImageFetchResult(
                        dataSource = DataSource.MEMORY_CACHE,
                        isSampled = false,
                        image = image,
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
