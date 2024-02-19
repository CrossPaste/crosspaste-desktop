package com.clipevery.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType.Companion.NonZero
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap.Companion.Butt
import androidx.compose.ui.graphics.StrokeJoin.Companion.Miter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

@Composable
fun feed(): ImageVector {
    return remember {
        ImageVector.Builder(
            name = "Feed", defaultWidth = 24.0.dp, defaultHeight = 24.0.dp,
            viewportWidth = 960.0f, viewportHeight = 960.0f
        ).apply {
            path(fill = SolidColor(Color(0xFF000000)), stroke = null, strokeLineWidth = 0.0f,
                strokeLineCap = Butt, strokeLineJoin = Miter, strokeLineMiter = 4.0f,
                pathFillType = NonZero) {
                moveTo(200.0f, 840.0f)
                quadToRelative(-33.0f, 0.0f, -56.5f, -23.5f)
                reflectiveQuadTo(120.0f, 760.0f)
                verticalLineToRelative(-560.0f)
                quadToRelative(0.0f, -33.0f, 23.5f, -56.5f)
                reflectiveQuadTo(200.0f, 120.0f)
                horizontalLineToRelative(440.0f)
                lineToRelative(200.0f, 200.0f)
                verticalLineToRelative(440.0f)
                quadToRelative(0.0f, 33.0f, -23.5f, 56.5f)
                reflectiveQuadTo(760.0f, 840.0f)
                lineTo(200.0f, 840.0f)
                close()
                moveTo(200.0f, 760.0f)
                horizontalLineToRelative(560.0f)
                verticalLineToRelative(-400.0f)
                lineTo(600.0f, 360.0f)
                verticalLineToRelative(-160.0f)
                lineTo(200.0f, 200.0f)
                verticalLineToRelative(560.0f)
                close()
                moveTo(280.0f, 680.0f)
                horizontalLineToRelative(400.0f)
                verticalLineToRelative(-80.0f)
                lineTo(280.0f, 600.0f)
                verticalLineToRelative(80.0f)
                close()
                moveTo(280.0f, 360.0f)
                horizontalLineToRelative(200.0f)
                verticalLineToRelative(-80.0f)
                lineTo(280.0f, 280.0f)
                verticalLineToRelative(80.0f)
                close()
                moveTo(280.0f, 520.0f)
                horizontalLineToRelative(400.0f)
                verticalLineToRelative(-80.0f)
                lineTo(280.0f, 440.0f)
                verticalLineToRelative(80.0f)
                close()
                moveTo(200.0f, 200.0f)
                verticalLineToRelative(160.0f)
                verticalLineToRelative(-160.0f)
                verticalLineToRelative(560.0f)
                verticalLineToRelative(-560.0f)
                close()
            }
        }
            .build()
    }
}