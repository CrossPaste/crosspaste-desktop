package com.crosspaste.mcp

import com.crosspaste.utils.ioDispatcher
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import kotlinx.coroutines.withContext
import okio.Path
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Encodes a clipboard image for an MCP ImageContent, downscaled so its longest
 * edge fits what vision models actually look at; larger images only cost the
 * agent context without adding detail. Opaque images go out as JPEG, since a
 * photo as PNG easily exceeds the per-image size limits of model APIs;
 * images with an alpha channel stay PNG to keep transparency.
 */
object McpImageEncoder {

    const val MAX_EDGE = 1568

    /**
     * Decompression-bomb guard: the size is read from the header before any
     * pixel decoding, so a huge image is skipped instead of allocated.
     */
    const val MAX_SOURCE_PIXELS = 64_000_000L

    const val JPEG_QUALITY = 0.85f

    const val PNG_MIME_TYPE = "image/png"

    const val JPEG_MIME_TYPE = "image/jpeg"

    /** The image at [path], or null when it cannot be decoded (e.g. svg, heic) or is too large. */
    suspend fun encode(path: Path): ImageContent? =
        withContext(ioDispatcher) {
            runCatching { encodeBlocking(path) }.getOrNull()
        }

    private fun encodeBlocking(path: Path): ImageContent? {
        val image = readWithinPixelLimit(path) ?: return null
        val output = ByteArrayOutputStream()
        val mimeType =
            if (image.colorModel.hasAlpha()) {
                if (!ImageIO.write(downscale(image, BufferedImage.TYPE_INT_ARGB), "png", output)) return null
                PNG_MIME_TYPE
            } else {
                writeJpeg(downscale(image, BufferedImage.TYPE_INT_RGB), output)
                JPEG_MIME_TYPE
            }
        return ImageContent(data = Base64.getEncoder().encodeToString(output.toByteArray()), mimeType = mimeType)
    }

    private fun readWithinPixelLimit(path: Path): BufferedImage? =
        ImageIO.createImageInputStream(path.toFile())?.use { input ->
            val reader = ImageIO.getImageReaders(input).asSequence().firstOrNull() ?: return null
            try {
                reader.input = input
                val width = reader.getWidth(0).toLong()
                val height = reader.getHeight(0).toLong()
                if (width <= 0 || height <= 0 || width * height > MAX_SOURCE_PIXELS) return null
                reader.read(0)
            } finally {
                reader.dispose()
            }
        }

    private fun writeJpeg(
        image: BufferedImage,
        output: ByteArrayOutputStream,
    ) {
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        try {
            ImageIO.createImageOutputStream(output).use { stream ->
                writer.output = stream
                val param =
                    writer.defaultWriteParam.apply {
                        compressionMode = ImageWriteParam.MODE_EXPLICIT
                        compressionQuality = JPEG_QUALITY
                    }
                writer.write(null, IIOImage(image, null, null), param)
            }
        } finally {
            writer.dispose()
        }
    }

    /**
     * Redraws [image] into [targetType] with its longest edge at most [MAX_EDGE];
     * redrawing even when no scaling is needed normalizes indexed/gray/custom
     * source types into one the target encoder accepts.
     */
    private fun downscale(
        image: BufferedImage,
        targetType: Int,
    ): BufferedImage {
        val scale = min(1.0, MAX_EDGE.toDouble() / max(image.width, image.height))
        val width = (image.width * scale).roundToInt().coerceAtLeast(1)
        val height = (image.height * scale).roundToInt().coerceAtLeast(1)
        if (scale == 1.0 && image.type == targetType) return image
        val scaled = BufferedImage(width, height, targetType)
        val graphics = scaled.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            graphics.drawImage(image, 0, 0, width, height, null)
        } finally {
            graphics.dispose()
        }
        return scaled
    }
}
