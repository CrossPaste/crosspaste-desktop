package com.crosspaste.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.crosspaste.ui.theme.AppUISize.xLarge

@Composable
internal fun iphoneDuo(): Painter = rememberVectorPainter(iphoneDuoIcon)

private val iphoneDuoIcon by lazy {
    // A shared Compose vector lets Android use the same artwork without loading an SVG.
    ImageVector
        .Builder("iPhone Duo", xLarge, xLarge, 24f, 24f)
        .path(
            fill = SolidColor(Color.Black),
            pathFillType = PathFillType.EvenOdd,
        ) {
            moveTo(3.75f, 0.75f)
            horizontalLineTo(17.25f)
            arcTo(3f, 3f, 0f, false, true, 20.25f, 3.75f)
            verticalLineTo(20.25f)
            arcTo(3f, 3f, 0f, false, true, 17.25f, 23.25f)
            horizontalLineTo(3.75f)
            close()

            // Hinge seam along the straight edge.
            moveTo(5.75f, 3.75f)
            arcTo(0.75f, 0.75f, 0f, false, true, 7.25f, 3.75f)
            verticalLineTo(20.25f)
            arcTo(0.75f, 0.75f, 0f, false, true, 5.75f, 20.25f)
            close()

            moveTo(16.75f, 3.75f)
            arcTo(0.75f, 0.75f, 0f, false, true, 16.75f, 5.25f)
            arcTo(0.75f, 0.75f, 0f, false, true, 16.75f, 3.75f)
            close()

            moveTo(11.5f, 19.5f)
            horizontalLineTo(14.5f)
            arcTo(0.75f, 0.75f, 0f, false, true, 14.5f, 21f)
            horizontalLineTo(11.5f)
            arcTo(0.75f, 0.75f, 0f, false, true, 11.5f, 19.5f)
            close()
        }.build()
}
