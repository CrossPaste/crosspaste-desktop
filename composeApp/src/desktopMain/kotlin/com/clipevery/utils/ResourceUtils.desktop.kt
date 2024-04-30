package com.clipevery.utils

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.res.ResourceLoader
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.loadSvgPainter
import androidx.compose.ui.res.loadXmlImageVector
import androidx.compose.ui.unit.Density
import com.clipevery.ui.base.ImageBitmapToPainter
import com.clipevery.ui.base.SvgResourceToPainter
import com.clipevery.ui.base.ToPainterImage
import com.clipevery.ui.base.XmlResourceToPainter
import org.xml.sax.InputSource
import java.util.Properties

actual fun getResourceUtils(): ResourceUtils {
    return DesktopResourceUtils
}

object DesktopResourceUtils : ResourceUtils {

    override fun loadProperties(fileName: String): Properties {
        val properties = Properties()
        val classLoader = Thread.currentThread().contextClassLoader
        classLoader.getResourceAsStream(fileName).use { inputStream ->
            if (inputStream == null) {
                throw IllegalArgumentException("File not found: $fileName")
            } else {
                properties.load(inputStream)
            }
        }
        return properties
    }

    @OptIn(ExperimentalComposeUiApi::class)
    override fun loadPainter(
        fileName: String,
        density: Density,
    ): ToPainterImage {
        val inputStream = ResourceLoader.Default.load(fileName)
        return when (fileName.substringAfterLast(".")) {
            "svg" -> SvgResourceToPainter(fileName, inputStream.buffered().use { loadSvgPainter(it, density) })
            "xml" -> XmlResourceToPainter(inputStream.buffered().use { loadXmlImageVector(InputSource(it), density) })
            else -> {
                ImageBitmapToPainter(fileName, inputStream.use { it.buffered().use(::loadImageBitmap) })
            }
        }
    }
}
