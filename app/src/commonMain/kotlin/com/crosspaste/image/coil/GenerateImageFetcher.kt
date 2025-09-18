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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

class GenerateImageFetcher(
    private val appSize: AppSize,
    private val generateImageService: GenerateImageService,
    private val item: GenerateImageItem,
) : Fetcher {

    private val logger = KotlinLogging.logger {}

    private val coilUtils = getCoilUtils()
    private val fileUtils = getFileUtils()

    override suspend fun fetch(): FetchResult? =
        runCatching {
            withContext(ioDispatcher) {
                currentCoroutineContext().ensureActive()

                val path = item.path
                val preview = item.preview
                val density = item.density

                generateImageService.awaitGeneration(path)

                currentCoroutineContext().ensureActive()

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
            }
        }.onFailure { e ->
            when (e) {
                is CancellationException -> {
                    logger.debug { "Image generation cancelled for ${item.path}" }
                }
                else -> {
                    logger.error(e) { "Failed to generate image ${item.path}" }
                }
            }
        }.getOrNull()
}

class GenerateImageFactory(
    private val appSize: AppSize,
    private val generateImageService: GenerateImageService,
) : Fetcher.Factory<GenerateImageItem> {
    override fun create(
        data: GenerateImageItem,
        options: Options,
        imageLoader: ImageLoader,
    ): Fetcher = GenerateImageFetcher(appSize, generateImageService, data)
}
