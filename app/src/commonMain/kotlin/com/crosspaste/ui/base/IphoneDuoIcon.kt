package com.crosspaste.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp

@Composable
internal fun iphoneDuo(): Painter = rememberVectorPainter(iphoneDuoIcon)

private val iphoneDuoIcon by lazy {
    val ink = SolidColor(Color.Black)
    ImageVector
        .Builder("iPhone Duo", 24.dp, 24.dp, 24f, 24f)
        .path(
            fill = ink,
            fillAlpha = 0.14f,
            stroke = ink,
            strokeLineWidth = 1.6f,
        ) {
            moveTo(5f, 2.25f)
            horizontalLineTo(15.5f)
            curveTo(17.43f, 2.25f, 19f, 3.82f, 19f, 5.75f)
            verticalLineTo(18.25f)
            curveTo(19f, 20.18f, 17.43f, 21.75f, 15.5f, 21.75f)
            horizontalLineTo(5f)
            close()
        }.path(
            stroke = ink,
            strokeLineWidth = 1.4f,
            strokeLineCap = StrokeCap.Round,
        ) {
            moveTo(15.25f, 6f)
            horizontalLineTo(15.75f)
            moveTo(10.5f, 18.75f)
            horizontalLineTo(13.5f)
        }.build()
}
