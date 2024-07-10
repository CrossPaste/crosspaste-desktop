package com.crosspaste.ui.base

import androidx.compose.ui.unit.Density
import com.crosspaste.utils.FileExtUtils
import com.crosspaste.utils.extension
import com.crosspaste.utils.getPainterUtils
import okio.FileSystem
import okio.Path

fun loadImageData(
    path: Path,
    density: Density,
    thumbnail: Boolean = false,
): LoadStateData {
    try {
        val painterUtils = getPainterUtils()
        val toPainterImage =
            when (path.toString().substringAfterLast(".")) {
                "svg" -> SvgResourceToPainter(path, painterUtils.getSvgPainter(path, density))
                "xml" -> XmlResourceToPainter(painterUtils.getXmlImageVector(path, density))
                else -> {
                    if (thumbnail) {
                        val thumbnailPath = painterUtils.getThumbnailPath(path)
                        if (!FileSystem.SYSTEM.exists(thumbnailPath)) {
                            painterUtils.createThumbnail(path)
                        }
                        ImageBitmapToPainter(
                            path,
                            painterUtils.getImageBitmap(thumbnailPath),
                        )
                    } else {
                        ImageBitmapToPainter(path, painterUtils.getImageBitmap(path))
                    }
                }
            }
        return LoadImageData(path, toPainterImage)
    } catch (e: Exception) {
        return ErrorStateData(e)
    }
}

fun loadIconData(
    filePath: Path,
    isFile: Boolean,
    density: Density,
): LoadStateData {
    try {
        if (isFile) {
            val extension = filePath.extension
            FileExtUtils.getExtPreviewImage(extension)?.let {
                return LoadImageData(extension, ImageBitmapToPainter(extension, it))
            } ?: run {
                return LoadIconData(
                    "file",
                    getPainterUtils()
                        .loadResourcePainter("icon/paste/file.svg", density),
                )
            }
        } else {
            return LoadIconData(
                "dir",
                getPainterUtils()
                    .loadResourcePainter("icon/paste/folder.svg", density),
            )
        }
    } catch (e: Exception) {
        return ErrorStateData(e)
    }
}
