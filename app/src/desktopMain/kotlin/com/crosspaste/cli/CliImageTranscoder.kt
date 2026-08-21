package com.crosspaste.cli

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorInfo
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface

/**
 * Decoded pixels for the CLI's sixel rendering (design:
 * ai/design/cli-sixel-support.md, issue #4846). The CLI never decodes image
 * files itself; this transcoder is where stored images become raw pixels.
 */
class CliRawImage(
    val width: Int,
    val height: Int,
    /** Straight-alpha RGBA8888, row-major, exactly width * height * 4 bytes. */
    val rgba: ByteArray,
)

object CliImageTranscoder {

    /** Server-side ceiling for the requested display box, per axis. */
    const val MAX_BOX_PX = 1000

    /**
     * Refuses decompression bombs: dimensions are known from the encoded
     * header before any pixel decoding, and 64M pixels (~8k x 8k) already
     * means a ~256 MB internal decode.
     */
    const val MAX_SOURCE_PIXELS = 64_000_000L

    /**
     * Decodes any Skia-supported format (PNG/JPEG/WebP/GIF/BMP; animated
     * images yield their first frame) and aspect-fit scales it into
     * [maxWidth] x [maxHeight], never upscaling. Returns straight-alpha
     * RGBA8888 at the exact returned dimensions, or null when [bytes] is not
     * a decodable image or exceeds [MAX_SOURCE_PIXELS].
     */
    fun transcode(
        bytes: ByteArray,
        maxWidth: Int,
        maxHeight: Int,
    ): CliRawImage? {
        val boxWidth = maxWidth.coerceIn(1, MAX_BOX_PX)
        val boxHeight = maxHeight.coerceIn(1, MAX_BOX_PX)
        val image =
            try {
                Image.makeFromEncoded(bytes)
            } catch (_: Exception) {
                // Undecodable bytes are an expected input (arbitrary stored
                // files), not an error condition
                return null
            }
        image.use { source ->
            if (source.width <= 0 || source.height <= 0) return null
            if (source.width.toLong() * source.height > MAX_SOURCE_PIXELS) return null
            val scale =
                minOf(
                    boxWidth.toFloat() / source.width,
                    boxHeight.toFloat() / source.height,
                    1f,
                )
            val outWidth = (source.width * scale).toInt().coerceAtLeast(1)
            val outHeight = (source.height * scale).toInt().coerceAtLeast(1)
            // Draw on a premul surface (Skia cannot render onto unpremul),
            // then let readPixels convert to the straight alpha the sixel
            // encoder expects
            Surface.makeRasterN32Premul(outWidth, outHeight).use { surface ->
                surface.canvas.drawImageRect(
                    source,
                    Rect.makeWH(source.width.toFloat(), source.height.toFloat()),
                    Rect.makeWH(outWidth.toFloat(), outHeight.toFloat()),
                    SamplingMode.LINEAR,
                    null,
                    true,
                )
                surface.makeImageSnapshot().use { snapshot ->
                    val bitmap = Bitmap()
                    try {
                        val info =
                            ImageInfo(
                                ColorInfo(ColorType.RGBA_8888, ColorAlphaType.UNPREMUL, null),
                                outWidth,
                                outHeight,
                            )
                        if (!bitmap.allocPixels(info)) return null
                        if (!snapshot.readPixels(bitmap)) return null
                        val rgba = bitmap.readPixels() ?: return null
                        if (rgba.size != outWidth * outHeight * 4) return null
                        return CliRawImage(outWidth, outHeight, rgba)
                    } finally {
                        bitmap.close()
                    }
                }
            }
        }
    }
}
