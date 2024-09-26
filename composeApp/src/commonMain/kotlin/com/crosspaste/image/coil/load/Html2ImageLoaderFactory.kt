package com.crosspaste.image.coil.load

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import com.crosspaste.app.AppFileType
import com.crosspaste.image.ImageCreator
import com.crosspaste.image.coil.fetch.Html2ImageFactory
import com.crosspaste.path.UserDataPathProvider

class Html2ImageLoaderFactory(userDataPathProvider: UserDataPathProvider) {

    private val tempPath = userDataPathProvider.resolve(appFileType = AppFileType.TEMP)

    fun createHtml2ImageLoader(imageCreator: ImageCreator): ImageLoader {
        return ImageLoader.Builder(PlatformContext.INSTANCE)
            .components {
                add(Html2ImageFactory(imageCreator))
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(PlatformContext.INSTANCE, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(tempPath)
                    .maxSizePercent(0.02)
                    .build()
            }
            .build()
    }
}
