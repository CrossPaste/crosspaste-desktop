package com.clipevery.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType.Companion.NonZero
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap.Companion.Round
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp


@Composable
fun warning(): ImageVector {
    return remember {
        ImageVector.Builder(
            name = "Warning", defaultWidth = 44.0.dp, defaultHeight = 44.0.dp,
            viewportWidth = 24.0f, viewportHeight = 24.0f
        ).apply {
            path(
                fill = SolidColor(Color(0x00000000)), stroke = SolidColor(Color(0xFF2c3e50)),
                strokeLineWidth = 1.5f, strokeLineCap = Round, strokeLineJoin =
                StrokeJoin.Companion.Round, strokeLineMiter = 4.0f, pathFillType = NonZero
            ) {
                moveTo(12.0f, 9.0f)
                verticalLineToRelative(4.0f)
            }
            path(
                fill = SolidColor(Color(0x00000000)), stroke = SolidColor(Color(0xFF2c3e50)),
                strokeLineWidth = 1.5f, strokeLineCap = Round, strokeLineJoin =
                StrokeJoin.Companion.Round, strokeLineMiter = 4.0f, pathFillType = NonZero
            ) {
                moveTo(10.363f, 3.591f)
                lineToRelative(-8.106f, 13.534f)
                arcToRelative(1.914f, 1.914f, 0.0f, false, false, 1.636f, 2.871f)
                horizontalLineToRelative(16.214f)
                arcToRelative(1.914f, 1.914f, 0.0f, false, false, 1.636f, -2.87f)
                lineToRelative(-8.106f, -13.536f)
                arcToRelative(1.914f, 1.914f, 0.0f, false, false, -3.274f, 0.0f)
                close()
            }
            path(
                fill = SolidColor(Color(0x00000000)), stroke = SolidColor(Color(0xFF2c3e50)),
                strokeLineWidth = 1.5f, strokeLineCap = Round, strokeLineJoin =
                StrokeJoin.Companion.Round, strokeLineMiter = 4.0f, pathFillType = NonZero
            ) {
                moveTo(12.0f, 16.0f)
                horizontalLineToRelative(0.01f)
            }
        }
            .build()
    }
}

@Composable
fun question(): ImageVector {
    return remember {
        ImageVector.Builder(
            name = "Question", defaultWidth = 44.0.dp, defaultHeight = 44.0.dp,
            viewportWidth = 24.0f, viewportHeight = 24.0f
        ).apply {
            path(
                fill = SolidColor(Color(0x00000000)), stroke = SolidColor(Color(0xFF2c3e50)),
                strokeLineWidth = 1.5f, strokeLineCap = Round, strokeLineJoin =
                StrokeJoin.Companion.Round, strokeLineMiter = 4.0f, pathFillType = NonZero
            ) {
                moveTo(9.103f, 2.0f)
                horizontalLineToRelative(5.794f)
                arcToRelative(3.0f, 3.0f, 0.0f, false, true, 2.122f, 0.879f)
                lineToRelative(4.101f, 4.1f)
                arcToRelative(3.0f, 3.0f, 0.0f, false, true, 0.88f, 2.125f)
                verticalLineToRelative(5.794f)
                arcToRelative(3.0f, 3.0f, 0.0f, false, true, -0.879f, 2.122f)
                lineToRelative(-4.1f, 4.101f)
                arcToRelative(3.0f, 3.0f, 0.0f, false, true, -2.123f, 0.88f)
                horizontalLineToRelative(-5.795f)
                arcToRelative(3.0f, 3.0f, 0.0f, false, true, -2.122f, -0.88f)
                lineToRelative(-4.101f, -4.1f)
                arcToRelative(3.0f, 3.0f, 0.0f, false, true, -0.88f, -2.124f)
                verticalLineToRelative(-5.794f)
                arcToRelative(3.0f, 3.0f, 0.0f, false, true, 0.879f, -2.122f)
                lineToRelative(4.1f, -4.101f)
                arcToRelative(3.0f, 3.0f, 0.0f, false, true, 2.125f, -0.88f)
                close()
            }
            path(
                fill = SolidColor(Color(0x00000000)), stroke = SolidColor(Color(0xFF2c3e50)),
                strokeLineWidth = 1.5f, strokeLineCap = Round, strokeLineJoin =
                StrokeJoin.Companion.Round, strokeLineMiter = 4.0f, pathFillType = NonZero
            ) {
                moveTo(12.0f, 9.0f)
                horizontalLineToRelative(0.01f)
            }
            path(
                fill = SolidColor(Color(0x00000000)), stroke = SolidColor(Color(0xFF2c3e50)),
                strokeLineWidth = 1.5f, strokeLineCap = Round, strokeLineJoin =
                StrokeJoin.Companion.Round, strokeLineMiter = 4.0f, pathFillType = NonZero
            ) {
                moveTo(11.0f, 12.0f)
                horizontalLineToRelative(1.0f)
                verticalLineToRelative(4.0f)
                horizontalLineToRelative(1.0f)
            }
        }
            .build()
    }
}
