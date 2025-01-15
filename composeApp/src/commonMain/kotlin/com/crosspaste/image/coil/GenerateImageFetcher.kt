package com.crosspaste.image.coil

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import com.crosspaste.app.AppSize
import com.crosspaste.utils.getCoilUtils
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.ioDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class GenerateImageFetcher(
    private val appSize: AppSize,
    private val item: GenerateImageItem,
) : Fetcher {

    private val coilUtils = getCoilUtils()
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
                            coilUtils.createImage(
                                path,
                                (appSize.mainPasteSize.width.value * density).toInt(),
                                (appSize.mainPasteSize.height.value * density).toInt(),
                            )
                        } else {
                            coilUtils.createImage(path)
                        }

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

class GenerateImageFactory(
    private val appSize: AppSize,
) : Fetcher.Factory<GenerateImageItem> {
    override fun create(
        data: GenerateImageItem,
        options: Options,
        imageLoader: ImageLoader,
    ): Fetcher {
        return GenerateImageFetcher(appSize, data)
    }
}
