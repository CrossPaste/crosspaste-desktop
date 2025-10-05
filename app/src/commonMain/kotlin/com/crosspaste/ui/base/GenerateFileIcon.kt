package com.crosspaste.ui.base

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.crosspaste.ui.theme.AppUISize.giant
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.xLarge
import com.crosspaste.utils.ColorUtils.lighten
import com.crosspaste.utils.FileColors
import com.crosspaste.utils.extension
import com.crosspaste.utils.safeIsDirectory
import kotlin.math.min

@Composable
fun SingleFileIcon(
    filePath: okio.Path,
    size: Dp = giant,
    cornerRadius: Dp = tiny,
    foldSize: Dp = xLarge,
    shadowColor: Color = Color.Black.copy(alpha = 0.6f),
    shadowOffset: Offset = Offset(2f, 2f),
    textColor: Color = Color.White,
) {
    if (filePath.safeIsDirectory) {
        FolderIcon(
            size = size,
            cornerRadius = cornerRadius,
            shadowColor = shadowColor.copy(alpha = 0.12f),
            shadowOffset = Offset(1.5f, 2f),
        )
        return
    }

    val density = LocalDensity.current
    val cornerRadiusPx = with(density) { cornerRadius.toPx() }
    val foldSizePx = with(density) { foldSize.toPx() }

    val ext = filePath.extension

    val color by remember(ext) {
        mutableStateOf(
            FileColors.getColorForExtension(ext),
        )
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(size)
                .drawBehind {
                    // Draw shadow
                    drawShadow(
                        color = shadowColor,
                        cornerRadius = cornerRadiusPx,
                        foldSize = foldSizePx,
                        offset = shadowOffset,
                    )

                    // Draw main file shape with fold
                    drawFileShape(
                        color = color,
                        cornerRadius = cornerRadiusPx,
                        foldSize = foldSizePx,
                    )

                    // Draw fold shadow
                    drawFoldShadow(
                        color = shadowColor.copy(alpha = 0.3f),
                        foldSize = foldSizePx,
                    )

                    // Draw a slightly lighter fold triangle
                    drawFoldTriangle(
                        color = color.lighten(0.5f),
                        cornerRadius = cornerRadiusPx,
                        foldSize = foldSizePx,
                    )
                },
    ) {
        // File extension text
        Text(
            text = ".$ext",
            color = textColor,
            fontSize = (size.value / 5).sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
fun MultiFileIcon(
    fileList: List<okio.Path>,
    size: Dp = giant,
    cornerRadius: Dp = tiny,
    foldSize: Dp = xLarge,
    shadowColor: Color = Color.Black.copy(alpha = 0.6f),
    shadowOffset: Offset = Offset(2f, 2f),
    textColor: Color = Color.White,
    maxVisibleFiles: Int = 4,
) {
    require(fileList.isNotEmpty()) { "Files list cannot be empty" }

    // Calculate the actual number of files to display
    val visibleFiles = min(fileList.size, maxVisibleFiles)

    // Calculate offsets
    val offsetStep = size.value * 0.08f
    // Compensation offset: since background files shift to bottom-left,
    // we compensate by shifting the whole group to top-right by half
    val compensationX = offsetStep * (visibleFiles - 1) / 2f // compensate to right
    val compensationY = -offsetStep * (visibleFiles - 1) / 2f // compensate upward

    Box(
        modifier = Modifier.size(size * 1.2f),
        contentAlignment = Alignment.Center,
    ) {
        // Inner Box containing all files with compensation offset applied
        Box(
            modifier = Modifier.offset(x = compensationX.dp, y = compensationY.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Draw background files (stacked behind)
            if (visibleFiles > 1) {
                // Draw files from back to front (excluding the main file)
                for (i in (visibleFiles - 1) downTo 1) {
                    val fileIndex = min(i, fileList.size - 1)
                    val filePath = fileList[fileIndex]

                    // Background files offset relative to main file (bottom-left)
                    val offsetX = -offsetStep * i // to left
                    val offsetY = offsetStep * i // downward

                    Box(
                        modifier =
                            Modifier
                                .offset(x = offsetX.dp, y = offsetY.dp)
                                .alpha(0.85f - (i * 0.1f))
                                .zIndex(-i.toFloat()),
                    ) {
                        // Use SingleFileIcon for each background file
                        SingleFileIcon(
                            filePath = filePath,
                            size = size,
                            cornerRadius = cornerRadius,
                            foldSize = foldSize,
                            shadowColor = shadowColor.copy(alpha = shadowColor.alpha * 0.4f),
                            shadowOffset = shadowOffset,
                            textColor = textColor,
                        )
                    }
                }
            }

            // Draw main file (front) - at origin position, no additional offset needed
            Box(
                modifier = Modifier.zIndex(1f),
            ) {
                SingleFileIcon(
                    filePath = fileList.first(),
                    size = size,
                    cornerRadius = cornerRadius,
                    foldSize = foldSize,
                    shadowColor = shadowColor,
                    shadowOffset = shadowOffset,
                    textColor = textColor,
                )
            }
        }
    }
}

// Function to draw the file shape with fold
fun DrawScope.drawFileShape(
    color: Color,
    cornerRadius: Float,
    foldSize: Float,
) {
    val path =
        Path().apply {
            // Start from top-left + corner radius
            moveTo(cornerRadius, 0f)

            // Top line to fold point
            lineTo(size.width - foldSize, 0f)

            // Fold line down
            lineTo(size.width, foldSize)

            // Right line to bottom-right - corner radius
            lineTo(size.width, size.height - cornerRadius)

            // Bottom-right corner
            arcTo(
                rect =
                    Rect(
                        left = size.width - cornerRadius * 2,
                        top = size.height - cornerRadius * 2,
                        right = size.width,
                        bottom = size.height,
                    ),
                startAngleDegrees = 0f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false,
            )

            // Bottom line
            lineTo(cornerRadius, size.height)

            // Bottom-left corner
            arcTo(
                rect =
                    Rect(
                        left = 0f,
                        top = size.height - cornerRadius * 2,
                        right = cornerRadius * 2,
                        bottom = size.height,
                    ),
                startAngleDegrees = 90f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false,
            )

            // Left line
            lineTo(0f, cornerRadius)

            // Top-left corner
            arcTo(
                rect =
                    Rect(
                        left = 0f,
                        top = 0f,
                        right = cornerRadius * 2,
                        bottom = cornerRadius * 2,
                    ),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false,
            )

            close()
        }

    drawPath(
        path = path,
        color = color,
    )
}

// Function to draw the fold triangle
fun DrawScope.drawFoldTriangle(
    color: Color,
    cornerRadius: Float,
    foldSize: Float,
) {
    val path =
        Path().apply {
            moveTo(size.width - foldSize, 0f)
            lineTo(size.width, foldSize)
            lineTo(size.width - foldSize + cornerRadius * 2, foldSize)
            arcTo(
                rect =
                    Rect(
                        left = size.width - foldSize,
                        top = foldSize - cornerRadius * 2,
                        right = size.width - foldSize + cornerRadius * 2,
                        bottom = foldSize,
                    ),
                startAngleDegrees = 90f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false,
            )
            close()
        }

    drawPath(
        path = path,
        color = color,
    )
}

// Function to draw fold shadow (the inner shadow cast by the fold)
fun DrawScope.drawFoldShadow(
    color: Color,
    foldSize: Float,
) {
    // Create a shadow path that extends from the fold
    val path =
        Path().apply {
            moveTo(size.width - foldSize * 0.8f, foldSize)
            lineTo(size.width, foldSize)
            lineTo(size.width, foldSize * 1.6f)
            close()
        }

    drawPath(
        path = path,
        color = color,
    )
}

// Function to draw shadow
fun DrawScope.drawShadow(
    color: Color,
    cornerRadius: Float,
    foldSize: Float,
    offset: Offset,
) {
    translate(offset.x, offset.y) {
        drawFileShape(
            color = color,
            cornerRadius = cornerRadius,
            foldSize = foldSize,
        )
    }
}
