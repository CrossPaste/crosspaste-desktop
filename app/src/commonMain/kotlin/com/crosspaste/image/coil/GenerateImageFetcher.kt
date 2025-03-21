package com.crosspaste.image.coil

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import com.crosspaste.app.AppSize
import com.crosspaste.image.GenerateImageService
import com.crosspaste.utils.getCoilUtils
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.withContext

class GenerateImageFetcher(
    private val appSize: AppSize,
    private val generateImageService: GenerateImageService,
    private val item: GenerateImageItem,
) : Fetcher {

    private val logger = KotlinLogging.logger {}

    private val coilUtils = getCoilUtils()
    private val fileUtils = getFileUtils()

    override suspend fun fetch(): FetchResult? {
        return withContext(ioDispatcher) {
            val path = item.path
            val preview = item.preview
            val density = item.density
            runCatching {
                generateImageService.awaitGeneration(path)
                if (fileUtils.existFile(path)) {
                    val image =
                        if (preview) {
                            coilUtils.createImage(
                                path,
                                (appSize.mainPasteSize.width.value * density).toInt(),
                                (appSize.mainPasteSize.height.value * density).toInt(),
                            )
                        } else {
                            coilUtils.createImage(path)
                        }

                    ImageFetchResult(
                        dataSource = DataSource.MEMORY_CACHE,
                        isSampled = false,
                        image = image,
                    )
                } else {
                    null
                }
            }.onFailure { e ->
                logger.error(e) { "Failed to generate image $path" }
            }.getOrNull()
        }
    }
}

class GenerateImageFactory(
    private val appSize: AppSize,
    private val generateImageService: GenerateImageService,
) : Fetcher.Factory<GenerateImageItem> {
    override fun create(
        data: GenerateImageItem,
        options: Options,
        imageLoader: ImageLoader,
    ): Fetcher {
        return GenerateImageFetcher(appSize, generateImageService, data)
    }
}
