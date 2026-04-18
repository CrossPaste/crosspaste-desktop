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
        val pixels = readN32Pixels(bitmap) ?: return 0
        val bounds = findContentBounds(pixels, width, height) ?: return 0
        val minPadding =
            minOf(
                bounds.left,
                bounds.top,
                width - 1 - bounds.right,
                height - 1 - bounds.bottom,
            )
        return resolveTrim(minPadding, minOf(width, height))
    }

    // One JNI hop to pull every pixel in N32 layout; alpha sits at byte 3 of every 4-byte pixel.
    private fun readN32Pixels(bitmap: Bitmap): ByteArray? {
        val info = ImageInfo.makeN32(bitmap.width, bitmap.height, ColorAlphaType.PREMUL)
        return bitmap.readPixels(info, bitmap.width * 4, 0, 0)
    }

    private fun findContentBounds(
        pixels: ByteArray,
        width: Int,
        height: Int,
    ): ContentBounds? {
        var top = 0
        while (top < height && isRowTransparent(pixels, width, top)) top++
        if (top == height) return null

        var bottom = height - 1
        while (bottom > top && isRowTransparent(pixels, width, bottom)) bottom--

        var left = 0
        while (left < width && isColumnTransparent(pixels, width, left, top, bottom)) left++

        var right = width - 1
        while (right > left && isColumnTransparent(pixels, width, right, top, bottom)) right--

        return ContentBounds(left, top, right, bottom)
    }

    private fun isRowTransparent(
        pixels: ByteArray,
        width: Int,
        y: Int,
    ): Boolean {
        var offset = y * width * 4 + 3
        repeat(width) {
            if (pixels[offset].toInt() and 0xFF > ALPHA_THRESHOLD_BYTE) return false
            offset += 4
        }
        return true
    }

    private fun isColumnTransparent(
        pixels: ByteArray,
        width: Int,
        x: Int,
        yStart: Int,
        yEnd: Int,
    ): Boolean {
        val rowStride = width * 4
        var offset = (yStart * width + x) * 4 + 3
        repeat(yEnd - yStart + 1) {
            if (pixels[offset].toInt() and 0xFF > ALPHA_THRESHOLD_BYTE) return false
            offset += rowStride
        }
        return true
    }

    private fun resolveTrim(
        minPadding: Int,
        shortSide: Int,
    ): Int {
        val marginPx = (shortSide * MARGIN_RATIO).roundToInt()
        val minTrimPx = (shortSide * MIN_TRIM_RATIO).roundToInt().coerceAtLeast(1)
        val appliedTrim = (minPadding - marginPx).coerceAtLeast(0)
        return if (appliedTrim < minTrimPx) 0 else appliedTrim
    }

    private data class ContentBounds(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    )
}
