package com.crosspaste.image.coil

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.crosspaste.image.GenerateImageService
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

class GenerateImageFetcher(
    private val generateImageService: GenerateImageService,
    private val item: GenerateImageItem,
) : Fetcher {

    private val logger = KotlinLogging.logger {}

    private val fileUtils = getFileUtils()

    override suspend fun fetch(): FetchResult? =
        runCatching {
            withContext(ioDispatcher) {
                currentCoroutineContext().ensureActive()

                val path = item.path

                generateImageService.awaitGeneration(path)

                currentCoroutineContext().ensureActive()

                if (fileUtils.existFile(path)) {
                    SourceFetchResult(
                        source =
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
    private val generateImageService: GenerateImageService,
) : Fetcher.Factory<GenerateImageItem> {
    override fun create(
        data: GenerateImageItem,
        options: Options,
        imageLoader: ImageLoader,
    ): Fetcher = GenerateImageFetcher(generateImageService, data)
}
