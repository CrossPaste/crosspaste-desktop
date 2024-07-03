package com.crosspaste.utils

import androidx.compose.ui.unit.Density
import com.crosspaste.ui.base.ToPainterImage
import java.io.InputStream
import java.nio.file.Path
import java.util.Properties

expect fun getResourceUtils(): ResourceUtils

interface ResourceUtils {

    fun resourceInputStream(fileName: String): InputStream

    fun loadProperties(fileName: String): Properties

    fun loadPainter(
        fileName: String,
        density: Density,
    ): ToPainterImage

    fun loadPainter(
        path: Path,
        density: Density,
    ): ToPainterImage
}
