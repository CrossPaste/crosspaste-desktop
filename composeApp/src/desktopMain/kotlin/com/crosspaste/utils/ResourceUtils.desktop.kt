package com.crosspaste.utils

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.res.ResourceLoader
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.loadSvgPainter
import androidx.compose.ui.res.loadXmlImageVector
import androidx.compose.ui.unit.Density
import com.crosspaste.ui.base.ImageBitmapToPainter
import com.crosspaste.ui.base.SvgResourceToPainter
import com.crosspaste.ui.base.ToPainterImage
import com.crosspaste.ui.base.XmlResourceToPainter
import org.xml.sax.InputSource
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.name

actual fun getResourceUtils(): ResourceUtils {
    return DesktopResourceUtils
}

@OptIn(ExperimentalComposeUiApi::class)
object DesktopResourceUtils : ResourceUtils {

    private val loader = ResourceLoader.Default

    override fun resourceInputStream(fileName: String): InputStream {
        return loader.load(fileName)
    }

    override fun loadProperties(fileName: String): Properties {
        val properties = Properties()
        InputStreamReader(resourceInputStream(fileName), StandardCharsets.UTF_8).use { inputStreamReader ->
            properties.load(inputStreamReader)
        }
        return properties
    }

    @OptIn(ExperimentalComposeUiApi::class)
    override fun loadPainter(
        fileName: String,
        density: Density,
    ): ToPainterImage {
        val inputStream = loader.load(fileName)
        return when (fileName.substringAfterLast(".")) {
            "svg" -> SvgResourceToPainter(fileName, inputStream.buffered().use { loadSvgPainter(it, density) })
            "xml" -> XmlResourceToPainter(inputStream.buffered().use { loadXmlImageVector(InputSource(it), density) })
            else -> {
                ImageBitmapToPainter(fileName, inputStream.use { it.buffered().use(::loadImageBitmap) })
            }
        }
    }

    override fun loadPainter(
        path: Path,
        density: Density,
    ): ToPainterImage {
        val inputStream = path.toFile().inputStream()
        val fileName = path.fileName.name
        return when (path.fileName.name.substringAfterLast(".")) {
            "svg" -> SvgResourceToPainter(fileName, inputStream.buffered().use { loadSvgPainter(it, density) })
            "xml" -> XmlResourceToPainter(inputStream.buffered().use { loadXmlImageVector(InputSource(it), density) })
            else -> {
                ImageBitmapToPainter(fileName, inputStream.use { it.buffered().use(::loadImageBitmap) })
            }
        }
    }
}
