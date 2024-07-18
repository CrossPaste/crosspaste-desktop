package com.crosspaste.ui.base

import androidx.compose.ui.unit.Density
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
    isFile: Boolean?,
    density: Density,
): LoadStateData {
    try {
        when (isFile) {
            true -> {
                return LoadIconData(
                    "file",
                    getPainterUtils()
                        .loadResourcePainter("icon/paste/file.svg", density),
                )
            }
            false -> {
                return LoadIconData(
                    "dir",
                    getPainterUtils()
                        .loadResourcePainter("icon/paste/folder.svg", density),
                )
            }
            else -> {
                return LoadIconData(
                    "file-slash",
                    getPainterUtils()
                        .loadResourcePainter("icon/paste/file-slash.svg", density),
                )
            }
        }
    } catch (e: Exception) {
        return ErrorStateData(e)
    }
}
