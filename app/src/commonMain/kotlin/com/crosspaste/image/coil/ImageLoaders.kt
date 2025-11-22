package com.crosspaste.image.coil

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.memory.MemoryCache
import com.crosspaste.image.FaviconLoader
import com.crosspaste.image.FileExtImageLoader
import com.crosspaste.image.GenerateImageService
import com.crosspaste.image.ThumbnailLoader
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.utils.getFileUtils

class ImageLoaders(
    private val faviconLoader: FaviconLoader,
    private val fileExtLoader: FileExtImageLoader,
    private val thumbnailLoader: ThumbnailLoader,
    private val generateImageService: GenerateImageService,
    platformContext: PlatformContext,
    userDataPathProvider: UserDataPathProvider,
) {
    companion object {
        val fileUtils = getFileUtils()
    }

    private val memoryCache =
        MemoryCache
            .Builder()
            .strongReferencesEnabled(true)
            .weakReferencesEnabled(true)
            .maxSizeBytes(256L * 1024L * 1024L)
            .maxSizePercent(platformContext, 0.85)
            .build()

    val generateImageLoader =
        ImageLoader
            .Builder(platformContext)
            .components {
                add(GenerateImageFactory(generateImageService))
                    .add(GenerateImageKeyer())
            }.memoryCache {
                memoryCache
            }.build()

    val faviconImageLoader =
        ImageLoader
            .Builder(platformContext)
            .components {
                add(FaviconFactory(faviconLoader))
                    .add(UrlKeyer())
            }.memoryCache {
                memoryCache
            }.build()

    val fileExtImageLoader =
        ImageLoader
            .Builder(platformContext)
            .components {
                add(FileExtFactory(fileExtLoader))
                    .add(FileExtKeyer())
            }.memoryCache {
                memoryCache
            }.build()

    val appSourceLoader =
        ImageLoader
            .Builder(platformContext)
            .components {
                add(AppSourceFactory(userDataPathProvider))
                    .add(AppSourceKeyer())
            }.memoryCache {
                memoryCache
            }.build()

    val userImageLoader =
        ImageLoader
            .Builder(platformContext)
            .components {
                add(UserImageFactory(thumbnailLoader))
                    .add(ImageKeyer())
            }.memoryCache {
                memoryCache
            }.build()
}
