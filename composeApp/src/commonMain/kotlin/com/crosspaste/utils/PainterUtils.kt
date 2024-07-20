package com.crosspaste.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Density
import com.crosspaste.ui.base.ToPainterImage
import okio.Path

expect fun getPainterUtils(): PainterUtils

interface PainterUtils {

    fun loadPainter(
        path: Path,
        density: Density,
    ): ToPainterImage

    fun loadResourcePainter(
        fileName: String,
        density: Density,
    ): ToPainterImage

    fun getSvgPainter(
        path: Path,
        density: Density,
    ): Painter

    fun getXmlImageVector(
        path: Path,
        density: Density,
    ): ImageVector

    fun getImageBitmap(path: Path): ImageBitmap
}
