package com.crosspaste.mcp

import okio.Path
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Encodes a clipboard image as base64 PNG for an MCP ImageContent, downscaled so
 * its longest edge fits what vision models actually look at; larger images only
 * cost the agent context without adding detail.
 */
object McpImageEncoder {

    const val MAX_EDGE = 1568

    const val MIME_TYPE = "image/png"

    /** Base64 PNG of the image at [path], or null when it cannot be decoded (e.g. svg, heic). */
    fun encode(path: Path): String? =
        runCatching {
            val image = ImageIO.read(path.toFile()) ?: return null
            val output = ByteArrayOutputStream()
            if (!ImageIO.write(downscale(image), "png", output)) return null
            Base64.getEncoder().encodeToString(output.toByteArray())
        }.getOrNull()

    private fun downscale(image: BufferedImage): BufferedImage {
        val longestEdge = max(image.width, image.height)
        if (longestEdge <= MAX_EDGE) return image
        val scale = MAX_EDGE.toDouble() / longestEdge
        val width = (image.width * scale).roundToInt().coerceAtLeast(1)
        val height = (image.height * scale).roundToInt().coerceAtLeast(1)
        val scaled = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
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
