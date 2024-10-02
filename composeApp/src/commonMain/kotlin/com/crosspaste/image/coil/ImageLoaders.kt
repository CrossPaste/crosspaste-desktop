package com.crosspaste.image.coil

import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import com.crosspaste.app.AppFileType
import com.crosspaste.image.FaviconLoader
import com.crosspaste.image.FileExtImageLoader
import com.crosspaste.image.ImageCreator
import com.crosspaste.image.ThumbnailLoader
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.utils.getCoilUtils

class ImageLoaders(
    private val faviconLoader: FaviconLoader,
    private val fileExtLoader: FileExtImageLoader,
    private val imageCreator: ImageCreator,
    private val thumbnailLoader: ThumbnailLoader,
    userDataPathProvider: UserDataPathProvider,
) {
    private val coilUtils = getCoilUtils()

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
        ImageLoader.Builder(coilUtils.getCoilContext())
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
        ImageLoader.Builder(coilUtils.getCoilContext())
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
        ImageLoader.Builder(coilUtils.getCoilContext())
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
        ImageLoader.Builder(coilUtils.getCoilContext())
            .components {
                add(AppSourceFactory(imageCreator, userDataPathProvider))
                    .add(PasteDataSourceKeyer())
            }
            .memoryCache {
                memoryCache
            }
            .diskCache {
                baseDiskCache
            }
            .build()

    val userImageLoader =
        ImageLoader.Builder(coilUtils.getCoilContext())
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
