package com.crosspaste.ui.base

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.crosspaste.ui.theme.AppUISize.giant
import com.crosspaste.ui.theme.AppUISize.tiny

@Composable
fun FolderIcon(
    size: Dp = giant,
    cornerRadius: Dp = tiny,
    tabWidthRatio: Float = 0.65f,
    tabHeightRatio: Float = 0.20f,
    shadowColor: Color = Color.Black.copy(alpha = 0.12f),
    shadowOffset: Offset = Offset(1.5f, 2f),
) {
    val density = LocalDensity.current
    val cornerRadiusPx = with(density) { cornerRadius.toPx() }
    val sizePx = with(density) { size.toPx() }

    val baseColor = Color(0xFFFFD54F)
    val tabColor = Color(0xFFFFE082)

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawFolderWithPath(
                baseColor = shadowColor,
                tabColor = shadowColor,
                cornerRadius = cornerRadiusPx,
                tabCornerRadius = cornerRadiusPx * 0.6f,
                tabWidth = sizePx * tabWidthRatio,
                tabHeight = sizePx * tabHeightRatio,
                offset = shadowOffset,
                drawHighlight = false,
            )

            drawFolderWithPath(
                baseColor = baseColor,
                tabColor = tabColor,
                cornerRadius = cornerRadiusPx,
                tabCornerRadius = cornerRadiusPx * 0.6f,
                tabWidth = sizePx * tabWidthRatio,
                tabHeight = sizePx * tabHeightRatio,
                offset = Offset.Zero,
                drawHighlight = true,
            )
        }
    }
}

private fun DrawScope.drawFolderWithPath(
    baseColor: Color,
    tabColor: Color,
    cornerRadius: Float,
    tabCornerRadius: Float, // 单独的 tab 圆角参数
    tabWidth: Float,
    tabHeight: Float,
    offset: Offset,
    drawHighlight: Boolean,
) {
    translate(offset.x, offset.y) {
        // 创建完整的文件夹路径
        val folderPath =
            Path().apply {
                // 从左上角开始（tab 左上角）
                moveTo(tabCornerRadius, 0f)

                // Tab 顶部
                lineTo(tabWidth - tabCornerRadius, 0f)

                // Tab 右上角圆角（较小的圆角）
                arcTo(
                    rect =
                        androidx.compose.ui.geometry.Rect(
                            offset = Offset(tabWidth - tabCornerRadius * 2, 0f),
                            size = Size(tabCornerRadius * 2, tabCornerRadius * 2),
                        ),
                    startAngleDegrees = 270f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false,
                )

                lineTo(tabWidth, tabHeight)

                lineTo(size.width - cornerRadius, tabHeight)

                arcTo(
                    rect =
                        androidx.compose.ui.geometry.Rect(
                            offset = Offset(size.width - cornerRadius * 2, tabHeight),
                            size = Size(cornerRadius * 2, cornerRadius * 2),
                        ),
                    startAngleDegrees = 270f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false,
                )

                lineTo(size.width, size.height - cornerRadius)

                arcTo(
                    rect =
                        androidx.compose.ui.geometry.Rect(
                            offset = Offset(size.width - cornerRadius * 2, size.height - cornerRadius * 2),
                            size = Size(cornerRadius * 2, cornerRadius * 2),
                        ),
                    startAngleDegrees = 0f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false,
                )

                lineTo(cornerRadius, size.height)

                arcTo(
                    rect =
                        androidx.compose.ui.geometry.Rect(
                            offset = Offset(0f, size.height - cornerRadius * 2),
                            size = Size(cornerRadius * 2, cornerRadius * 2),
                        ),
                    startAngleDegrees = 90f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false,
                )

                lineTo(0f, tabCornerRadius)

                arcTo(
                    rect =
                        androidx.compose.ui.geometry.Rect(
                            offset = Offset(0f, 0f),
                            size = Size(tabCornerRadius * 2, tabCornerRadius * 2),
                        ),
                    startAngleDegrees = 180f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false,
                )

                close()
            }

        clipPath(folderPath) {
            drawRect(
                color = baseColor,
                topLeft = Offset.Zero,
                size = size,
            )

            if (tabColor != baseColor) {
                val tabPath =
                    Path().apply {
                        moveTo(0f, 0f)
                        lineTo(tabWidth, 0f)
                        lineTo(tabWidth, tabHeight)
                        lineTo(0f, tabHeight)
                        close()
                    }

                drawPath(
                    path = tabPath,
                    color = tabColor,
                )
            }

            if (drawHighlight) {
                drawRect(
                    brush =
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    Color.White.copy(alpha = 0.12f),
                                    Color.Transparent,
                                ),
                            startY = 0f,
                            endY = tabHeight * 3,
                        ),
                    topLeft = Offset.Zero,
                    size = Size(size.width, tabHeight * 3),
                )

                drawRect(
                    brush =
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    Color.Black.copy(alpha = 0.12f),
                                    Color.Transparent,
                                ),
                            startY = tabHeight,
                            endY = tabHeight + cornerRadius * 3f,
                        ),
                    topLeft = Offset(0f, tabHeight),
                    size = Size(size.width, cornerRadius * 3f),
                )
            }
        }
    }
}
