package com.crosspaste.ui.search

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import com.crosspaste.ui.theme.AppUISize.tiny4X
import com.crosspaste.ui.theme.AppUISize.tiny5X
import com.crosspaste.ui.theme.AppUISize.tiny6X

/**
 * Keyboard focus ring for the search window's virtually highlighted controls.
 *
 * By default it sits [outset] outside a pill-shaped chip, so it never covers the chip's own
 * selected/hover edge. Controls inside a clipping container pass `outset = zero` and their
 * [cornerRadius] to draw the ring on their own edge instead.
 */
@Composable
fun Modifier.keyboardHighlightRing(
    visible: Boolean,
    outset: Dp = tiny4X,
    cornerRadius: Dp? = null,
): Modifier {
    if (!visible) return this
    val color = MaterialTheme.colorScheme.primary
    return drawWithContent {
        drawContent()
        val stroke = (tiny5X + tiny6X).toPx()
        // Positive inset grows the ring outward; on the edge the stroke is pulled fully inside
        val inset = outset.toPx() + if (outset.value > 0f) stroke / 2 else -stroke / 2
        val radius = cornerRadius?.toPx()?.plus(inset) ?: (size.height / 2 + inset)
        drawRoundRect(
            color = color,
            topLeft = Offset(-inset, -inset),
            size = Size(size.width + inset * 2, size.height + inset * 2),
            cornerRadius = CornerRadius(radius),
            style = Stroke(width = stroke),
        )
    }
}
