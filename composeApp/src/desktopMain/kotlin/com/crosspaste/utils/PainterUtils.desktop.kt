package com.crosspaste.utils

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.ResourceLoader
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.loadSvgPainter
import androidx.compose.ui.res.loadXmlImageVector
import androidx.compose.ui.unit.Density
import com.crosspaste.ui.base.ImageBitmapToPainter
import com.crosspaste.ui.base.SvgResourceToPainter
import com.crosspaste.ui.base.ToPainterImage
import com.crosspaste.ui.base.XmlResourceToPainter
import okio.Path
import org.xml.sax.InputSource
import kotlin.io.path.inputStream

actual fun getPainterUtils(): PainterUtils {
    return DesktopPainterUtils
}

object DesktopPainterUtils : PainterUtils {

    @OptIn(ExperimentalComposeUiApi::class)
    private val loader = ResourceLoader.Default

    override fun loadPainter(
        path: Path,
        density: Density,
    ): ToPainterImage {
        return when (path.name.substringAfterLast(".")) {
            "svg" -> SvgResourceToPainter(path) { getSvgPainter(path, density) }
            "xml" -> XmlResourceToPainter { getXmlImageVector(path, density) }
            else -> {
                ImageBitmapToPainter(path, false) {
                    getImageBitmap(path)
                }
            }
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    override fun loadResourcePainter(
        fileName: String,
        density: Density,
    ): ToPainterImage {
        val inputStream = loader.load(fileName)
        return when (fileName.substringAfterLast(".")) {
            "svg" ->
                SvgResourceToPainter(fileName) {
                    inputStream.buffered().use {
                        loadSvgPainter(it, density)
                    }
                }
            "xml" ->
                XmlResourceToPainter {
                    inputStream.buffered().use {
                        loadXmlImageVector(InputSource(it), density)
                    }
                }
            else -> {
                ImageBitmapToPainter(fileName, false) {
                    inputStream.use {
                        it.buffered().use(::loadImageBitmap)
                    }
                }
            }
        }
    }

    override fun getSvgPainter(
        path: Path,
        density: Density,
    ): Painter {
        return path.toNioPath().inputStream().buffered().use {
            loadSvgPainter(it, density)
        }
    }

    override fun getXmlImageVector(
        path: Path,
        density: Density,
    ): ImageVector {
        return path.toNioPath().inputStream().buffered().use {
            loadXmlImageVector(InputSource(it), density)
        }
    }

    override fun getImageBitmap(path: Path): ImageBitmap {
        return path.toNioPath().inputStream().buffered().use {
            it.use(::loadImageBitmap)
        }
    }
}
