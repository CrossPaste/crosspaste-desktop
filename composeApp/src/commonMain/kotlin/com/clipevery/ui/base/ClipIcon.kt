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

@Composable
fun link(): ImageVector {
    return remember {
        ImageVector.Builder(
            name = "Link", defaultWidth = 24.0.dp, defaultHeight = 24.0.dp,
            viewportWidth = 960.0f, viewportHeight = 960.0f
        ).apply {
            path(fill = SolidColor(Color(0xFF000000)), stroke = null, strokeLineWidth = 0.0f,
                strokeLineCap = Butt, strokeLineJoin = Miter, strokeLineMiter = 4.0f,
                pathFillType = NonZero) {
                moveTo(440.0f, 680.0f)
                lineTo(280.0f, 680.0f)
                quadToRelative(-83.0f, 0.0f, -141.5f, -58.5f)
                reflectiveQuadTo(80.0f, 480.0f)
                quadToRelative(0.0f, -83.0f, 58.5f, -141.5f)
                reflectiveQuadTo(280.0f, 280.0f)
                horizontalLineToRelative(160.0f)
                verticalLineToRelative(80.0f)
                lineTo(280.0f, 360.0f)
                quadToRelative(-50.0f, 0.0f, -85.0f, 35.0f)
                reflectiveQuadToRelative(-35.0f, 85.0f)
                quadToRelative(0.0f, 50.0f, 35.0f, 85.0f)
                reflectiveQuadToRelative(85.0f, 35.0f)
                horizontalLineToRelative(160.0f)
                verticalLineToRelative(80.0f)
                close()
                moveTo(320.0f, 520.0f)
                verticalLineToRelative(-80.0f)
                horizontalLineToRelative(320.0f)
                verticalLineToRelative(80.0f)
                lineTo(320.0f, 520.0f)
                close()
                moveTo(520.0f, 680.0f)
                verticalLineToRelative(-80.0f)
                horizontalLineToRelative(160.0f)
                quadToRelative(50.0f, 0.0f, 85.0f, -35.0f)
                reflectiveQuadToRelative(35.0f, -85.0f)
                quadToRelative(0.0f, -50.0f, -35.0f, -85.0f)
                reflectiveQuadToRelative(-85.0f, -35.0f)
                lineTo(520.0f, 360.0f)
                verticalLineToRelative(-80.0f)
                horizontalLineToRelative(160.0f)
                quadToRelative(83.0f, 0.0f, 141.5f, 58.5f)
                reflectiveQuadTo(880.0f, 480.0f)
                quadToRelative(0.0f, 83.0f, -58.5f, 141.5f)
                reflectiveQuadTo(680.0f, 680.0f)
                lineTo(520.0f, 680.0f)
                close()
            }
        }
            .build()
    }
}

@Composable
fun html(): ImageVector {
    return remember {
        ImageVector.Builder(
            name = "Html", defaultWidth = 24.0.dp, defaultHeight = 24.0.dp,
            viewportWidth = 960.0f, viewportHeight = 960.0f
        ).apply {
            path(fill = SolidColor(Color(0xFF000000)), stroke = null, strokeLineWidth = 0.0f,
                strokeLineCap = Butt, strokeLineJoin = Miter, strokeLineMiter = 4.0f,
                pathFillType = NonZero) {
                moveTo(0.0f, 600.0f)
                verticalLineToRelative(-240.0f)
                horizontalLineToRelative(60.0f)
                verticalLineToRelative(80.0f)
                horizontalLineToRelative(80.0f)
                verticalLineToRelative(-80.0f)
                horizontalLineToRelative(60.0f)
                verticalLineToRelative(240.0f)
                horizontalLineToRelative(-60.0f)
                verticalLineToRelative(-100.0f)
                lineTo(60.0f, 500.0f)
                verticalLineToRelative(100.0f)
                lineTo(0.0f, 600.0f)
                close()
                moveTo(310.0f, 600.0f)
                verticalLineToRelative(-180.0f)
                horizontalLineToRelative(-70.0f)
                verticalLineToRelative(-60.0f)
                horizontalLineToRelative(200.0f)
                verticalLineToRelative(60.0f)
                horizontalLineToRelative(-70.0f)
                verticalLineToRelative(180.0f)
                horizontalLineToRelative(-60.0f)
                close()
                moveTo(480.0f, 600.0f)
                verticalLineToRelative(-200.0f)
                quadToRelative(0.0f, -17.0f, 11.5f, -28.5f)
                reflectiveQuadTo(520.0f, 360.0f)
                horizontalLineToRelative(180.0f)
                quadToRelative(17.0f, 0.0f, 28.5f, 11.5f)
                reflectiveQuadTo(740.0f, 400.0f)
                verticalLineToRelative(200.0f)
                horizontalLineToRelative(-60.0f)
                verticalLineToRelative(-180.0f)
                horizontalLineToRelative(-40.0f)
                verticalLineToRelative(140.0f)
                horizontalLineToRelative(-60.0f)
                verticalLineToRelative(-140.0f)
                horizontalLineToRelative(-40.0f)
                verticalLineToRelative(180.0f)
                horizontalLineToRelative(-60.0f)
                close()
                moveTo(800.0f, 600.0f)
                verticalLineToRelative(-240.0f)
                horizontalLineToRelative(60.0f)
                verticalLineToRelative(180.0f)
                horizontalLineToRelative(100.0f)
                verticalLineToRelative(60.0f)
                lineTo(800.0f, 600.0f)
                close()
            }
        }
            .build()
    }
}

@Composable
fun image(): ImageVector {
    return remember {
        ImageVector.Builder(name = "Image", defaultWidth = 24.0.dp, defaultHeight = 24.0.dp,
            viewportWidth = 960.0f, viewportHeight = 960.0f).apply {
            path(fill = SolidColor(Color(0xFF000000)), stroke = null, strokeLineWidth = 0.0f,
                strokeLineCap = Butt, strokeLineJoin = Miter, strokeLineMiter = 4.0f,
                pathFillType = NonZero) {
                moveTo(200.0f, 840.0f)
                quadToRelative(-33.0f, 0.0f, -56.5f, -23.5f)
                reflectiveQuadTo(120.0f, 760.0f)
                verticalLineToRelative(-560.0f)
                quadToRelative(0.0f, -33.0f, 23.5f, -56.5f)
                reflectiveQuadTo(200.0f, 120.0f)
                horizontalLineToRelative(560.0f)
                quadToRelative(33.0f, 0.0f, 56.5f, 23.5f)
                reflectiveQuadTo(840.0f, 200.0f)
                verticalLineToRelative(560.0f)
                quadToRelative(0.0f, 33.0f, -23.5f, 56.5f)
                reflectiveQuadTo(760.0f, 840.0f)
                lineTo(200.0f, 840.0f)
                close()
                moveTo(200.0f, 760.0f)
                horizontalLineToRelative(560.0f)
                verticalLineToRelative(-560.0f)
                lineTo(200.0f, 200.0f)
                verticalLineToRelative(560.0f)
                close()
                moveTo(240.0f, 680.0f)
                horizontalLineToRelative(480.0f)
                lineTo(570.0f, 480.0f)
                lineTo(450.0f, 640.0f)
                lineToRelative(-90.0f, -120.0f)
                lineToRelative(-120.0f, 160.0f)
                close()
                moveTo(200.0f, 760.0f)
                verticalLineToRelative(-560.0f)
                verticalLineToRelative(560.0f)
                close()
            }
        }
            .build()
    }
}

@Composable
fun file(): ImageVector {
    return remember {
        ImageVector.Builder(
            name = "File", defaultWidth = 24.0.dp, defaultHeight = 24.0.dp,
            viewportWidth = 960.0f, viewportHeight = 960.0f
        ).apply {
            path(fill = SolidColor(Color(0xFF000000)), stroke = null, strokeLineWidth = 0.0f,
                strokeLineCap = Butt, strokeLineJoin = Miter, strokeLineMiter = 4.0f,
                pathFillType = NonZero) {
                moveTo(320.0f, 720.0f)
                horizontalLineToRelative(320.0f)
                verticalLineToRelative(-80.0f)
                lineTo(320.0f, 640.0f)
                verticalLineToRelative(80.0f)
                close()
                moveTo(320.0f, 560.0f)
                horizontalLineToRelative(320.0f)
                verticalLineToRelative(-80.0f)
                lineTo(320.0f, 480.0f)
                verticalLineToRelative(80.0f)
                close()
                moveTo(240.0f, 880.0f)
                quadToRelative(-33.0f, 0.0f, -56.5f, -23.5f)
                reflectiveQuadTo(160.0f, 800.0f)
                verticalLineToRelative(-640.0f)
                quadToRelative(0.0f, -33.0f, 23.5f, -56.5f)
                reflectiveQuadTo(240.0f, 80.0f)
                horizontalLineToRelative(320.0f)
                lineToRelative(240.0f, 240.0f)
                verticalLineToRelative(480.0f)
                quadToRelative(0.0f, 33.0f, -23.5f, 56.5f)
                reflectiveQuadTo(720.0f, 880.0f)
                lineTo(240.0f, 880.0f)
                close()
                moveTo(520.0f, 360.0f)
                verticalLineToRelative(-200.0f)
                lineTo(240.0f, 160.0f)
                verticalLineToRelative(640.0f)
                horizontalLineToRelative(480.0f)
                verticalLineToRelative(-440.0f)
                lineTo(520.0f, 360.0f)
                close()
                moveTo(240.0f, 160.0f)
                verticalLineToRelative(200.0f)
                verticalLineToRelative(-200.0f)
                verticalLineToRelative(640.0f)
                verticalLineToRelative(-640.0f)
                close()
            }
        }
            .build()
    }
}

@Composable
fun folder(): ImageVector {
    return remember {
        ImageVector.Builder(
            name = "Folder", defaultWidth = 24.0.dp, defaultHeight = 24.0.dp,
            viewportWidth = 960.0f, viewportHeight = 960.0f
        ).apply {
            path(fill = SolidColor(Color(0xFF000000)), stroke = null, strokeLineWidth = 0.0f,
                strokeLineCap = Butt, strokeLineJoin = Miter, strokeLineMiter = 4.0f,
                pathFillType = NonZero) {
                moveTo(160.0f, 800.0f)
                quadToRelative(-33.0f, 0.0f, -56.5f, -23.5f)
                reflectiveQuadTo(80.0f, 720.0f)
                verticalLineToRelative(-480.0f)
                quadToRelative(0.0f, -33.0f, 23.5f, -56.5f)
                reflectiveQuadTo(160.0f, 160.0f)
                horizontalLineToRelative(240.0f)
                lineToRelative(80.0f, 80.0f)
                horizontalLineToRelative(320.0f)
                quadToRelative(33.0f, 0.0f, 56.5f, 23.5f)
                reflectiveQuadTo(880.0f, 320.0f)
                verticalLineToRelative(400.0f)
                quadToRelative(0.0f, 33.0f, -23.5f, 56.5f)
                reflectiveQuadTo(800.0f, 800.0f)
                lineTo(160.0f, 800.0f)
                close()
                moveTo(160.0f, 720.0f)
                horizontalLineToRelative(640.0f)
                verticalLineToRelative(-400.0f)
                lineTo(447.0f, 320.0f)
                lineToRelative(-80.0f, -80.0f)
                lineTo(160.0f, 240.0f)
                verticalLineToRelative(480.0f)
                close()
                moveTo(160.0f, 720.0f)
                verticalLineToRelative(-480.0f)
                verticalLineToRelative(480.0f)
                close()
            }
        }
            .build()
    }
}