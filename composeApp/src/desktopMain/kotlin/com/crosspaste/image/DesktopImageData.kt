package com.crosspaste.image

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter

class ImageBitmapData(key: Any, bitmap: ImageBitmap) :
    DesktopImageData<ImageBitmap>(key, bitmap) {

    override val isIcon: Boolean = true

    override fun loadPainter(imageData: ImageBitmap): Painter {
        return BitmapPainter(imageData)
    }
}

class SvgData(key: Any, painter: Painter) :
    DesktopImageData<Painter>(key, painter) {

    override val isIcon: Boolean = false

    override fun loadPainter(imageData: Painter): Painter {
        return imageData
    }
}

class ImageVectorData(key: Any, imageVector: ImageVector) :
    DesktopImageData<ImageVector>(key, imageVector) {

    override val isIcon: Boolean = false

    override fun loadPainter(imageData: ImageVector): Painter {
        throw UnsupportedOperationException("Not supported")
    }

    @Composable
    override fun readPainter(): Painter {
        return rememberVectorPainter(imageData)
    }
}

abstract class DesktopImageData<T>(
    key: Any,
    imageData: T,
) : ImageData<T>(key, imageData)
