package com.crosspaste.image.coil

import coil3.Bitmap
import coil3.size.Size
import coil3.transform.Transformation
import org.jetbrains.skia.IRect
import kotlin.math.roundToInt

object AppSourceIconCropTransformation : Transformation() {

    private const val CROP_TOP: Float = 0.12f
    private const val CROP_BOTTOM: Float = 0.12f
    private const val CROP_LEFT: Float = 0f
    private const val CROP_RIGHT: Float = 0.12f

    override val cacheKey: String = "appSourceIconCrop"

    fun requestSize(sizePx: Int): Int {
        val trimFactor = 1f - 2f * AppSourceIconAlphaTrim.TYPICAL_PADDING_RATIO
        val cropFactor = 1f - CROP_TOP - CROP_BOTTOM
        return (sizePx / (trimFactor * cropFactor)).roundToInt()
    }

    override suspend fun transform(
        input: Bitmap,
        size: Size,
    ): Bitmap {
        val width = input.width
        val height = input.height
        if (width < 4 || height < 4) return input

        // Step 1: normalize by symmetrically trimming transparent padding.
        val trim = AppSourceIconAlphaTrim.detectSymmetricTrim(input)
        val trimmedWidth = width - 2 * trim
        val trimmedHeight = height - 2 * trim
        if (trimmedWidth <= 0 || trimmedHeight <= 0) return input

        // Step 2: apply the fixed crop ratios relative to the trimmed content.
        val left = trim + (trimmedWidth * CROP_LEFT).roundToInt()
        val top = trim + (trimmedHeight * CROP_TOP).roundToInt()
        val right = trim + trimmedWidth - (trimmedWidth * CROP_RIGHT).roundToInt()
        val bottom = trim + trimmedHeight - (trimmedHeight * CROP_BOTTOM).roundToInt()

        val outWidth = (right - left).coerceAtLeast(1)
        val outHeight = (bottom - top).coerceAtLeast(1)

        return AppSourceIconAlphaTrim.copySubset(
            input,
            IRect.makeXYWH(left, top, outWidth, outHeight),
        )
    }
}
