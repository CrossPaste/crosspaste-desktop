package com.crosspaste.image

import androidx.compose.ui.graphics.Color
import org.jetbrains.skia.Image
import org.jetbrains.skia.Surface

object PngBackgroundAnalyzer {

    fun detect(
        bytes: ByteArray,
        sampleSize: Int,
        bottomWeight: Float,
    ): Color {
        val img = Image.Companion.makeFromEncoded(bytes)

        val w = img.width
        val h = img.height
        val s = minOf(sampleSize, w, h)

        val surface = Surface.Companion.makeRasterN32Premul(w, h)
        val canvas = surface.canvas
        canvas.drawImage(img, 0f, 0f)

        val pixmap = surface.makeImageSnapshot().peekPixels()

        fun cornerAvg(
            x0: Int,
            y0: Int,
        ): Triple<Long, Long, Long> {
            var r = 0L
            var g = 0L
            var b = 0L
            repeat(s) { dy ->
                repeat(s) { dx ->
                    val color = pixmap!!.getColor(x0 + dx, y0 + dy)
                    r +=
                        org.jetbrains.skia.Color
                            .getR(color)
                            .toLong()
                    g +=
                        org.jetbrains.skia.Color
                            .getG(color)
                            .toLong()
                    b +=
                        org.jetbrains.skia.Color
                            .getB(color)
                            .toLong()
                }
            }
            val cnt = (s * s).toLong()
            return Triple(r / cnt, g / cnt, b / cnt)
        }

        val (rTL, gTL, bTL) = cornerAvg(0, 0)
        val (rTR, gTR, bTR) = cornerAvg(w - s, 0)
        val (rBL, gBL, bBL) = cornerAvg(0, h - s)
        val (rBR, gBR, bBR) = cornerAvg(w - s, h - s)

        val topW = 1f
        val tot = topW * 2 + bottomWeight * 2

        fun wSum(
            t: Float,
            b: Float,
        ) = t * topW + b * bottomWeight

        val r =
            (
                wSum(rTL.toFloat(), rBL.toFloat()) +
                    wSum(rTR.toFloat(), rBR.toFloat())
            ) / tot
        val g =
            (
                wSum(gTL.toFloat(), gBL.toFloat()) +
                    wSum(gTR.toFloat(), gBR.toFloat())
            ) / tot
        val b =
            (
                wSum(bTL.toFloat(), bBL.toFloat()) +
                    wSum(bTR.toFloat(), bBR.toFloat())
            ) / tot

        return Color(
            (0xFF shl 24) or
                (r.toInt() shl 16) or
                (g.toInt() shl 8) or
                b.toInt(),
        )
    }
}
