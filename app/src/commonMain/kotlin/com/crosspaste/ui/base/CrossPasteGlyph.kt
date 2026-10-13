package com.crosspaste.ui.base

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser

// Geometry of composeResources/drawable/crosspaste_svg.svg, in its 801x801 viewport.
private const val VIEWPORT = 801f
private const val GLYPH_OFFSET_X = 143f
private const val GLYPH_OFFSET_Y = 81f
private const val OUTLINE_WIDTH = 44f
private const val TEXT_LINE_WIDTH = 52f

// Distance (in viewport units) over which the clip's shoulder fades in once the clip is traced.
private const val SHOULDER_FADE_LENGTH = 40f

private class GlyphStroke(
    val path: Path,
    val width: Float,
    val cap: StrokeCap,
) {
    val length: Float = PathMeasure().apply { setPath(path, false) }.length
}

private class GlyphGeometry(
    // Traced in this order, as one continuous pen movement.
    val strokes: List<GlyphStroke>,
    val shoulder: Path,
    // Pen distance after which the shoulder starts to appear.
    val shoulderAt: Float,
) {
    val totalLength: Float = strokes.sumOf { it.length.toDouble() }.toFloat()
}

private fun svgPath(data: String): Path = PathParser().parsePathString(data).toPath()

private fun line(
    x1: Float,
    x2: Float,
    y: Float,
): Path =
    Path().apply {
        moveTo(x1, y)
        lineTo(x2, y)
    }

private fun glyphGeometry(): GlyphGeometry {
    val clip =
        GlyphStroke(
            svgPath(
                "M355.62 177.817H201.08c-14.952 0-27.074-12.076-27.074-26.972v-67.43" +
                    "C174.006 37.344 211.493 0 257.734 0c46.242 0 83.728 37.345 83.728 83.414",
            ),
            OUTLINE_WIDTH,
            StrokeCap.Round,
        )
    val rightSide =
        GlyphStroke(
            svgPath(
                "M356 97h88.948C483.636 97 515 128.398 515 167.13v401.74" +
                    "c0 38.732-31.364 70.13-70.052 70.13",
            ),
            OUTLINE_WIDTH,
            StrokeCap.Butt,
        )
    val leftSide =
        GlyphStroke(
            svgPath(
                "M445 639H70.158C31.41 639 0 607.602 0 568.87V167.12" +
                    "c0-38.731 31.41-70.13 70.158-70.13",
            ),
            OUTLINE_WIDTH,
            StrokeCap.Round,
        )
    // The text lines are rounded rects in the SVG, i.e. thick round-capped lines.
    val textLines =
        listOf(
            line(102f, 414f, 290f),
            line(102f, 414f, 405f),
            line(101f, 272f, 520f),
        ).map { GlyphStroke(it, TEXT_LINE_WIDTH, StrokeCap.Round) }

    val shoulder =
        svgPath(
            "M320 75c18.666.508 32.661.84 41.989.998 9.326.158 14.997.158 17.011 0V118" +
                "h-30.463c-15.727 0-28.483-12.791-28.511-28.587L320 75Z",
        )
    return GlyphGeometry(
        strokes = listOf(clip, rightSide, leftSide) + textLines,
        shoulder = shoulder,
        shoulderAt = clip.length,
    )
}

/**
 * The CrossPaste clipboard glyph, drawn as the single pen stroke it is designed as.
 * [progress] runs from 0 (nothing drawn) to 1 (complete glyph); the pen moves at a
 * constant speed along the outline and then the text lines.
 */
@Composable
fun CrossPasteGlyph(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val geometry = remember { glyphGeometry() }
    val measure = remember { PathMeasure() }
    val segment = remember { Path() }

    Canvas(modifier) {
        val scale = minOf(size.width, size.height) / VIEWPORT
        val penAt = progress.coerceIn(0f, 1f) * geometry.totalLength

        withTransform({
            scale(scale, scale, pivot = Offset.Zero)
            translate(GLYPH_OFFSET_X, GLYPH_OFFSET_Y)
        }) {
            var travelled = 0f
            for (stroke in geometry.strokes) {
                val drawn = (penAt - travelled).coerceIn(0f, stroke.length)
                travelled += stroke.length
                if (drawn <= 0f) break
                val style = Stroke(width = stroke.width, cap = stroke.cap)
                if (drawn >= stroke.length) {
                    drawPath(stroke.path, color, style = style)
                } else {
                    segment.reset()
                    measure.setPath(stroke.path, false)
                    measure.getSegment(0f, drawn, segment, true)
                    drawPath(segment, color, style = style)
                }
            }

            val shoulderAlpha = ((penAt - geometry.shoulderAt) / SHOULDER_FADE_LENGTH).coerceIn(0f, 1f)
            if (shoulderAlpha > 0f) {
                drawPath(geometry.shoulder, color, alpha = shoulderAlpha)
            }
        }
    }
}
