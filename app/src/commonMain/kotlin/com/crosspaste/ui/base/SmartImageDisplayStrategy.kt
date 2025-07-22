package com.crosspaste.ui.base

import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.ScaleFactor
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class SmartImageDisplayStrategy(
    private val longScreenshotThreshold: Float = 3.0f,
    private val longScreenshotMinHeight: Float = 2000f,
    private val aspectRatioDifferenceThreshold: Float = 0.3f,
) : ImageDisplayStrategy {

    override fun compute(
        srcSize: Size,
        dstSize: Size,
    ): ImageDisplayStrategy.DisplayResult {
        val aspectRatio = srcSize.height / srcSize.width

        if (aspectRatio > longScreenshotThreshold && srcSize.height > longScreenshotMinHeight) {
            return ImageDisplayStrategy.DisplayResult(
                contentScale = ContentScale.FillWidth,
                alignment = Alignment.TopCenter,
            )
        }

        val scaleX = dstSize.width / srcSize.width
        val scaleY = dstSize.height / srcSize.height
        val ratioDifference = abs(scaleX - scaleY) / max(scaleX, scaleY)

        return if (ratioDifference < aspectRatioDifferenceThreshold) {
            ImageDisplayStrategy.DisplayResult(
                contentScale = CropContentScale,
                alignment = Alignment.Center,
            )
        } else {
            ImageDisplayStrategy.DisplayResult(
                contentScale = FitContentScale,
                alignment = Alignment.Center,
            )
        }
    }

    private object CropContentScale : ContentScale {
        override fun computeScaleFactor(
            srcSize: Size,
            dstSize: Size,
        ): ScaleFactor {
            val scaleX = dstSize.width / srcSize.width
            val scaleY = dstSize.height / srcSize.height
            val scale = max(scaleX, scaleY)
            return ScaleFactor(scale, scale)
        }
    }

    private object FitContentScale : ContentScale {
        override fun computeScaleFactor(
            srcSize: Size,
            dstSize: Size,
        ): ScaleFactor {
            val scaleX = dstSize.width / srcSize.width
            val scaleY = dstSize.height / srcSize.height
            val scale = min(scaleX, scaleY)
            return ScaleFactor(scale, scale)
        }
    }
}
