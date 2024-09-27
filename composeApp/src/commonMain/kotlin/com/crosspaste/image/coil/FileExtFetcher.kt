package com.crosspaste.image.coil

import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import com.crosspaste.image.FileExtImageLoader
import com.crosspaste.image.ImageCreator
import com.crosspaste.paste.item.PasteFileCoordinate
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.realm.paste.PasteType
import com.crosspaste.utils.ioDispatcher
import kotlinx.coroutines.withContext

class FileExtFetcher(
    private val data: PasteDataItem,
    private val fileExtLoader: FileExtImageLoader,
    private val imageCreator: ImageCreator,
    private val userDataPathProvider: UserDataPathProvider,
) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        return withContext(ioDispatcher) {
            val pasteData = data.pasteData
            if (pasteData.pasteType == PasteType.FILE) {
                pasteData.getPasteItem()?.let {
                    it as PasteFiles
                    try {
                        val files = it.getPasteFiles(userDataPathProvider)
                        if (files.isNotEmpty()) {
                            fileExtLoader.load(files[0].getFilePath())?.let { path ->
                                val pasteFileCoordinate = PasteFileCoordinate(pasteData.getPasteCoordinate(), path)
                                return@withContext ImageFetchResult(
                                    dataSource = DataSource.MEMORY_CACHE,
                                    isSampled = false,
                                    image = imageCreator.createBitmap(pasteFileCoordinate.filePath).asImage(shareable = true),
                                )
                            }
                        }
                    } catch (ignore: Exception) {
                    }
                }
            }
            return@withContext null
        }
    }
}

class FileExtFactory(
    private val fileExtLoader: FileExtImageLoader,
    private val imageCreator: ImageCreator,
    private val userDataPathProvider: UserDataPathProvider,
) : Fetcher.Factory<PasteDataItem> {
    override fun create(
        data: PasteDataItem,
        options: Options,
        imageLoader: ImageLoader,
    ): Fetcher {
        return FileExtFetcher(data, fileExtLoader, imageCreator, userDataPathProvider)
    }
}
