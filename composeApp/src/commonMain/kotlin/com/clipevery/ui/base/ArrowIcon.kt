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
fun left(): ImageVector {
    return remember {
        ImageVector.Builder(
            name = "ChevronLeft", defaultWidth = 24.0.dp, defaultHeight =
            24.0.dp, viewportWidth = 960.0f, viewportHeight = 960.0f
        ).apply {
            path(fill = SolidColor(Color(0xFF000000)), stroke = null, strokeLineWidth = 0.0f,
                strokeLineCap = Butt, strokeLineJoin = Miter, strokeLineMiter = 4.0f,
                pathFillType = NonZero) {
                moveTo(560.0f, 720.0f)
                lineTo(320.0f, 480.0f)
                lineToRelative(240.0f, -240.0f)
                lineToRelative(56.0f, 56.0f)
                lineToRelative(-184.0f, 184.0f)
                lineToRelative(184.0f, 184.0f)
                lineToRelative(-56.0f, 56.0f)
                close()
            }
        }
            .build()
    }
}

@Composable
fun right(): ImageVector {
    return remember {
        ImageVector.Builder(
            name = "ChevronRight", defaultWidth = 24.0.dp, defaultHeight =
            24.0.dp, viewportWidth = 960.0f, viewportHeight = 960.0f
        ).apply {
            path(fill = SolidColor(Color(0xFF000000)), stroke = null, strokeLineWidth = 0.0f,
                strokeLineCap = Butt, strokeLineJoin = Miter, strokeLineMiter = 4.0f,
                pathFillType = NonZero) {
                moveTo(504.0f, 480.0f)
                lineTo(320.0f, 296.0f)
                lineToRelative(56.0f, -56.0f)
                lineToRelative(240.0f, 240.0f)
                lineToRelative(-240.0f, 240.0f)
                lineToRelative(-56.0f, -56.0f)
                lineToRelative(184.0f, -184.0f)
                close()
            }
        }
            .build()
    }
}