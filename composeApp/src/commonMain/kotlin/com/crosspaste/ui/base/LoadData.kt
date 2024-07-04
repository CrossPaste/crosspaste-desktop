package com.crosspaste.ui.base

import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.loadSvgPainter
import androidx.compose.ui.res.loadXmlImageVector
import androidx.compose.ui.unit.Density
import com.crosspaste.utils.FileExtUtils
import com.crosspaste.utils.getResourceUtils
import org.xml.sax.InputSource
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.inputStream
import kotlin.io.path.pathString

fun loadImageData(
    path: Path,
    density: Density,
    thumbnail: Boolean = false,
): LoadStateData {
    try {
        val toPainterImage =
            when (path.pathString.substringAfterLast(".")) {
                "svg" -> SvgResourceToPainter(path, path.inputStream().buffered().use { loadSvgPainter(it, density) })
                "xml" ->
                    XmlResourceToPainter(
                        path.inputStream().buffered().use { loadXmlImageVector(InputSource(it), density) },
                    )

                else -> {
                    if (thumbnail) {
                        val thumbnailPath = getThumbnailPath(path)
                        if (!thumbnailPath.exists()) {
                            createThumbnail(path)
                        }
                        ImageBitmapToPainter(
                            path,
                            thumbnailPath.inputStream().buffered().use { it.use(::loadImageBitmap) },
                        )
                    } else {
                        ImageBitmapToPainter(path, path.inputStream().buffered().use { it.use(::loadImageBitmap) })
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
                return LoadIconData("file", getResourceUtils().loadPainter("icon/paste/file.svg", density))
            }
        } else {
            return LoadIconData("dir", getResourceUtils().loadPainter("icon/paste/folder.svg", density))
        }
    } catch (e: Exception) {
        return ErrorStateData(e)
    }
}
