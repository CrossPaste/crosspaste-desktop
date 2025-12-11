package com.crosspaste.image.coil

import coil3.Bitmap
import coil3.size.Size
import coil3.transform.Transformation
import org.jetbrains.skia.IRect
import kotlin.math.roundToInt

object MacCropTransformation : Transformation() {

    private const val CROP_TOP: Float = 0.18f
    private const val CROP_BOTTOM: Float = 0.18f
    private const val CROP_LEFT: Float = 0f
    private const val CROP_RIGHT: Float = 0.18f

    override val cacheKey: String = "mac"

    fun requestSize(sizePx: Int): Int = (sizePx / (1f - CROP_TOP - CROP_BOTTOM)).roundToInt()

    override suspend fun transform(
        input: Bitmap,
        size: Size,
    ): Bitmap {
        // 2) Calculate pixel coordinates
        val left = (input.width * CROP_LEFT).roundToInt()
        val top = (input.height * CROP_TOP).roundToInt()
        val right = (input.width - input.width * CROP_RIGHT).roundToInt()
        val bottom = (input.height - input.height * CROP_BOTTOM).roundToInt()

        val width = (right - left).coerceAtLeast(1)
        val height = (bottom - top).coerceAtLeast(1)

        // If the dimensions haven't changed, return the original bitmap
        if (width == input.width && height == input.height) return input

        val subsetRect = IRect.makeXYWH(left, top, width, height)

        // 3) Try extractSubset first (zero-copy when possible)
        val result = coil3.Bitmap()
        val ok = input.extractSubset(result, subsetRect)
        if (ok) return result

        // 4) Fallback: if extractSubset fails (rare), copy the pixels manually
        val copy = coil3.Bitmap()
        copy.allocN32Pixels(width, height)
        input.readPixels(srcX = left, srcY = top)
        return copy
    }
}
