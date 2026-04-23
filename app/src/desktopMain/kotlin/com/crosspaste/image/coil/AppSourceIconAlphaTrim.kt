package com.crosspaste.image.coil

import coil3.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.IRect
import org.jetbrains.skia.ImageInfo
import kotlin.math.roundToInt

internal object AppSourceIconAlphaTrim {

    // Pixels whose normalized alpha is at or below this value are treated as transparent.
    // Kept low so anti-aliased edge pixels survive the trim.
    private const val ALPHA_THRESHOLD_BYTE: Int = 4

    // Safety margin kept around the alpha bounding box so that subtle shadows or
    // glow pixels at the border of a Mac-style icon are not clipped.
    private const val MARGIN_RATIO: Float = 0.025f

    // Trim thinner than this ratio of the shorter side is ignored.
    private const val MIN_TRIM_RATIO: Float = 0.02f

    // Typical transparent padding on each side of a Mac-style icon. Used only when
    // sizing the Coil request so that, after alpha trim (and any additional crop),
    // the resulting bitmap still has roughly sizePx pixels along each dimension.
    const val TYPICAL_PADDING_RATIO: Float = 0.075f

    /**
     * Returns a bitmap containing the pixels of [input] inside [rect], or [input]
     * itself if the copy cannot be produced. Prefers the zero-copy [Bitmap.extractSubset]
     * path and falls back to an explicit pixel copy when the fast path fails.
     */
    fun copySubset(
        input: Bitmap,
        rect: IRect,
    ): Bitmap {
        val result = Bitmap()
        if (input.extractSubset(result, rect)) return result

        val outWidth = rect.width
        val outHeight = rect.height
        val outInfo = ImageInfo.makeN32(outWidth, outHeight, ColorAlphaType.PREMUL)
        val pixels = input.readPixels(outInfo, outWidth * 4, rect.left, rect.top) ?: return input
        val copy = Bitmap()
        if (!copy.setImageInfo(outInfo)) return input
        if (!copy.installPixels(pixels)) return input
        return copy
    }

    fun detectSymmetricTrim(bitmap: Bitmap): Int {
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
