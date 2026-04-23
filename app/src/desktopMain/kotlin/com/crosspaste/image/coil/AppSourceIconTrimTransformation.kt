package com.crosspaste.image.coil

import coil3.Bitmap
import coil3.size.Size
import coil3.transform.Transformation
import org.jetbrains.skia.IRect
import kotlin.math.roundToInt

object AppSourceIconTrimTransformation : Transformation() {

    override val cacheKey: String = "appSourceIconTrim"

    // Over-request 2× of the post-trim target so Coil/Skia has enough source
    // pixels to downsample smoothly — removes the jagged edges that appear when
    // the trimmed bitmap is only slightly larger than the display size.
    private const val OVERSAMPLE_FACTOR: Float = 2f

    fun requestSize(sizePx: Int): Int {
        val trimFactor = 1f - 2f * AppSourceIconAlphaTrim.TYPICAL_PADDING_RATIO
        return (sizePx * OVERSAMPLE_FACTOR / trimFactor).roundToInt()
    }

    override suspend fun transform(
        input: Bitmap,
        size: Size,
    ): Bitmap {
        val width = input.width
        val height = input.height
        if (width < 4 || height < 4) return input

        val trim = AppSourceIconAlphaTrim.detectSymmetricTrim(input)
        if (trim <= 0) return input

        val trimmedWidth = width - 2 * trim
        val trimmedHeight = height - 2 * trim
        if (trimmedWidth <= 0 || trimmedHeight <= 0) return input

        return AppSourceIconAlphaTrim.copySubset(
            input,
            IRect.makeXYWH(trim, trim, trimmedWidth, trimmedHeight),
        )
    }
}
