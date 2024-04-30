package com.clipevery.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.loadSvgPainter
import androidx.compose.ui.res.loadXmlImageVector
import androidx.compose.ui.unit.Density
import com.clipevery.utils.FileExtUtils
import com.clipevery.utils.getResourceUtils
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
): LoadImageData {
    val toPainterImage =
        when (path.pathString.substringAfterLast(".")) {
            "svg" -> SvgResourceToPainter(path, path.inputStream().buffered().use { loadSvgPainter(it, density) })
            "xml" -> XmlResourceToPainter(path.inputStream().buffered().use { loadXmlImageVector(InputSource(it), density) })
            else -> {
                if (thumbnail) {
                    val thumbnailPath = getThumbnailPath(path)
                    if (!thumbnailPath.exists()) {
                        createThumbnail(path)
                    }
                    ImageBitmapToPainter(path, thumbnailPath.inputStream().use { it.buffered().use(::loadImageBitmap) })
                } else {
                    ImageBitmapToPainter(path, path.inputStream().use { it.buffered().use(::loadImageBitmap) })
                }
            }
        }
    return LoadImageData(path, toPainterImage)
}

fun loadIconData(
    filePath: Path,
    isFile: Boolean,
    density: Density,
): LoadStateData {
    if (isFile) {
        val extension = filePath.extension
        FileExtUtils.getExtPreviewImage(extension)?.let {
            return LoadImageData(extension, ImageBitmapToPainter(extension, it))
        } ?: run {
            return LoadIconData("file", getResourceUtils().loadPainter("icon/clip/file.svg", density))
        }
    } else
        {
            return LoadIconData("dir", getResourceUtils().loadPainter("icon/clip/folder.svg", density))
        }
}

class LoadImageData(
    val key: Any,
    val toPainterImage: ToPainterImage,
) : LoadStateData {

    override fun getLoadState(): LoadState {
        return LoadState.Success
    }
}

class LoadIconData(
    val key: Any,
    val toPainterImage: ToPainterImage,
) : LoadStateData {

    override fun getLoadState(): LoadState {
        return LoadState.Success
    }
}

interface ToPainterImage {
    @Composable
    fun toPainter(): Painter
}

class ImageBitmapToPainter(
    private val key: Any,
    private val imageBitmap: ImageBitmap,
) : ToPainterImage {
    @Composable
    override fun toPainter(): Painter {
        return remember(key) { BitmapPainter(imageBitmap) }
    }
}

class SvgResourceToPainter(
    private val key: Any,
    private val painter: Painter,
) : ToPainterImage {
    @Composable
    override fun toPainter(): Painter {
        return remember(key) { painter }
    }
}

class XmlResourceToPainter(private val imageVector: ImageVector) : ToPainterImage {
    @Composable
    override fun toPainter(): Painter {
        return rememberVectorPainter(imageVector)
    }
}
