package com.crosspaste.ui.mouse

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import com.crosspaste.mouse.Position
import com.crosspaste.mouse.ScreenInfo
import com.crosspaste.ui.theme.AppUISize

/**
 * Canvas that lets the user drag remote devices around a virtual desktop.
 * Local screens are locked at (0, 0). Each remote device is a rigid group
 * of its own screens; dragging any screen of the group shifts the group's
 * [Position] offset.
 */
@Composable
fun ScreenCanvas(
    viewModel: ScreenArrangementViewModel,
    modifier: Modifier = Modifier,
) {
    val locals by viewModel.localScreens.collectAsState()
    val remotes by viewModel.remoteDevices.collectAsState()
    val measurer: TextMeasurer = rememberTextMeasurer()

    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(AppUISize.xxxxLarge * 6)) {
        val viewportPx = maxWidth.value.coerceAtLeast(400f)
        val (bounds, scale) =
            remember(locals, remotes, viewportPx) {
                computeBoundsAndScale(locals, remotes, viewportPx)
            }

        Canvas(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(AppUISize.xxxxLarge * 6)
                    .background(Color(0xFFF2F2F2))
                    .pointerInput(remotes.keys) {
                        detectDragGestures(
                            onDrag = { change, drag ->
                                change.consume()
                                val hitId =
                                    remotes.entries
                                        .firstOrNull { (_, info) ->
                                            info.screens.any { screen ->
                                                val rect = toRect(info.position, screen, bounds, scale)
                                                rect.contains(change.position)
                                            }
                                        }?.key ?: return@detectDragGestures
                                viewModel.onDragDevice(
                                    deviceId = hitId,
                                    dx = (drag.x / scale).toInt(),
                                    dy = (drag.y / scale).toInt(),
                                )
                            },
                            onDragEnd = {
                                remotes.keys.forEach { viewModel.onDragEnd(it) }
                            },
                        )
                    },
        ) {
            // Draw local screens (blue), origin-locked
            locals.forEach { screen ->
                val rect = toRect(Position(0, 0), screen, bounds, scale)
                drawRect(
                    color = Color(0xFF1E88E5).copy(alpha = 0.35f),
                    topLeft = rect.topLeft,
                    size = rect.size,
                )
                drawRect(
                    color = Color(0xFF1E88E5),
                    topLeft = rect.topLeft,
                    size = rect.size,
                    style = Stroke(width = 2f),
                )
            }
            // Draw each remote device's screens (orange), as a group
            remotes.forEach { (_, info) ->
                info.screens.forEach { screen ->
                    val rect = toRect(info.position, screen, bounds, scale)
                    drawRect(
                        color = Color(0xFFFB8C00).copy(alpha = 0.35f),
                        topLeft = rect.topLeft,
                        size = rect.size,
                    )
                    drawRect(
                        color = Color(0xFFFB8C00),
                        topLeft = rect.topLeft,
                        size = rect.size,
                        style = Stroke(width = 2f),
                    )
                    drawText(
                        textMeasurer = measurer,
                        text = AnnotatedString(info.name),
                        topLeft = rect.topLeft + Offset(4f, 4f),
                    )
                }
            }
        }
    }
}

private data class WorldBounds(
    val minX: Int,
    val minY: Int,
)

private data class RectFloats(
    val topLeft: Offset,
    val size: Size,
) {
    fun contains(p: Offset) =
        p.x in topLeft.x..(topLeft.x + size.width) &&
            p.y in topLeft.y..(topLeft.y + size.height)
}

private fun computeBoundsAndScale(
    locals: List<ScreenInfo>,
    remotes: Map<String, RemoteDeviceInfo>,
    viewportPx: Float,
): Pair<WorldBounds, Float> {
    val allRects =
        buildList {
            locals.forEach { add(IntRect(0, 0, it.width, it.height)) }
            remotes.values.forEach { info ->
                info.screens.forEach {
                    add(
                        IntRect(
                            info.position.x,
                            info.position.y,
                            info.position.x + it.width,
                            info.position.y + it.height,
                        ),
                    )
                }
            }
        }
    val minX = allRects.minOfOrNull { it.left } ?: 0
    val minY = allRects.minOfOrNull { it.top } ?: 0
    val maxX = allRects.maxOfOrNull { it.right } ?: 1920
    val worldWidth = (maxX - minX).coerceAtLeast(1920)
    val scale = ((viewportPx - 40f) / worldWidth).coerceAtLeast(0.01f)
    return WorldBounds(minX, minY) to scale
}

private fun toRect(
    position: Position,
    screen: ScreenInfo,
    bounds: WorldBounds,
    scale: Float,
): RectFloats {
    val x = (position.x - bounds.minX) * scale + 20f
    val y = (position.y - bounds.minY) * scale + 20f
    return RectFloats(
        topLeft = Offset(x, y),
        size = Size(screen.width * scale, screen.height * scale),
    )
}

private data class IntRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)
