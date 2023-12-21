package com.clipevery.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp


@Composable
fun macos(): ImageVector {
    return remember {
        ImageVector.Builder(
            name = "macos",
            defaultWidth = 384.dp,
            defaultHeight = 512.dp,
            viewportWidth = 384f,
            viewportHeight = 512f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1.0f,
                stroke = null,
                strokeAlpha = 1.0f,
                strokeLineWidth = 1.0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 1.0f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(318.7f, 268.7f)
                curveToRelative(-0.2f, -36.7f, 16.4f, -64.4f, 50f, -84.8f)
                curveToRelative(-18.8f, -26.9f, -47.2f, -41.7f, -84.7f, -44.6f)
                curveToRelative(-35.5f, -2.8f, -74.3f, 20.7f, -88.5f, 20.7f)
                curveToRelative(-15f, 0f, -49.4f, -19.7f, -76.4f, -19.7f)
                curveTo(63.3f, 141.2f, 4f, 184.8f, 4f, 273.5f)
                quadToRelative(0f, 39.3f, 14.4f, 81.2f)
                curveToRelative(12.8f, 36.7f, 59f, 126.7f, 107.2f, 125.2f)
                curveToRelative(25.2f, -0.6f, 43f, -17.9f, 75.8f, -17.9f)
                curveToRelative(31.8f, 0f, 48.3f, 17.9f, 76.4f, 17.9f)
                curveToRelative(48.6f, -0.7f, 90.4f, -82.5f, 102.6f, -119.3f)
                curveToRelative(-65.2f, -30.7f, -61.7f, -90f, -61.7f, -91.9f)
                close()
                moveToRelative(-56.6f, -164.2f)
                curveToRelative(27.3f, -32.4f, 24.8f, -61.9f, 24f, -72.5f)
                curveToRelative(-24.1f, 1.4f, -52f, 16.4f, -67.9f, 34.9f)
                curveToRelative(-17.5f, 19.8f, -27.8f, 44.3f, -25.6f, 71.9f)
                curveToRelative(26.1f, 2f, 49.9f, -11.4f, 69.5f, -34.3f)
                close()
            }
        }.build()
    }
}


@Composable
fun windows(): ImageVector {
    return remember {
        ImageVector.Builder(
            name = "windows",
            defaultWidth = 448.dp,
            defaultHeight = 512.dp,
            viewportWidth = 448f,
            viewportHeight = 512f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1.0f,
                stroke = null,
                strokeAlpha = 1.0f,
                strokeLineWidth = 1.0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 1.0f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(0f, 93.7f)
                lineToRelative(183.6f, -25.3f)
                verticalLineToRelative(177.4f)
                horizontalLineTo(0f)
                verticalLineTo(93.7f)
                close()
                moveToRelative(0f, 324.6f)
                lineToRelative(183.6f, 25.3f)
                verticalLineTo(268.4f)
                horizontalLineTo(0f)
                verticalLineToRelative(149.9f)
                close()
                moveToRelative(203.8f, 28f)
                lineTo(448f, 480f)
                verticalLineTo(268.4f)
                horizontalLineTo(203.8f)
                verticalLineToRelative(177.9f)
                close()
                moveToRelative(0f, -380.6f)
                verticalLineToRelative(180.1f)
                horizontalLineTo(448f)
                verticalLineTo(32f)
                lineTo(203.8f, 65.7f)
                close()
            }
        }.build()
    }
}


@Composable
fun linux(): ImageVector {
    return remember {
        ImageVector.Builder(
            name = "linux",
            defaultWidth = 448.dp,
            defaultHeight = 512.dp,
            viewportWidth = 448f,
            viewportHeight = 512f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1.0f,
                stroke = null,
                strokeAlpha = 1.0f,
                strokeLineWidth = 1.0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 1.0f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(220.8f, 123.3f)
                curveToRelative(1f, 0.5f, 1.8f, 1.7f, 3f, 1.7f)
                curveToRelative(1.1f, 0f, 2.8f, -0.4f, 2.9f, -1.5f)
                curveToRelative(0.2f, -1.4f, -1.9f, -2.3f, -3.2f, -2.9f)
                curveToRelative(-1.7f, -0.7f, -3.9f, -1f, -5.5f, -0.1f)
                curveToRelative(-0.4f, 0.2f, -0.8f, 0.7f, -0.6f, 1.1f)
                curveToRelative(0.3f, 1.3f, 2.3f, 1.1f, 3.4f, 1.7f)
                close()
                moveToRelative(-21.9f, 1.7f)
                curveToRelative(1.2f, 0f, 2f, -1.2f, 3f, -1.7f)
                curveToRelative(1.1f, -0.6f, 3.1f, -0.4f, 3.5f, -1.6f)
                curveToRelative(0.2f, -0.4f, -0.2f, -0.9f, -0.6f, -1.1f)
                curveToRelative(-1.6f, -0.9f, -3.8f, -0.6f, -5.5f, 0.1f)
                curveToRelative(-1.3f, 0.6f, -3.4f, 1.5f, -3.2f, 2.9f)
                curveToRelative(0.1f, 1f, 1.8f, 1.5f, 2.8f, 1.4f)
                close()
                moveTo(420f, 403.8f)
                curveToRelative(-3.6f, -4f, -5.3f, -11.6f, -7.2f, -19.7f)
                curveToRelative(-1.8f, -8.1f, -3.9f, -16.8f, -10.5f, -22.4f)
                curveToRelative(-1.3f, -1.1f, -2.6f, -2.1f, -4f, -2.9f)
                curveToRelative(-1.3f, -0.8f, -2.7f, -1.5f, -4.1f, -2f)
                curveToRelative(9.2f, -27.3f, 5.6f, -54.5f, -3.7f, -79.1f)
                curveToRelative(-11.4f, -30.1f, -31.3f, -56.4f, -46.5f, -74.4f)
                curveToRelative(-17.1f, -21.5f, -33.7f, -41.9f, -33.4f, -72f)
                curveTo(311.1f, 85.4f, 315.7f, 0.1f, 234.8f, 0f)
                curveTo(132.4f, -0.2f, 158f, 103.4f, 156.9f, 135.2f)
                curveToRelative(-1.7f, 23.4f, -6.4f, 41.8f, -22.5f, 64.7f)
                curveToRelative(-18.9f, 22.5f, -45.5f, 58.8f, -58.1f, 96.7f)
                curveToRelative(-6f, 17.9f, -8.8f, 36.1f, -6.2f, 53.3f)
                curveToRelative(-6.5f, 5.8f, -11.4f, 14.7f, -16.6f, 20.2f)
                curveToRelative(-4.2f, 4.3f, -10.3f, 5.9f, -17f, 8.3f)
                reflectiveCurveToRelative(-14f, 6f, -18.5f, 14.5f)
                curveToRelative(-2.1f, 3.9f, -2.8f, 8.1f, -2.8f, 12.4f)
                curveToRelative(0f, 3.9f, 0.6f, 7.9f, 1.2f, 11.8f)
                curveToRelative(1.2f, 8.1f, 2.5f, 15.7f, 0.8f, 20.8f)
                curveToRelative(-5.2f, 14.4f, -5.9f, 24.4f, -2.2f, 31.7f)
                curveToRelative(3.8f, 7.3f, 11.4f, 10.5f, 20.1f, 12.3f)
                curveToRelative(17.3f, 3.6f, 40.8f, 2.7f, 59.3f, 12.5f)
                curveToRelative(19.8f, 10.4f, 39.9f, 14.1f, 55.9f, 10.4f)
                curveToRelative(11.6f, -2.6f, 21.1f, -9.6f, 25.9f, -20.2f)
                curveToRelative(12.5f, -0.1f, 26.3f, -5.4f, 48.3f, -6.6f)
                curveToRelative(14.9f, -1.2f, 33.6f, 5.3f, 55.1f, 4.1f)
                curveToRelative(0.6f, 2.3f, 1.4f, 4.6f, 2.5f, 6.7f)
                verticalLineToRelative(0.1f)
                curveToRelative(8.3f, 16.7f, 23.8f, 24.3f, 40.3f, 23f)
                curveToRelative(16.6f, -1.3f, 34.1f, -11f, 48.3f, -27.9f)
                curveToRelative(13.6f, -16.4f, 36f, -23.2f, 50.9f, -32.2f)
                curveToRelative(7.4f, -4.5f, 13.4f, -10.1f, 13.9f, -18.3f)
                curveToRelative(0.4f, -8.2f, -4.4f, -17.3f, -15.5f, -29.7f)
                close()
                moveTo(223.7f, 87.3f)
                curveToRelative(9.8f, -22.2f, 34.2f, -21.8f, 44f, -0.4f)
                curveToRelative(6.5f, 14.2f, 3.6f, 30.9f, -4.3f, 40.4f)
                curveToRelative(-1.6f, -0.8f, -5.9f, -2.6f, -12.6f, -4.9f)
                curveToRelative(1.1f, -1.2f, 3.1f, -2.7f, 3.9f, -4.6f)
                curveToRelative(4.8f, -11.8f, -0.2f, -27f, -9.1f, -27.3f)
                curveToRelative(-7.3f, -0.5f, -13.9f, 10.8f, -11.8f, 23f)
                curveToRelative(-4.1f, -2f, -9.4f, -3.5f, -13f, -4.4f)
                curveToRelative(-1f, -6.9f, -0.3f, -14.6f, 2.9f, -21.8f)
                close()
                moveTo(183f, 75.8f)
                curveToRelative(10.1f, 0f, 20.8f, 14.2f, 19.1f, 33.5f)
                curveToRelative(-3.5f, 1f, -7.1f, 2.5f, -10.2f, 4.6f)
                curveToRelative(1.2f, -8.9f, -3.3f, -20.1f, -9.6f, -19.6f)
                curveToRelative(-8.4f, 0.7f, -9.8f, 21.2f, -1.8f, 28.1f)
                curveToRelative(1f, 0.8f, 1.9f, -0.2f, -5.9f, 5.5f)
                curveToRelative(-15.6f, -14.6f, -10.5f, -52.1f, 8.4f, -52.1f)
                close()
                moveToRelative(-13.6f, 60.7f)
                curveToRelative(6.2f, -4.6f, 13.6f, -10f, 14.1f, -10.5f)
                curveToRelative(4.7f, -4.4f, 13.5f, -14.2f, 27.9f, -14.2f)
                curveToRelative(7.1f, 0f, 15.6f, 2.3f, 25.9f, 8.9f)
                curveToRelative(6.3f, 4.1f, 11.3f, 4.4f, 22.6f, 9.3f)
                curveToRelative(8.4f, 3.5f, 13.7f, 9.7f, 10.5f, 18.2f)
                curveToRelative(-2.6f, 7.1f, -11f, 14.4f, -22.7f, 18.1f)
                curveToRelative(-11.1f, 3.6f, -19.8f, 16f, -38.2f, 14.9f)
                curveToRelative(-3.9f, -0.2f, -7f, -1f, -9.6f, -2.1f)
                curveToRelative(-8f, -3.5f, -12.2f, -10.4f, -20f, -15f)
                curveToRelative(-8.6f, -4.8f, -13.2f, -10.4f, -14.7f, -15.3f)
                curveToRelative(-1.4f, -4.9f, 0f, -9f, 4.2f, -12.3f)
                close()
                moveToRelative(3.3f, 334f)
                curveToRelative(-2.7f, 35.1f, -43.9f, 34.4f, -75.3f, 18f)
                curveToRelative(-29.9f, -15.8f, -68.6f, -6.5f, -76.5f, -21.9f)
                curveToRelative(-2.4f, -4.7f, -2.4f, -12.7f, 2.6f, -26.4f)
                verticalLineToRelative(-0.2f)
                curveToRelative(2.4f, -7.6f, 0.6f, -16f, -0.6f, -23.9f)
                curveToRelative(-1.2f, -7.8f, -1.8f, -15f, 0.9f, -20f)
                curveToRelative(3.5f, -6.7f, 8.5f, -9.1f, 14.8f, -11.3f)
                curveToRelative(10.3f, -3.7f, 11.8f, -3.4f, 19.6f, -9.9f)
                curveToRelative(5.5f, -5.7f, 9.5f, -12.9f, 14.3f, -18f)
                curveToRelative(5.1f, -5.5f, 10f, -8.1f, 17.7f, -6.9f)
                curveToRelative(8.1f, 1.2f, 15.1f, 6.8f, 21.9f, 16f)
                lineToRelative(19.6f, 35.6f)
                curveToRelative(9.5f, 19.9f, 43.1f, 48.4f, 41f, 68.9f)
                close()
                moveToRelative(-1.4f, -25.9f)
                curveToRelative(-4.1f, -6.6f, -9.6f, -13.6f, -14.4f, -19.6f)
                curveToRelative(7.1f, 0f, 14.2f, -2.2f, 16.7f, -8.9f)
                curveToRelative(2.3f, -6.2f, 0f, -14.9f, -7.4f, -24.9f)
                curveToRelative(-13.5f, -18.2f, -38.3f, -32.5f, -38.3f, -32.5f)
                curveToRelative(-13.5f, -8.4f, -21.1f, -18.7f, -24.6f, -29.9f)
                reflectiveCurveToRelative(-3f, -23.3f, -0.3f, -35.2f)
                curveToRelative(5.2f, -22.9f, 18.6f, -45.2f, 27.2f, -59.2f)
                curveToRelative(2.3f, -1.7f, 0.8f, 3.2f, -8.7f, 20.8f)
                curveToRelative(-8.5f, 16.1f, -24.4f, 53.3f, -2.6f, 82.4f)
                curveToRelative(0.6f, -20.7f, 5.5f, -41.8f, 13.8f, -61.5f)
                curveToRelative(12f, -27.4f, 37.3f, -74.9f, 39.3f, -112.7f)
                curveToRelative(1.1f, 0.8f, 4.6f, 3.2f, 6.2f, 4.1f)
                curveToRelative(4.6f, 2.7f, 8.1f, 6.7f, 12.6f, 10.3f)
                curveToRelative(12.4f, 10f, 28.5f, 9.2f, 42.4f, 1.2f)
                curveToRelative(6.2f, -3.5f, 11.2f, -7.5f, 15.9f, -9f)
                curveToRelative(9.9f, -3.1f, 17.8f, -8.6f, 22.3f, -15f)
                curveToRelative(7.7f, 30.4f, 25.7f, 74.3f, 37.2f, 95.7f)
                curveToRelative(6.1f, 11.4f, 18.3f, 35.5f, 23.6f, 64.6f)
                curveToRelative(3.3f, -0.1f, 7f, 0.4f, 10.9f, 1.4f)
                curveToRelative(13.8f, -35.7f, -11.7f, -74.2f, -23.3f, -84.9f)
                curveToRelative(-4.7f, -4.6f, -4.9f, -6.6f, -2.6f, -6.5f)
                curveToRelative(12.6f, 11.2f, 29.2f, 33.7f, 35.2f, 59f)
                curveToRelative(2.8f, 11.6f, 3.3f, 23.7f, 0.4f, 35.7f)
                curveToRelative(16.4f, 6.8f, 35.9f, 17.9f, 30.7f, 34.8f)
                curveToRelative(-2.2f, -0.1f, -3.2f, 0f, -4.2f, 0f)
                curveToRelative(3.2f, -10.1f, -3.9f, -17.6f, -22.8f, -26.1f)
                curveToRelative(-19.6f, -8.6f, -36f, -8.6f, -38.3f, 12.5f)
                curveToRelative(-12.1f, 4.2f, -18.3f, 14.7f, -21.4f, 27.3f)
                curveToRelative(-2.8f, 11.2f, -3.6f, 24.7f, -4.4f, 39.9f)
                curveToRelative(-0.5f, 7.7f, -3.6f, 18f, -6.8f, 29f)
                curveToRelative(-32.1f, 22.9f, -76.7f, 32.9f, -114.3f, 7.2f)
                close()
                moveToRelative(257.4f, -11.5f)
                curveToRelative(-0.9f, 16.8f, -41.2f, 19.9f, -63.2f, 46.5f)
                curveToRelative(-13.2f, 15.7f, -29.4f, 24.4f, -43.6f, 25.5f)
                reflectiveCurveToRelative(-26.5f, -4.8f, -33.7f, -19.3f)
                curveToRelative(-4.7f, -11.1f, -2.4f, -23.1f, 1.1f, -36.3f)
                curveToRelative(3.7f, -14.2f, 9.2f, -28.8f, 9.9f, -40.6f)
                curveToRelative(0.8f, -15.2f, 1.7f, -28.5f, 4.2f, -38.7f)
                curveToRelative(2.6f, -10.3f, 6.6f, -17.2f, 13.7f, -21.1f)
                curveToRelative(0.3f, -0.2f, 0.7f, -0.3f, 1f, -0.5f)
                curveToRelative(0.8f, 13.2f, 7.3f, 26.6f, 18.8f, 29.5f)
                curveToRelative(12.6f, 3.3f, 30.7f, -7.5f, 38.4f, -16.3f)
                curveToRelative(9f, -0.3f, 15.7f, -0.9f, 22.6f, 5.1f)
                curveToRelative(9.9f, 8.5f, 7.1f, 30.3f, 17.1f, 41.6f)
                curveToRelative(10.6f, 11.6f, 14f, 19.5f, 13.7f, 24.6f)
                close()
                moveTo(173.3f, 148.7f)
                curveToRelative(2f, 1.9f, 4.7f, 4.5f, 8f, 7.1f)
                curveToRelative(6.6f, 5.2f, 15.8f, 10.6f, 27.3f, 10.6f)
                curveToRelative(11.6f, 0f, 22.5f, -5.9f, 31.8f, -10.8f)
                curveToRelative(4.9f, -2.6f, 10.9f, -7f, 14.8f, -10.4f)
                reflectiveCurveToRelative(5.9f, -6.3f, 3.1f, -6.6f)
                reflectiveCurveToRelative(-2.6f, 2.6f, -6f, 5.1f)
                curveToRelative(-4.4f, 3.2f, -9.7f, 7.4f, -13.9f, 9.8f)
                curveToRelative(-7.4f, 4.2f, -19.5f, 10.2f, -29.9f, 10.2f)
                reflectiveCurveToRelative(-18.7f, -4.8f, -24.9f, -9.7f)
                curveToRelative(-3.1f, -2.5f, -5.7f, -5f, -7.7f, -6.9f)
                curveToRelative(-1.5f, -1.4f, -1.9f, -4.6f, -4.3f, -4.9f)
                curveToRelative(-1.4f, -0.1f, -1.8f, 3.7f, 1.7f, 6.5f)
                close()
            }
        }.build()
    }
}

@Composable
fun iphone(): ImageVector {
    return remember {
        ImageVector.Builder(
            name = "MobileSolid",
            defaultWidth = 12.dp,
            defaultHeight = 16.dp,
            viewportWidth = 384f,
            viewportHeight = 512f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1.0f,
                stroke = null,
                strokeAlpha = 1.0f,
                strokeLineWidth = 1.0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 1.0f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(80f, 0f)
                curveTo(44.7f, 0f, 16f, 28.7f, 16f, 64f)
                verticalLineTo(448f)
                curveToRelative(0f, 35.3f, 28.7f, 64f, 64f, 64f)
                horizontalLineTo(304f)
                curveToRelative(35.3f, 0f, 64f, -28.7f, 64f, -64f)
                verticalLineTo(64f)
                curveToRelative(0f, -35.3f, -28.7f, -64f, -64f, -64f)
                horizontalLineTo(80f)
                close()
                moveToRelative(80f, 432f)
                horizontalLineToRelative(64f)
                curveToRelative(8.8f, 0f, 16f, 7.2f, 16f, 16f)
                reflectiveCurveToRelative(-7.2f, 16f, -16f, 16f)
                horizontalLineTo(160f)
                curveToRelative(-8.8f, 0f, -16f, -7.2f, -16f, -16f)
                reflectiveCurveToRelative(7.2f, -16f, 16f, -16f)
                close()
            }
        }.build()
    }
}

@Composable
fun ipad(): ImageVector {
    return remember {
        ImageVector.Builder(
            name = "TabletSolid",
            defaultWidth = 14.dp,
            defaultHeight = 16.dp,
            viewportWidth = 448f,
            viewportHeight = 512f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1.0f,
                stroke = null,
                strokeAlpha = 1.0f,
                strokeLineWidth = 1.0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 1.0f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(64f, 0f)
                curveTo(28.7f, 0f, 0f, 28.7f, 0f, 64f)
                verticalLineTo(448f)
                curveToRelative(0f, 35.3f, 28.7f, 64f, 64f, 64f)
                horizontalLineTo(384f)
                curveToRelative(35.3f, 0f, 64f, -28.7f, 64f, -64f)
                verticalLineTo(64f)
                curveToRelative(0f, -35.3f, -28.7f, -64f, -64f, -64f)
                horizontalLineTo(64f)
                close()
                moveTo(176f, 432f)
                horizontalLineToRelative(96f)
                curveToRelative(8.8f, 0f, 16f, 7.2f, 16f, 16f)
                reflectiveCurveToRelative(-7.2f, 16f, -16f, 16f)
                horizontalLineTo(176f)
                curveToRelative(-8.8f, 0f, -16f, -7.2f, -16f, -16f)
                reflectiveCurveToRelative(7.2f, -16f, 16f, -16f)
                close()
            }
        }.build()
    }
}

@Composable
fun android(): ImageVector {
    return remember {
        ImageVector.Builder(
            name = "android",
            defaultWidth = 576.dp,
            defaultHeight = 512.dp,
            viewportWidth = 576f,
            viewportHeight = 512f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1.0f,
                stroke = null,
                strokeAlpha = 1.0f,
                strokeLineWidth = 1.0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 1.0f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(420.55f, 301.93f)
                arcToRelative(24f, 24f, 0f, isMoreThanHalf = true, isPositiveArc = true, 24f, -24f)
                arcToRelative(24f, 24f, 0f, isMoreThanHalf = false, isPositiveArc = true, -24f, 24f)
                moveToRelative(-265.1f, 0f)
                arcToRelative(24f, 24f, 0f, isMoreThanHalf = true, isPositiveArc = true, 24f, -24f)
                arcToRelative(24f, 24f, 0f, isMoreThanHalf = false, isPositiveArc = true, -24f, 24f)
                moveToRelative(273.7f, -144.48f)
                lineToRelative(47.94f, -83f)
                arcToRelative(10f, 10f, 0f, isMoreThanHalf = true, isPositiveArc = false, -17.27f, -10f)
                horizontalLineToRelative(0f)
                lineToRelative(-48.54f, 84.07f)
                arcToRelative(301.25f, 301.25f, 0f, isMoreThanHalf = false, isPositiveArc = false, -246.56f, 0f)
                lineTo(116.18f, 64.45f)
                arcToRelative(10f, 10f, 0f, isMoreThanHalf = true, isPositiveArc = false, -17.27f, 10f)
                horizontalLineToRelative(0f)
                lineToRelative(47.94f, 83f)
                curveTo(64.53f, 202.22f, 8.24f, 285.55f, 0f, 384f)
                horizontalLineTo(576f)
                curveToRelative(-8.24f, -98.45f, -64.54f, -181.78f, -146.85f, -226.55f)
            }
        }.build()
    }
}

@Composable
fun questionOS(): ImageVector {
    return remember {
        ImageVector.Builder(
            name = "QuestionSolid",
            defaultWidth = 10.dp,
            defaultHeight = 16.dp,
            viewportWidth = 320f,
            viewportHeight = 512f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1.0f,
                stroke = null,
                strokeAlpha = 1.0f,
                strokeLineWidth = 1.0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 1.0f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(80f, 160f)
                curveToRelative(0f, -35.3f, 28.7f, -64f, 64f, -64f)
                horizontalLineToRelative(32f)
                curveToRelative(35.3f, 0f, 64f, 28.7f, 64f, 64f)
                verticalLineToRelative(3.6f)
                curveToRelative(0f, 21.8f, -11.1f, 42.1f, -29.4f, 53.8f)
                lineToRelative(-42.2f, 27.1f)
                curveToRelative(-25.2f, 16.2f, -40.4f, 44.1f, -40.4f, 74f)
                verticalLineTo(320f)
                curveToRelative(0f, 17.7f, 14.3f, 32f, 32f, 32f)
                reflectiveCurveToRelative(32f, -14.3f, 32f, -32f)
                verticalLineToRelative(-1.4f)
                curveToRelative(0f, -8.2f, 4.2f, -15.8f, 11f, -20.2f)
                lineToRelative(42.2f, -27.1f)
                curveToRelative(36.6f, -23.6f, 58.8f, -64.1f, 58.8f, -107.7f)
                verticalLineTo(160f)
                curveToRelative(0f, -70.7f, -57.3f, -128f, -128f, -128f)
                horizontalLineTo(144f)
                curveTo(73.3f, 32f, 16f, 89.3f, 16f, 160f)
                curveToRelative(0f, 17.7f, 14.3f, 32f, 32f, 32f)
                reflectiveCurveToRelative(32f, -14.3f, 32f, -32f)
                close()
                moveToRelative(80f, 320f)
                arcToRelative(40f, 40f, 0f, isMoreThanHalf = true, isPositiveArc = false, 0f, -80f)
                arcToRelative(40f, 40f, 0f, isMoreThanHalf = true, isPositiveArc = false, 0f, 80f)
                close()
            }
        }.build()
    }
}
