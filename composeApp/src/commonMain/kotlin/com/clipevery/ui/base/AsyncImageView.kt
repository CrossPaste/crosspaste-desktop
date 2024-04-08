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
import org.xml.sax.InputSource
import java.nio.file.Path
import kotlin.io.path.inputStream
import kotlin.io.path.pathString

fun loadImage(
    path: Path,
    density: Density,
): ToPainterImage {
    return when (path.pathString.substringAfterLast(".")) {
        "svg" -> SvgResourceToPainter(path.inputStream().buffered().use { loadSvgPainter(it, density) })
        "xml" -> XmlResourceToPainter(path.inputStream().buffered().use { loadXmlImageVector(InputSource(it), density) })
        else -> ImageBitmapToPainter(path.inputStream().use { it.buffered().use(::loadImageBitmap) })
    }
}

class LoadImageData(
    val path: Path,
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

class ImageBitmapToPainter(private val imageBitmap: ImageBitmap) : ToPainterImage {
    @Composable
    override fun toPainter(): Painter {
        return remember { BitmapPainter(imageBitmap) }
    }
}

class SvgResourceToPainter(private val painter: Painter) : ToPainterImage {
    @Composable
    override fun toPainter(): Painter {
        return remember { painter }
    }
}

class XmlResourceToPainter(private val imageVector: ImageVector) : ToPainterImage {
    @Composable
    override fun toPainter(): Painter {
        return rememberVectorPainter(imageVector)
    }
}
