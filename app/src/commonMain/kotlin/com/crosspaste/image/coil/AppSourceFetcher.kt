package com.crosspaste.image.coil

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.crosspaste.app.AppFileType
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.withContext

class AppSourceFetcher(
    private val data: AppSourceItem,
    private val userDataPathProvider: UserDataPathProvider,
) : Fetcher {

    private val logger = KotlinLogging.logger {}

    private val fileUtils = getFileUtils()

    override suspend fun fetch(): FetchResult? =
        withContext(ioDispatcher) {
            data.source?.let {
                runCatching {
                    val path = userDataPathProvider.resolve("$it.png", AppFileType.ICON)
                    if (fileUtils.existFile(path)) {
                        SourceFetchResult(
                            source =
                                ImageSource(
                                    file = path,
                                    fileSystem = fileUtils.fileSystem,
                                ),
                            mimeType = "image/png",
                            dataSource = DataSource.MEMORY_CACHE,
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
) : Fetcher.Factory<AppSourceItem> {
    override fun create(
        data: AppSourceItem,
        options: Options,
        imageLoader: ImageLoader,
    ): Fetcher = AppSourceFetcher(data, userDataPathProvider)
}
