package com.crosspaste.image

import com.crosspaste.app.AppFileType
import com.crosspaste.paste.item.PasteFileCoordinate
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.utils.ConcurrentLoader
import com.crosspaste.utils.StripedMutex
import com.crosspaste.utils.fileNameRemoveExtension
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.noOptionParent
import io.github.oshai.kotlinlogging.KLogger
import okio.Path

abstract class AbstractThumbnailLoader(
    private val userDataPathProvider: UserDataPathProvider,
) : ConcurrentLoader<PasteFileCoordinate, Path>,
    ThumbnailLoader {

    abstract val logger: KLogger

    protected val fileUtils = getFileUtils()

    override val mutex = StripedMutex()

    abstract val thumbnailSize: Int

    protected val basePath = userDataPathProvider.resolve(appFileType = AppFileType.IMAGE)

    override fun resolve(
        key: String,
        value: PasteFileCoordinate,
    ): Path = getThumbnailPath(value)

    override fun getThumbnailPath(pasteFileCoordinate: PasteFileCoordinate): Path {
        val relativePath =
            fileUtils.createPasteRelativePath(
                pasteFileCoordinate.toPasteCoordinate(),
                pasteFileCoordinate.filePath.name,
            )

        val thumbnailName = "thumbnail_${pasteFileCoordinate.filePath.fileNameRemoveExtension}.png"

        return userDataPathProvider
            .resolve(basePath, relativePath, autoCreate = true, isFile = true)
            .noOptionParent
            .resolve(thumbnailName)
    }

    override fun getOriginMetaPath(pasteFileCoordinate: PasteFileCoordinate): Path {
        val relativePath =
            fileUtils.createPasteRelativePath(
                pasteFileCoordinate.toPasteCoordinate(),
                pasteFileCoordinate.filePath.name,
            )

        val metaProperties = "meta_${pasteFileCoordinate.filePath.fileNameRemoveExtension}.properties"

        return userDataPathProvider
            .resolve(basePath, relativePath, autoCreate = false, isFile = true)
            .noOptionParent
            .resolve(metaProperties)
    }

    override fun convertToKey(value: PasteFileCoordinate): String = value.toString()

    override fun exist(result: Path): Boolean = fileUtils.existFile(result)

    override fun loggerWarning(
        value: PasteFileCoordinate,
        e: Throwable,
    ) {
        logger.warn { "Failed to create thumbnail for file: $value" }
    }
}
