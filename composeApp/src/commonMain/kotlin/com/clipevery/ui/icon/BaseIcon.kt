package com.clipevery.ui.icon

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathFillType.Companion.NonZero
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeCap.Companion.Butt
import androidx.compose.ui.graphics.StrokeCap.Companion.Round
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeJoin.Companion.Miter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.group
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

@Composable
fun arrowLeft(): ImageVector {
    return remember {
        ImageVector.Builder(
            name = "arrowLeft", defaultWidth = 24.0.dp,
            defaultHeight = 24.0.dp, viewportWidth = 24.0f, viewportHeight = 24.0f
        ).apply {
            path(
                fill = SolidColor(Color(0xFF000000)), stroke = null, strokeLineWidth = 0.0f,
                strokeLineCap = Butt, strokeLineJoin = Miter, strokeLineMiter = 4.0f,
                pathFillType = NonZero
            ) {
                moveTo(15.41f, 7.41f)
                lineTo(14.0f, 6.0f)
                lineToRelative(-6.0f, 6.0f)
                lineToRelative(6.0f, 6.0f)
                lineToRelative(1.41f, -1.41f)
                lineTo(10.83f, 12.0f)
                lineToRelative(4.58f, -4.59f)
                close()
            }
        }
            .build()
    }
}

@Composable
fun arrowRight(): ImageVector {
    return remember {
        ImageVector.Builder(
            name = "arrowRight", defaultWidth = 24.0.dp,
            defaultHeight = 24.0.dp, viewportWidth = 24.0f, viewportHeight = 24.0f
        ).apply {
            path(
                fill = SolidColor(Color(0xFF000000)), stroke = null, strokeLineWidth = 0.0f,
                strokeLineCap = Butt, strokeLineJoin = Miter, strokeLineMiter = 4.0f,
                pathFillType = NonZero
            ) {
                moveTo(8.59f, 16.59f)
                lineTo(10.0f, 18.0f)
                lineToRelative(6.0f, -6.0f)
                lineToRelative(-6.0f, -6.0f)
                lineToRelative(-1.41f, 1.41f)
                lineTo(13.17f, 12.0f)
                lineToRelative(-4.58f, 4.59f)
                close()
            }
        }
            .build()
    }
}

@Composable
fun arrowUp(): ImageVector {
    return remember {
        ImageVector.Builder(
            name = "ExpandLessBlack24dp", defaultWidth = 24.0.dp,
            defaultHeight = 24.0.dp, viewportWidth = 24.0f, viewportHeight = 24.0f
        ).apply {
            path(
                fill = SolidColor(Color(0xFF000000)), stroke = null, strokeLineWidth = 0.0f,
                strokeLineCap = Butt, strokeLineJoin = Miter, strokeLineMiter = 4.0f,
                pathFillType = NonZero
            ) {
                moveTo(12.0f, 8.0f)
                lineToRelative(-6.0f, 6.0f)
                lineToRelative(1.41f, 1.41f)
                lineTo(12.0f, 10.83f)
                lineToRelative(4.59f, 4.58f)
                lineTo(18.0f, 14.0f)
                lineToRelative(-6.0f, -6.0f)
                close()
            }
        }
            .build()
    }
}

@Composable
fun arrowDown(): ImageVector {
    return remember {
        ImageVector.Builder(
            name = "ExpandMoreBlack24dp", defaultWidth = 24.0.dp,
            defaultHeight = 24.0.dp, viewportWidth = 24.0f, viewportHeight = 24.0f
        ).apply {
            path(
                fill = SolidColor(Color(0xFF000000)), stroke = null, strokeLineWidth = 0.0f,
                strokeLineCap = Butt, strokeLineJoin = Miter, strokeLineMiter = 4.0f,
                pathFillType = NonZero
            ) {
                moveTo(16.59f, 8.59f)
                lineTo(12.0f, 13.17f)
                lineTo(7.41f, 8.59f)
                lineTo(6.0f, 10.0f)
                lineToRelative(6.0f, 6.0f)
                lineToRelative(6.0f, -6.0f)
                lineToRelative(-1.41f, -1.41f)
                close()
            }
        }
            .build()
    }
}

@Composable
fun syncAlt(): ImageVector {
    return remember {
        ImageVector.Builder(
            name = "SyncAltBlack24dp",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            group {
                path(
                    fill = null,
                    fillAlpha = 1.0f,
                    stroke = null,
                    strokeAlpha = 1.0f,
                    strokeLineWidth = 1.0f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Miter,
                    strokeLineMiter = 1.0f,
                    pathFillType = PathFillType.NonZero
                ) {
                    moveTo(0f, 0f)
                    horizontalLineTo(24f)
                    verticalLineTo(24f)
                    horizontalLineTo(0f)
                    verticalLineTo(0f)
                    close()
                }
            }
            group {
                group {
                    path(
                        fill = SolidColor(Color(0xFF000000)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.NonZero
                    ) {
                        moveTo(7.41f, 13.41f)
                        lineTo(6f, 12f)
                        lineTo(2f, 16f)
                        lineTo(6f, 20f)
                        lineTo(7.41f, 18.59f)
                        lineTo(5.83f, 17f)
                        lineTo(21f, 17f)
                        lineTo(21f, 15f)
                        lineTo(5.83f, 15f)
                        close()
                    }
                    path(
                        fill = SolidColor(Color(0xFF000000)),
                        fillAlpha = 1.0f,
                        stroke = null,
                        strokeAlpha = 1.0f,
                        strokeLineWidth = 1.0f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                        strokeLineMiter = 1.0f,
                        pathFillType = PathFillType.NonZero
                    ) {
                        moveTo(16.59f, 10.59f)
                        lineTo(18f, 12f)
                        lineTo(22f, 8f)
                        lineTo(18f, 4f)
                        lineTo(16.59f, 5.41f)
                        lineTo(18.17f, 7f)
                        lineTo(3f, 7f)
                        lineTo(3f, 9f)
                        lineTo(18.17f, 9f)
                        close()
                    }
                }
            }
        }.build()
    }
}

@Composable
fun arrowLeftIcon(): ImageVector {
    return remember {
        ImageVector.Builder(name = "Arrowleft", defaultWidth = 244.0.dp, defaultHeight = 183.0.dp,
            viewportWidth = 244.0f, viewportHeight = 183.0f).apply {
            group {
                path(fill = SolidColor(Color(0xFF000000)), stroke = null, fillAlpha = 0.0f,
                    strokeLineWidth = 0.0f, strokeLineCap = Butt, strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f, pathFillType = NonZero) {
                    moveTo(0.0f, 0.0f)
                    horizontalLineToRelative(244.0f)
                    verticalLineToRelative(183.0f)
                    horizontalLineToRelative(-244.0f)
                    close()
                }
                path(fill = SolidColor(Color(0xFF000000)), stroke = null, strokeLineWidth = 0.0f,
                    strokeLineCap = Butt, strokeLineJoin = Miter, strokeLineMiter = 4.0f,
                    pathFillType = NonZero) {
                    moveTo(81.002f, 71.751f)
                    lineTo(66.667f, 61.0f)
                    lineTo(26.0f, 91.5f)
                    lineTo(66.667f, 122.0f)
                    lineTo(81.002f, 111.249f)
                    lineTo(64.938f, 99.125f)
                    lineTo(219.167f, 99.125f)
                    lineTo(219.167f, 83.875f)
                    lineTo(64.938f, 83.875f)
                    lineTo(81.002f, 71.751f)
                    close()
                }
            }
        }
            .build()
    }
}

@Composable
fun arrowRightIcon(): ImageVector {
    return remember {
        ImageVector.Builder(name = "Arrowright", defaultWidth = 244.0.dp, defaultHeight =
        183.0.dp, viewportWidth = 244.0f, viewportHeight = 183.0f).apply {
            group {
                path(fill = SolidColor(Color(0xFF000000)), stroke = null, fillAlpha = 0.0f,
                    strokeLineWidth = 0.0f, strokeLineCap = Butt, strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f, pathFillType = NonZero) {
                    moveTo(0.0f, 0.0f)
                    horizontalLineToRelative(244.0f)
                    verticalLineToRelative(183.0f)
                    horizontalLineToRelative(-244.0f)
                    close()
                }
                path(fill = SolidColor(Color(0xFF000000)), stroke = null, strokeLineWidth = 0.0f,
                    strokeLineCap = Butt, strokeLineJoin = Miter, strokeLineMiter = 4.0f,
                    pathFillType = NonZero) {
                    moveTo(169.165f, 111.249f)
                    lineTo(183.5f, 122.0f)
                    lineTo(224.167f, 91.5f)
                    lineTo(183.5f, 61.0f)
                    lineTo(169.165f, 71.751f)
                    lineTo(185.228f, 83.875f)
                    lineTo(31.0f, 83.875f)
                    lineTo(31.0f, 99.125f)
                    lineTo(185.228f, 99.125f)
                    lineTo(169.165f, 111.249f)
                    close()
                }
            }
        }
            .build()
    }
}

@Composable
fun block(): ImageVector {
    return remember {
        ImageVector.Builder(name = "Block", defaultWidth = 24.0.dp, defaultHeight = 24.0.dp,
            viewportWidth = 960.0f, viewportHeight = 960.0f).apply {
            path(fill = SolidColor(Color(0xFF000000)), stroke = null, strokeLineWidth = 0.0f,
                strokeLineCap = Butt, strokeLineJoin = Miter, strokeLineMiter = 4.0f,
                pathFillType = NonZero) {
                moveTo(480.0f, 880.0f)
                quadToRelative(-83.0f, 0.0f, -156.0f, -31.5f)
                reflectiveQuadTo(197.0f, 763.0f)
                quadToRelative(-54.0f, -54.0f, -85.5f, -127.0f)
                reflectiveQuadTo(80.0f, 480.0f)
                quadToRelative(0.0f, -83.0f, 31.5f, -156.0f)
                reflectiveQuadTo(197.0f, 197.0f)
                quadToRelative(54.0f, -54.0f, 127.0f, -85.5f)
                reflectiveQuadTo(480.0f, 80.0f)
                quadToRelative(83.0f, 0.0f, 156.0f, 31.5f)
                reflectiveQuadTo(763.0f, 197.0f)
                quadToRelative(54.0f, 54.0f, 85.5f, 127.0f)
                reflectiveQuadTo(880.0f, 480.0f)
                quadToRelative(0.0f, 83.0f, -31.5f, 156.0f)
                reflectiveQuadTo(763.0f, 763.0f)
                quadToRelative(-54.0f, 54.0f, -127.0f, 85.5f)
                reflectiveQuadTo(480.0f, 880.0f)
                close()
                moveTo(480.0f, 800.0f)
                quadToRelative(54.0f, 0.0f, 104.0f, -17.5f)
                reflectiveQuadToRelative(92.0f, -50.5f)
                lineTo(228.0f, 284.0f)
                quadToRelative(-33.0f, 42.0f, -50.5f, 92.0f)
                reflectiveQuadTo(160.0f, 480.0f)
                quadToRelative(0.0f, 134.0f, 93.0f, 227.0f)
                reflectiveQuadToRelative(227.0f, 93.0f)
                close()
                moveTo(732.0f, 676.0f)
                quadToRelative(33.0f, -42.0f, 50.5f, -92.0f)
                reflectiveQuadTo(800.0f, 480.0f)
                quadToRelative(0.0f, -134.0f, -93.0f, -227.0f)
                reflectiveQuadToRelative(-227.0f, -93.0f)
                quadToRelative(-54.0f, 0.0f, -104.0f, 17.5f)
                reflectiveQuadTo(284.0f, 228.0f)
                lineToRelative(448.0f, 448.0f)
                close()
            }
        }
            .build()
    }
}

@Preview
@Composable
fun showIcon() {
    Icon(arrowLeft(), contentDescription = "arrowLeft")
    Icon(arrowRight(), contentDescription = "arrowRight")
    Icon(arrowUp(), contentDescription = "arrowUp")
    Icon(arrowDown(), contentDescription = "arrowDown")
}