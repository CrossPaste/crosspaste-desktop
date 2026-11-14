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
    val ink = SolidColor(Color.Black)
    ImageVector
        .Builder("iPhone Duo", xLarge, xLarge, 24f, 24f)
        .path(
            fill = ink,
            fillAlpha = 0.55f,
        ) {
            // Only the free edge of the folded chassis is visible behind the cover.
            moveTo(17f, 2.5f)
            horizontalLineTo(17.75f)
            arcTo(3f, 3f, 0f, false, true, 20.75f, 5.5f)
            verticalLineTo(20.25f)
            arcTo(3f, 3f, 0f, false, true, 17.75f, 23.25f)
            horizontalLineTo(3.75f)
            verticalLineTo(20f)
            horizontalLineTo(17f)
            close()
        }.path(
            fill = ink,
            pathFillType = PathFillType.EvenOdd,
        ) {
            moveTo(3.75f, 0.75f)
            horizontalLineTo(16f)
            arcTo(3f, 3f, 0f, false, true, 19f, 3.75f)
            verticalLineTo(18.5f)
            arcTo(3f, 3f, 0f, false, true, 16f, 21.5f)
            horizontalLineTo(3.75f)
            close()

            moveTo(15.75f, 3.75f)
            arcTo(0.75f, 0.75f, 0f, false, true, 15.75f, 5.25f)
            arcTo(0.75f, 0.75f, 0f, false, true, 15.75f, 3.75f)
            close()
        }.build()
}
