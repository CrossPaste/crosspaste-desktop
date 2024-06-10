package com.clipevery.ui.base

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
    private val imageBitmap: ImageBitmap,
) : ToPainterImage {
    @Composable
    override fun toPainter(): Painter {
        return remember(key) { BitmapPainter(imageBitmap) }
    }
}

class SvgResourceToPainter(
    private val key: Any,
    private val painter: Painter,
) : ToPainterImage {
    @Composable
    override fun toPainter(): Painter {
        return remember(key) { painter }
    }
}

class XmlResourceToPainter(private val imageVector: ImageVector) : ToPainterImage {
    @Composable
    override fun toPainter(): Painter {
        return rememberVectorPainter(imageVector)
    }
}
