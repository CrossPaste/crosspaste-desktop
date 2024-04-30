package com.clipevery.utils

import androidx.compose.ui.unit.Density
import com.clipevery.ui.base.ToPainterImage
import java.util.Properties

expect fun getResourceUtils(): ResourceUtils

interface ResourceUtils {

    fun loadProperties(fileName: String): Properties

    fun loadPainter(
        fileName: String,
        density: Density,
    ): ToPainterImage
}
