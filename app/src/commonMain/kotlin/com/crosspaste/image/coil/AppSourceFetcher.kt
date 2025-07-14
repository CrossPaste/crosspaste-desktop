package com.crosspaste.image.coil

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import com.crosspaste.app.AppFileType
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.utils.getCoilUtils
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.withContext

class AppSourceFetcher(
    private val data: PasteDataItem,
    private val userDataPathProvider: UserDataPathProvider,
) : Fetcher {

    private val logger = KotlinLogging.logger {}

    private val coilUtils = getCoilUtils()
    private val fileUtils = getFileUtils()

    override suspend fun fetch(): FetchResult? =
        withContext(ioDispatcher) {
            val pasteData = data.pasteData
            pasteData.source?.let {
                runCatching {
                    val path = userDataPathProvider.resolve("$it.png", AppFileType.ICON)
                    if (fileUtils.existFile(path)) {
                        ImageFetchResult(
                            dataSource = DataSource.MEMORY_CACHE,
                            isSampled = false,
                            image = coilUtils.createImage(path),
                        )
                    } else {
                        null
                    }
                }.onFailure { e ->
                    logger.error(e) { "Error while fetching app source" }
                }.getOrNull()
            }
        }
}

class AppSourceFactory(
    private val userDataPathProvider: UserDataPathProvider,
) : Fetcher.Factory<PasteDataItem> {
    override fun create(
        data: PasteDataItem,
        options: Options,
        imageLoader: ImageLoader,
    ): Fetcher = AppSourceFetcher(data, userDataPathProvider)
}
