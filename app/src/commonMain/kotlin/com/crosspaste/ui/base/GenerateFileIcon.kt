package com.crosspaste.ui.base

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.crosspaste.ui.theme.AppUISize.giant
import com.crosspaste.utils.ColorUtils.lighten
import com.crosspaste.utils.FileColors
import com.crosspaste.utils.extension
import com.crosspaste.utils.safeIsDirectory
import kotlin.math.min

// Predefined constants to keep the code clean
private val FolderBaseColor = Color(0xFFFFD54F) // Classic manila folder color
private val FolderDarkColor = Color(0xFFFFC107) // Darker shade
private val PaperWhite = Color(0xFFF5F5F5)

@Composable
fun SingleFileIcon(
    filePath: okio.Path,
    size: Dp = giant,
    cornerRadius: Dp = size / 12f,
    foldSize: Dp = size / 3.5f,
    shadowColor: Color = Color.Black.copy(alpha = 0.2f),
    textColor: Color = Color.White,
) {
    if (filePath.safeIsDirectory) {
        FolderIcon(
            size = size,
            shadowColor = shadowColor,
        )
        return
    }

    val density = LocalDensity.current
    val cornerRadiusPx = with(density) { cornerRadius.toPx() }
    val foldSizePx = with(density) { foldSize.toPx() }
    val ext = filePath.extension

    val baseColor = remember(ext) { FileColors.getColorForExtension(ext) }
    val gradientBrush =
        remember(baseColor) {
            Brush.linearGradient(
                colors = listOf(baseColor.lighten(0.1f), baseColor),
                start = Offset.Zero,
                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
            )
        }

    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(size)
                .drawBehind {
                    val width = this.size.width
                    val height = this.size.height

                    // 1. Draw drop shadow
                    drawPath(
                        path = createFileShapePath(width, height, cornerRadiusPx, foldSizePx),
                        color = shadowColor,
                        style = Fill,
                    )

                    // 2. Draw file body with gradient
                    val bodyPath = createFileShapePath(width, height, cornerRadiusPx, foldSizePx)
                    drawPath(
                        path = bodyPath,
                        brush = gradientBrush,
                    )

                    // 3. Draw the fold
                    drawFold(
                        width = width,
                        foldSize = foldSizePx,
                        cornerRadius = cornerRadiusPx,
                        baseColor = baseColor,
                    )
                },
    ) {
        // Extension text with shadow for visibility on light backgrounds
        Text(
            text = ext.uppercase().take(4), // Limit length to prevent overflow
            color = textColor,
            fontSize = (size.value / 4.5).sp, // Slightly adjusted font size
            fontWeight = FontWeight.Black,
            style =
                TextStyle(
                    shadow =
                        Shadow(
                            color = Color.Black.copy(alpha = 0.2f),
                            offset = Offset(1f, 1f),
                            blurRadius = 2f,
                        ),
                ),
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
fun FolderIcon(
    size: Dp,
    shadowColor: Color = Color.Black.copy(alpha = 0.2f),
) {
    Box(
        modifier =
            Modifier
                .size(size)
                .drawBehind {
                    val width = this.size.width
                    val height = this.size.height

                    // Parameter tuning
                    val tabHeight = height * 0.18f
                    val tabWidth = width * 0.45f
                    val cornerRadius = width * 0.08f

                    // 1. Backplate & tab
                    val backPath =
                        Path().apply {
                            // Top tab
                            moveTo(0f, tabHeight)
                            lineTo(0f, cornerRadius)
                            quadraticTo(0f, 0f, cornerRadius, 0f)
                            lineTo(tabWidth - cornerRadius, 0f)
                            quadraticTo(tabWidth, 0f, tabWidth, tabHeight)
                            lineTo(width - cornerRadius, tabHeight)
                            quadraticTo(width, tabHeight, width, tabHeight + cornerRadius)
                            lineTo(width, height - cornerRadius)
                            quadraticTo(width, height, width - cornerRadius, height)
                            lineTo(cornerRadius, height)
                            quadraticTo(0f, height, 0f, height - cornerRadius)
                            close()
                        }

                    // Draw backplate shadow
                    translate(left = 2f, top = 4f) {
                        drawPath(backPath, color = shadowColor)
                    }

                    // Draw backplate body
                    drawPath(
                        path = backPath,
                        brush =
                            Brush.verticalGradient(
                                colors = listOf(FolderDarkColor, FolderBaseColor),
                            ),
                    )

                    // 2. Paper insert hint
                    // Slightly exposed white paper to add depth
                    drawRoundRect(
                        color = PaperWhite,
                        topLeft = Offset(width * 0.1f, tabHeight + height * 0.05f),
                        size = Size(width * 0.8f, height * 0.7f),
                        cornerRadius = CornerRadius(4f, 4f),
                        alpha = 0.9f,
                    )

                    // 3. Front cover is usually slightly lower, here we make a rounded rectangle
                    val frontTopY = tabHeight + (height * 0.05f) // Leave a gap to show the paper inside

                    val frontPath =
                        Path().apply {
                            moveTo(0f, frontTopY)
                            lineTo(width, frontTopY)
                            lineTo(width, height - cornerRadius)
                            quadraticTo(width, height, width - cornerRadius, height)
                            lineTo(cornerRadius, height)
                            quadraticTo(0f, height, 0f, height - cornerRadius)
                            close()
                        }

                    // Front cover shadow cast on backplate
                    drawPath(
                        path = frontPath,
                        color = Color.Black.copy(alpha = 0.1f), // Very subtle shadow
                        style = Fill,
                    )

                    // Draw front cover
                    drawPath(
                        path = frontPath,
                        brush =
                            Brush.linearGradient(
                                colors = listOf(FolderBaseColor.lighten(0.1f), FolderBaseColor),
                                start = Offset(0f, frontTopY),
                                end = Offset(0f, height),
                            ),
                    )

                    // Add top edge highlight to front cover
                    drawLine(
                        color = Color.White.copy(alpha = 0.4f),
                        start = Offset(0f, frontTopY + 1f),
                        end = Offset(width, frontTopY + 1f),
                        strokeWidth = 2f,
                    )
                },
    )
}

@Composable
fun MultiFileIcon(
    fileList: List<okio.Path>,
    size: Dp = giant,
    maxVisibleFiles: Int = 3,
) {
    if (fileList.isEmpty()) return
    // Actual number of files to display
    val visibleCount = min(fileList.size, maxVisibleFiles)
    // Base parameter configuration
    val scaleStep = 0.07f // Scale reduction ratio per layer
    // [Key fix] Set offset to 8% ~ 10% of size, ensuring visibility even with scaling
    val offsetStepPercent = 0.09f
    // Calculate total offset needed for the entire stack, used for center compensation
    // We assume the stacking direction is towards upper right (X+, Y-)
    val totalOffsetX = size * (offsetStepPercent * (visibleCount - 1))
    val totalOffsetY = size * (offsetStepPercent * (visibleCount - 1))
    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        // Center compensation: since the stack drifts to upper right, we pull the whole group to lower left
        Box(
            modifier =
                Modifier
                    .offset(
                        x = -(totalOffsetX / 2),
                        y = (totalOffsetY / 2),
                    ),
        ) {
            // Reverse loop: draw the bottom layer (Nth) first, top layer (0th) last
            for (i in (visibleCount - 1) downTo 0) {
                val isTop = i == 0
                // Calculate scale and offset for current layer
                val scale = 1f - (i * scaleStep)
                // Offset to the right (X+)
                val offsetX = size * (offsetStepPercent * i)
                // Offset upward (Y-), making files in the back appear on top
                val offsetY = -(size * (offsetStepPercent * i))
                Box(
                    modifier =
                        Modifier
                            // [Important] Apply Offset before Scale for clearer logic
                            // Ensure each file has an independent offset position
                            .offset(x = offsetX, y = offsetY)
                            .zIndex(-i.toFloat()) // Ensure correct layer order
                            .size(size) // Original size
                            // Use graphicsLayer for scaling, which scales from center
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                // Add a slight rotation for a more dynamic look (optional, can be removed if not desired)
                                rotationZ = if (isTop) 0f else (i * 2f)
                            },
                ) {
                    SingleFileIcon(
                        filePath = fileList[i],
                        size = size, // Pass original size, scaling controlled by outer Box
                        // Files in the back have lighter shadow, front file has deeper shadow
                        shadowColor = if (isTop) Color.Black.copy(0.25f) else Color.Black.copy(0.1f),
                        // Only the top file shows text to avoid clutter (optional)
                        textColor = if (isTop) Color.White else Color.Transparent,
                    )
                }
            }
        }
    }
}

// --- Drawing Helpers ---

private fun createFileShapePath(
    w: Float,
    h: Float,
    cr: Float,
    fold: Float,
): Path =
    Path().apply {
        moveTo(0f, cr)
        quadraticTo(0f, 0f, cr, 0f)
        lineTo(w - fold, 0f) // Top edge stops at the fold
        lineTo(w, fold) // Diagonal cut down
        lineTo(w, h - cr)
        quadraticTo(w, h, w - cr, h)
        lineTo(cr, h)
        quadraticTo(0f, h, 0f, h - cr)
        close()
    }

private fun DrawScope.drawFold(
    width: Float,
    foldSize: Float,
    cornerRadius: Float,
    baseColor: Color,
) {
    val foldPath =
        Path().apply {
            moveTo(width - foldSize, 0f)
            lineTo(width, foldSize)
            lineTo(width - foldSize + cornerRadius, foldSize)
            quadraticTo(
                width - foldSize,
                foldSize,
                width - foldSize,
                foldSize - cornerRadius,
            )
            close()
        }

    val foldColor =
        Color(
            red = (baseColor.red * 0.7f).coerceIn(0f, 1f),
            green = (baseColor.green * 0.7f).coerceIn(0f, 1f),
            blue = (baseColor.blue * 0.7f).coerceIn(0f, 1f),
            alpha = 1f,
        )

    val foldColorDark =
        Color(
            red = (baseColor.red * 0.55f).coerceIn(0f, 1f),
            green = (baseColor.green * 0.55f).coerceIn(0f, 1f),
            blue = (baseColor.blue * 0.55f).coerceIn(0f, 1f),
            alpha = 1f,
        )

    drawPath(
        path = foldPath,
        brush =
            Brush.linearGradient(
                colors = listOf(foldColor, foldColorDark),
                start = Offset(width - foldSize, 0f),
                end = Offset(width, foldSize),
            ),
    )

    drawLine(
        color = Color.Black.copy(alpha = 0.2f),
        start = Offset(width - foldSize, 0f),
        end = Offset(width, foldSize),
        strokeWidth = 1.5f,
    )
}
