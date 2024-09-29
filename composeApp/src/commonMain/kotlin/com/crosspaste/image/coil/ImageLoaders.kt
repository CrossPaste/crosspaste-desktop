package com.crosspaste.image.coil

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import com.crosspaste.app.AppFileType
import com.crosspaste.image.FaviconLoader
import com.crosspaste.image.FileExtImageLoader
import com.crosspaste.image.ImageCreator
import com.crosspaste.image.ThumbnailLoader
import com.crosspaste.path.UserDataPathProvider

class ImageLoaders(
    private val faviconLoader: FaviconLoader,
    private val fileExtLoader: FileExtImageLoader,
    private val imageCreator: ImageCreator,
    private val thumbnailLoader: ThumbnailLoader,
    userDataPathProvider: UserDataPathProvider,
) {

    private val html2ImageCache = "html2ImageCache"
    private val baseCache = "baseCache"

    private val html2ImageTempPath =
        userDataPathProvider.resolve(
            fileName = html2ImageCache,
            appFileType = AppFileType.TEMP,
        )

    private val baseTempPath =
        userDataPathProvider.resolve(
            fileName = baseCache,
            appFileType = AppFileType.TEMP,
        )

    private val memoryCache =
        MemoryCache.Builder()
            .strongReferencesEnabled(false)
            .maxSizeBytes(48L * 1024L * 1024L)
            .build()

    private val htmlDiskCache =
        DiskCache.Builder()
            .directory(html2ImageTempPath)
            .maxSizeBytes(64L * 1024L * 1024L)
            .build()

    private val baseDiskCache =
        DiskCache.Builder()
            .directory(baseTempPath)
            .maxSizeBytes(64L * 1024L * 1024L)
            .build()

    val html2ImageLoader =
        ImageLoader.Builder(PlatformContext.INSTANCE)
            .components {
                add(Html2ImageFactory(imageCreator))
                    .add(Html2ImageKeyer())
            }
            .memoryCache {
                memoryCache
            }
            .diskCache {
                htmlDiskCache
            }
            .build()

    val faviconImageLoader =
        ImageLoader.Builder(PlatformContext.INSTANCE)
            .components {
                add(FaviconFactory(faviconLoader, imageCreator))
                    .add(PasteDataKeyer())
            }
            .memoryCache {
                memoryCache
            }
            .diskCache {
                baseDiskCache
            }
            .build()

    val fileExtImageLoader =
        ImageLoader.Builder(PlatformContext.INSTANCE)
            .components {
                add(FileExtFactory(fileExtLoader, imageCreator))
                    .add(FileExtKeyer())
            }
            .memoryCache {
                memoryCache
            }
            .diskCache {
                baseDiskCache
            }
            .build()

    val appSourceLoader =
        ImageLoader.Builder(PlatformContext.INSTANCE)
            .components {
                add(AppSourceFactory(imageCreator, userDataPathProvider))
                    .add(PasteDataKeyer())
            }
            .memoryCache {
                memoryCache
            }
            .diskCache {
                baseDiskCache
            }
            .build()

    val userImageLoader =
        ImageLoader.Builder(PlatformContext.INSTANCE)
            .components {
                add(UserImageFactory(thumbnailLoader, imageCreator))
                    .add(ImageKeyer())
            }
            .memoryCache {
                memoryCache
            }
            .diskCache {
                baseDiskCache
            }
            .build()
}
