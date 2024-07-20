package com.crosspaste.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter

interface ToPainterImage {
    @Composable
    fun toPainter(): Painter
}

class ImageBitmapToPainter(
    private val key: Any,
    private val thumbnail: Boolean = false,
    private val createImageBitmap: () -> ImageBitmap,
) : ToPainterImage {

    @Composable
    override fun toPainter(): Painter {
        return remember(key) { BitmapPainter(createImageBitmap()) }
    }

    fun isThumbnail(): Boolean {
        return thumbnail
    }
}

class SvgResourceToPainter(
    private val key: Any,
    private val createPainter: () -> Painter,
) : ToPainterImage {
    @Composable
    override fun toPainter(): Painter {
        return remember(key) { createPainter() }
    }
}

class XmlResourceToPainter(
    private val createImageVector: () -> ImageVector,
) : ToPainterImage {
    @Composable
    override fun toPainter(): Painter {
        return rememberVectorPainter(createImageVector())
    }
}
