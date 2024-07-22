package com.crosspaste.image

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter

class ImageBitmapData(
    key: Any,
    bitmap: ImageBitmap,
    imageInfo: ImageInfo = EMPTY_IMAGE_INFO,
    override val isThumbnail: Boolean = false,
) :
    ImageData<ImageBitmap>(key, bitmap, imageInfo) {

    override val isIcon: Boolean = false

    override fun loadPainter(imageData: ImageBitmap): Painter {
        return BitmapPainter(imageData)
    }
}

class SvgData(
    key: Any,
    painter: Painter,
    imageInfo: ImageInfo = EMPTY_IMAGE_INFO,
) :
    ImageData<Painter>(key, painter, imageInfo) {

    override val isIcon: Boolean = true

    override val isThumbnail: Boolean = false

    override fun loadPainter(imageData: Painter): Painter {
        return imageData
    }
}

class ImageVectorData(
    key: Any,
    imageVector: ImageVector,
    imageInfo: ImageInfo = EMPTY_IMAGE_INFO,
) :
    ImageData<ImageVector>(key, imageVector, imageInfo) {

    override val isIcon: Boolean = true

    override val isThumbnail: Boolean = false

    override fun loadPainter(imageData: ImageVector): Painter {
        throw UnsupportedOperationException("Not supported")
    }

    @Composable
    override fun readPainter(): Painter {
        return rememberVectorPainter(imageData)
    }
}
