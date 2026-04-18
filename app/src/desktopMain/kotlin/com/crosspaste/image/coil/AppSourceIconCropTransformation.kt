package com.crosspaste.image.coil

import coil3.Bitmap
import coil3.size.Size
import coil3.transform.Transformation
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.IRect
import org.jetbrains.skia.ImageInfo
import kotlin.math.roundToInt

object AppSourceIconCropTransformation : Transformation() {

    private const val CROP_TOP: Float = 0.12f
    private const val CROP_BOTTOM: Float = 0.12f
    private const val CROP_LEFT: Float = 0f
    private const val CROP_RIGHT: Float = 0.12f

    // Pixels whose normalized alpha is at or below this value are treated as transparent.
    private const val ALPHA_THRESHOLD_BYTE: Int = 12

    // Safety margin kept around the alpha bounding box so that subtle shadows or
    // glow pixels at the border of a Mac-style icon are not clipped.
    private const val MARGIN_RATIO: Float = 0.015f

    // Trim thinner than this ratio of the shorter side is ignored.
    private const val MIN_TRIM_RATIO: Float = 0.02f

    // Typical transparent padding on each side of a Mac-style icon. Used only when
    // sizing the Coil request so that, after alpha trim and the fixed crop, the
    // resulting bitmap still has roughly sizePx pixels along each dimension.
    private const val TYPICAL_PADDING_RATIO: Float = 0.075f

    override val cacheKey: String = "appSourceIconCrop"

    fun requestSize(sizePx: Int): Int {
        val trimFactor = 1f - 2f * TYPICAL_PADDING_RATIO
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
        val trim = detectSymmetricTrim(input)
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

        val subsetRect = IRect.makeXYWH(left, top, outWidth, outHeight)

        val result = coil3.Bitmap()
        if (input.extractSubset(result, subsetRect)) return result

        val outInfo = ImageInfo.makeN32(outWidth, outHeight, ColorAlphaType.PREMUL)
        val pixels = input.readPixels(outInfo, outWidth * 4, left, top) ?: return input
        val copy = coil3.Bitmap()
        if (!copy.setImageInfo(outInfo)) return input
        if (!copy.installPixels(pixels)) return input
        return copy
    }

    private fun detectSymmetricTrim(bitmap: Bitmap): Int {
        val width = bitmap.width
        val height = bitmap.height

        // One JNI hop to pull every pixel into a known BGRA layout; alpha is byte 3.
        val info = ImageInfo.makeN32(width, height, ColorAlphaType.PREMUL)
        val pixels = bitmap.readPixels(info, width * 4, 0, 0) ?: return 0

        fun alphaAt(
            x: Int,
            y: Int,
        ): Int = pixels[(y * width + x) * 4 + 3].toInt() and 0xFF

        var top = 0
        topScan@ while (top < height) {
            for (x in 0 until width) {
                if (alphaAt(x, top) > ALPHA_THRESHOLD_BYTE) break@topScan
            }
            top++
        }
        if (top == height) return 0

        var bottom = height - 1
        bottomScan@ while (bottom > top) {
            for (x in 0 until width) {
                if (alphaAt(x, bottom) > ALPHA_THRESHOLD_BYTE) break@bottomScan
            }
            bottom--
        }

        var left = 0
        leftScan@ while (left < width) {
            for (y in top..bottom) {
                if (alphaAt(left, y) > ALPHA_THRESHOLD_BYTE) break@leftScan
            }
            left++
        }

        var right = width - 1
        rightScan@ while (right > left) {
            for (y in top..bottom) {
                if (alphaAt(right, y) > ALPHA_THRESHOLD_BYTE) break@rightScan
            }
            right--
        }

        val paddingLeft = left
        val paddingTop = top
        val paddingRight = width - 1 - right
        val paddingBottom = height - 1 - bottom
        val minPadding = minOf(paddingLeft, paddingTop, paddingRight, paddingBottom)

        val shortSide = minOf(width, height)
        val marginPx = (shortSide * MARGIN_RATIO).roundToInt()
        val minTrimPx = (shortSide * MIN_TRIM_RATIO).roundToInt().coerceAtLeast(1)

        val appliedTrim = (minPadding - marginPx).coerceAtLeast(0)
        return if (appliedTrim < minTrimPx) 0 else appliedTrim
    }
}
