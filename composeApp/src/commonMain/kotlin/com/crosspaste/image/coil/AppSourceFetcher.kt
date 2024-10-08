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
import kotlinx.coroutines.withContext

class AppSourceFetcher(
    private val data: PasteDataItem,
    private val userDataPathProvider: UserDataPathProvider,
) : Fetcher {

    private val coilUtils = getCoilUtils()
    private val fileUtils = getFileUtils()

    override suspend fun fetch(): FetchResult? {
        return withContext(ioDispatcher) {
            val pasteData = data.pasteData
            pasteData.source?.let {
                val path = userDataPathProvider.resolve("$it.png", AppFileType.ICON)
                if (fileUtils.existFile(path)) {
                    return@withContext ImageFetchResult(
                        dataSource = DataSource.MEMORY_CACHE,
                        isSampled = false,
                        image = coilUtils.createImage(path),
                    )
                }
            }
            return@withContext null
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
    ): Fetcher {
        return AppSourceFetcher(data, userDataPathProvider)
    }
}
