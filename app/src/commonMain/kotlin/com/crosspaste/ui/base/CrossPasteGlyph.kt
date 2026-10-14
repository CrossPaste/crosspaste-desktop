package com.crosspaste.ui.base

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

private const val TRACE_DURATION_MILLIS = 1600

// Geometry of composeResources/drawable/crosspaste_svg.svg, in its 801x801 viewport.
private const val VIEWPORT = 801f
private const val GLYPH_OFFSET_X = 143f
private const val GLYPH_OFFSET_Y = 81f
private const val OUTLINE_WIDTH = 44f
private const val TEXT_LINE_WIDTH = 52f

// Distance (in viewport units) over which the clip's shoulder fades in. It finishes as the pen
// reaches it, so the shoulder already covers the right side's end when its cap turns from round to butt.
private const val SHOULDER_FADE_LENGTH = 40f

private class GlyphStroke(
    val path: Path,
    val width: Float,
    val cap: StrokeCap,
) {
    val length: Float = PathMeasure().apply { setPath(path, false) }.length
    val style: Stroke = Stroke(width = width, cap = cap)
    val partialStyle: Stroke = if (cap == StrokeCap.Round) style else Stroke(width = width, cap = StrokeCap.Round)
}

private class GlyphGeometry(
    // Traced in this order, as one continuous pen movement.
    val strokes: List<GlyphStroke>,
    val shoulder: Path,
    // Pen distance at which the shoulder starts to appear.
    val shoulderAt: Float,
) {
    val totalLength: Float = strokes.sumOf { it.length.toDouble() }.toFloat()
}

private val GEOMETRY: GlyphGeometry by lazy { glyphGeometry() }

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
                "M341.462 83.414C341.462 37.345 303.976 0 257.734 0C211.493 0 174.006 37.344 174.006 83.415" +
                    "V150.845C174.006 165.741 186.128 177.817 201.08 177.817H355.62",
            ),
            OUTLINE_WIDTH,
            StrokeCap.Round,
        )
    val rightSide =
        GlyphStroke(
            svgPath(
                "M444.948 639C483.636 639 515 607.602 515 568.87V167.13" +
                    "C515 128.398 483.636 97 444.948 97H356",
            ),
            OUTLINE_WIDTH,
            StrokeCap.Butt,
        )
    val leftSide =
        GlyphStroke(
            svgPath(
                "M70.158 96.99C31.41 96.99 0 128.389 0 167.12V568.87" +
                    "C0 607.602 31.41 639 70.158 639H445",
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
        strokes = listOf(leftSide, rightSide, clip) + textLines,
        shoulder = shoulder,
        shoulderAt = leftSide.length + rightSide.length - SHOULDER_FADE_LENGTH,
    )
}

/**
 * The CrossPaste clipboard glyph, drawn as the single pen stroke it is designed as.
 * [progress] runs from 0 (nothing drawn) to 1 (complete glyph); the pen moves at a
 * constant speed along the outline and then the text lines.
 *
 * Providing a lambda allows reading the animation progress directly in the Draw phase,
 * avoiding recomposition of caller and canvas on every frame.
 */
@Composable
fun CrossPasteGlyph(
    progress: () -> Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val geometry = GEOMETRY
    val measure = remember { PathMeasure() }
    val segment = remember { Path() }

    Canvas(modifier) {
        val side = minOf(size.width, size.height)
        val scale = side / VIEWPORT
        val offsetX = (size.width - side) / 2f
        val offsetY = (size.height - side) / 2f
        val penAt = progress().coerceIn(0f, 1f) * geometry.totalLength

        withTransform({
            translate(offsetX, offsetY)
            scale(scale, scale, pivot = Offset.Zero)
            translate(GLYPH_OFFSET_X, GLYPH_OFFSET_Y)
        }) {
            var travelled = 0f
            for (stroke in geometry.strokes) {
                val drawn = (penAt - travelled).coerceIn(0f, stroke.length)
                travelled += stroke.length
                if (drawn <= 0f) break
                if (drawn >= stroke.length) {
                    drawPath(stroke.path, color, style = stroke.style)
                } else {
                    segment.reset()
                    measure.setPath(stroke.path, false)
                    measure.getSegment(0f, drawn, segment, true)
                    drawPath(segment, color, style = stroke.partialStyle)
                }
            }

            val shoulderAlpha = ((penAt - geometry.shoulderAt) / SHOULDER_FADE_LENGTH).coerceIn(0f, 1f)
            if (shoulderAlpha > 0f) {
                drawPath(geometry.shoulder, color, alpha = shoulderAlpha)
            }
        }
    }
}

/**
 * [CrossPasteGlyph] tracing itself once each time it enters the composition.
 */
@Composable
fun AnimatedCrossPasteGlyph(
    color: Color,
    modifier: Modifier = Modifier,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(TRACE_DURATION_MILLIS, easing = FastOutSlowInEasing))
    }
    CrossPasteGlyph(
        progress = { progress.value },
        color = color,
        modifier = modifier,
    )
}
