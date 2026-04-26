package com.crosspaste.ui.mouse

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crosspaste.mouse.Position
import com.crosspaste.mouse.ScreenInfo
import org.jetbrains.skia.Image
import java.io.File

/**
 * Infinite-canvas view of the mouse-sharing layout.
 *
 * Coordinate system:
 *   world  = px in the daemon's virtual desktop (the units `Position` uses)
 *   view   = px on the Compose canvas
 *   view = pan + world * scale
 *
 * Gestures:
 *   - drag on empty space → pan the canvas
 *   - long-press on a remote device's screen + drag → move that device's
 *     entire screen group (its [Position] offset). Locals are pinned at
 *     world (0, 0) — only the remote layout is editable.
 *   - mouse wheel → zoom around the cursor position. View coords under
 *     the cursor stay anchored to the same world coords across zoom, so
 *     the rectangle the user is pointing at doesn't drift away.
 */
private const val INITIAL_SCALE = 0.08f

private const val ABSOLUTE_MAX_SCALE = 1f
private const val ZOOM_STEP = 1.1f

// Lower zoom-out limit, expressed as "a 1080p-class display never renders
// smaller than [MIN_VISIBLE_SCREEN_DP] across on the canvas". Anchoring the
// floor to a dp size (rather than a fraction of the viewport) means the
// minimum scale stays the same when the user resizes the window — without
// this, a maximised window would push the minimum up to where the layout
// already fills the canvas, blocking further zoom-out.
private val MIN_VISIBLE_SCREEN_DP = 50.dp
private const val REFERENCE_SCREEN_WORLD_WIDTH = 1920f

// When panning, keep at least this many view px of the bounding box visible
// against every viewport edge so the user can't fling everything off-canvas.
private const val PAN_VISIBLE_MARGIN = 80f

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ScreenCanvas(
    viewModel: ScreenArrangementViewModel,
    modifier: Modifier = Modifier,
) {
    val locals by viewModel.localScreens.collectAsState()
    val remotes by viewModel.remoteDevices.collectAsState()
    val measurer: TextMeasurer = rememberTextMeasurer()
    val wallpapers = rememberWallpaperBitmaps(locals)
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier,
    ) {
        // Use the px-valued layout constraints — pointer drag deltas, scroll
        // events, and DrawScope all operate in pixels, so deriving viewport
        // dimensions from `maxWidth.value` (a Dp) silently mis-scaled the pan
        // limits on HiDPI screens (density > 1), which made the canvas
        // un-pannable past the right and bottom edges.
        val viewportWidth = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val viewportHeight = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        // Bounding box of every screen (locals + remotes) in world units.
        // Used for pan clamping so the layout can't drift entirely off-canvas.
        val bounds = remember(locals, remotes) { computeWorldBounds(locals, remotes) }

        // Zoom limits are window-independent: a fixed dp-based floor (tied to
        // a reference 1080p display) and a 1:1 view-px ceiling. Recomputed
        // only when density changes so HiDPI screens see the same dp limit.
        val minScale =
            remember(density) {
                with(density) { MIN_VISIBLE_SCREEN_DP.toPx() } / REFERENCE_SCREEN_WORLD_WIDTH
            }
        val maxScale = ABSOLUTE_MAX_SCALE

        var scale by remember { mutableStateOf(INITIAL_SCALE) }

        // Track whether the user has manually panned. Until then, recenter
        // automatically whenever the local-screens layout changes (so the
        // first frame doesn't paint everything off-canvas).
        var pan by remember { mutableStateOf(Offset.Zero) }
        var userPanned by remember { mutableStateOf(false) }
        LaunchedEffect(locals, remotes, viewportWidth, viewportHeight, scale) {
            val clampedScale = scale.coerceIn(minScale, maxScale)
            if (clampedScale != scale) {
                scale = clampedScale
                return@LaunchedEffect
            }
            pan =
                if (!userPanned) {
                    centerLocals(locals, viewportWidth, viewportHeight, clampedScale)
                } else {
                    clampPan(pan, bounds, clampedScale, viewportWidth, viewportHeight)
                }
        }

        var draggingDeviceId by remember { mutableStateOf<String?>(null) }

        Canvas(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF2F2F2))
                    // Clip drawing to the canvas rect so screen rectangles
                    // (and their wallpapers/labels) at extreme pan/zoom
                    // can't bleed into the surrounding UI.
                    .clipToBounds()
                    // Mouse wheel → zoom about the cursor. We re-anchor pan
                    // so that the world point under the cursor stays fixed:
                    //   pivotWorld = (cursor - pan) / scaleOld
                    //   pan'       = cursor - pivotWorld * scaleNew
                    .onPointerEvent(PointerEventType.Scroll) { event ->
                        val change = event.changes.firstOrNull() ?: return@onPointerEvent
                        val scrollY = change.scrollDelta.y
                        if (scrollY == 0f) return@onPointerEvent
                        val factor = if (scrollY < 0f) ZOOM_STEP else 1f / ZOOM_STEP
                        val newScale = (scale * factor).coerceIn(minScale, maxScale)
                        if (newScale == scale) return@onPointerEvent
                        val cursor = change.position
                        val pivotWorld =
                            Offset((cursor.x - pan.x) / scale, (cursor.y - pan.y) / scale)
                        val pivotedPan =
                            Offset(
                                cursor.x - pivotWorld.x * newScale,
                                cursor.y - pivotWorld.y * newScale,
                            )
                        pan = clampPan(pivotedPan, bounds, newScale, viewportWidth, viewportHeight)
                        scale = newScale
                        userPanned = true
                        change.consume()
                    }
                    // Long-press on a device screen → drag that device's
                    // whole group. Compose's long-press detector waits for
                    // the long-press threshold before claiming the gesture,
                    // so a short drag falls through to the pan handler below.
                    .pointerInput(remotes.keys, pan, scale) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset ->
                                draggingDeviceId = hitTestDevice(remotes, offset, pan, scale)
                            },
                            onDrag = { change, drag ->
                                change.consume()
                                val id = draggingDeviceId ?: return@detectDragGesturesAfterLongPress
                                viewModel.onDragDevice(
                                    deviceId = id,
                                    dx = (drag.x / scale).toInt(),
                                    dy = (drag.y / scale).toInt(),
                                )
                            },
                            onDragEnd = {
                                draggingDeviceId?.let { viewModel.onDragEnd(it) }
                                draggingDeviceId = null
                            },
                            onDragCancel = { draggingDeviceId = null },
                        )
                    }
                    // Plain drag → pan the infinite canvas.
                    // Keyed on bounds + viewport so the closure captures fresh
                    // values whenever a remote moves or the window resizes;
                    // otherwise the clamp would use stale limits.
                    .pointerInput(bounds, viewportWidth, viewportHeight) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            pan = clampPan(pan + drag, bounds, scale, viewportWidth, viewportHeight)
                            userPanned = true
                        }
                    },
        ) {
            // Local screens (blue) — origin-locked at world (0, 0). Each
            // screen's own (x, y) places it relative to the device origin
            // (e.g. a second monitor at world x = 1920).
            locals.forEachIndexed { index, screen ->
                val rect = toRect(Position(0, 0), screen, pan, scale)
                val bitmap = screen.wallpaperPath?.let { wallpapers[it] }
                if (bitmap != null) {
                    drawWallpaper(bitmap, rect)
                } else {
                    drawRect(
                        color = Color(0xFF1E88E5).copy(alpha = 0.35f),
                        topLeft = rect.topLeft,
                        size = rect.size,
                    )
                }
                drawRect(
                    color = Color(0xFF1E88E5),
                    topLeft = rect.topLeft,
                    size = rect.size,
                    style = Stroke(width = 2f),
                )
                drawScreenLabel(measurer, localScreenLabel(index, screen), rect)
            }
            // Remote devices (orange) — every screen of one device shares the
            // device's [Position] offset and moves as one rigid group.
            remotes.forEach { (_, info) ->
                info.screens.forEach { screen ->
                    val rect = toRect(info.position, screen, pan, scale)
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
                    drawScreenLabel(measurer, info.name, rect)
                }
            }
        }
    }
}

private data class RectFloats(
    val topLeft: Offset,
    val size: Size,
) {
    fun contains(p: Offset) =
        p.x in topLeft.x..(topLeft.x + size.width) &&
            p.y in topLeft.y..(topLeft.y + size.height)
}

/**
 * Map (device-position + screen.x/screen.y) into view coordinates.
 *
 * Earlier versions of this function ignored `screen.x` / `screen.y`, which
 * is why every screen of a multi-monitor device drew at the device origin
 * and the rectangles overlapped. The within-device offset is what places a
 * second monitor to the right of the primary on the virtual desktop.
 */
private fun toRect(
    position: Position,
    screen: ScreenInfo,
    pan: Offset,
    scale: Float,
): RectFloats {
    val worldX = position.x + screen.x
    val worldY = position.y + screen.y
    return RectFloats(
        topLeft = Offset(pan.x + worldX * scale, pan.y + worldY * scale),
        size = Size(screen.width * scale, screen.height * scale),
    )
}

private fun hitTestDevice(
    remotes: Map<String, RemoteDeviceInfo>,
    pointer: Offset,
    pan: Offset,
    scale: Float,
): String? =
    remotes.entries
        .firstOrNull { (_, info) ->
            info.screens.any { screen ->
                toRect(info.position, screen, pan, scale).contains(pointer)
            }
        }?.key

private data class WorldBounds(
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float,
) {
    val width: Float get() = maxX - minX
    val height: Float get() = maxY - minY
}

private fun computeWorldBounds(
    locals: List<ScreenInfo>,
    remotes: Map<String, RemoteDeviceInfo>,
): WorldBounds? {
    var minX = Float.POSITIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY
    var any = false
    locals.forEach {
        any = true
        minX = minOf(minX, it.x.toFloat())
        minY = minOf(minY, it.y.toFloat())
        maxX = maxOf(maxX, (it.x + it.width).toFloat())
        maxY = maxOf(maxY, (it.y + it.height).toFloat())
    }
    remotes.values.forEach { info ->
        info.screens.forEach { s ->
            any = true
            minX = minOf(minX, (info.position.x + s.x).toFloat())
            minY = minOf(minY, (info.position.y + s.y).toFloat())
            maxX = maxOf(maxX, (info.position.x + s.x + s.width).toFloat())
            maxY = maxOf(maxY, (info.position.y + s.y + s.height).toFloat())
        }
    }
    if (!any || maxX <= minX || maxY <= minY) return null
    return WorldBounds(minX, minY, maxX, maxY)
}

/**
 * Pull [pan] back so the bounding box can never escape the viewport entirely:
 * at least [PAN_VISIBLE_MARGIN] view px of it must remain visible against
 * each edge. When `bounds` is null (nothing on canvas yet) we leave pan alone.
 */
private fun clampPan(
    pan: Offset,
    bounds: WorldBounds?,
    scale: Float,
    viewportWidth: Float,
    viewportHeight: Float,
): Offset {
    if (bounds == null) return pan
    val margin = PAN_VISIBLE_MARGIN
    val viewMinX = bounds.minX * scale
    val viewMaxX = bounds.maxX * scale
    val viewMinY = bounds.minY * scale
    val viewMaxY = bounds.maxY * scale
    val minPanX = margin - viewMaxX
    val maxPanX = viewportWidth - margin - viewMinX
    val minPanY = margin - viewMaxY
    val maxPanY = viewportHeight - margin - viewMinY
    return Offset(
        pan.x.coerceIn(minPanX, maxPanX),
        pan.y.coerceIn(minPanY, maxPanY),
    )
}

private fun centerLocals(
    locals: List<ScreenInfo>,
    viewportWidth: Float,
    viewportHeight: Float,
    scale: Float,
): Offset {
    if (locals.isEmpty()) return Offset(viewportWidth / 2f, viewportHeight / 2f)
    val minX = locals.minOf { it.x }
    val minY = locals.minOf { it.y }
    val maxX = locals.maxOf { it.x + it.width }
    val maxY = locals.maxOf { it.y + it.height }
    val centerWorld = Offset((minX + maxX) / 2f, (minY + maxY) / 2f)
    return Offset(
        x = viewportWidth / 2f - centerWorld.x * scale,
        y = viewportHeight / 2f - centerWorld.y * scale,
    )
}

private fun localScreenLabel(
    index: Int,
    screen: ScreenInfo,
): String {
    val display = screen.name?.takeIf { it.isNotBlank() } ?: "Display ${index + 1}"
    val tag = if (screen.isPrimary) "$display (Primary)" else display
    return "$tag\n${screen.width}×${screen.height}"
}

/**
 * Draw a label centered in the given screen rect with a translucent backdrop
 * so the text stays legible over arbitrary wallpapers. Font size scales with
 * the rect (so it tracks zoom), and the layout is constrained to the rect's
 * inner bounds — overflowing characters get ellipsized rather than spilling
 * outside the screen.
 */
private fun DrawScope.drawScreenLabel(
    measurer: TextMeasurer,
    text: String,
    rect: RectFloats,
) {
    if (rect.size.width < 24f || rect.size.height < 24f) return

    val outerPadding = 4f
    val maxWidth = (rect.size.width - 2 * outerPadding).toInt()
    val maxHeight = (rect.size.height - 2 * outerPadding).toInt()
    if (maxWidth <= 0 || maxHeight <= 0) return

    // Tie font size to the smaller side of the rect so labels feel
    // proportional, and convert to sp through the current Density so the
    // result matches user-perceived size on HiDPI displays.
    val targetPx = minOf(rect.size.width, rect.size.height) * 0.14f
    val fontSize = (targetPx / density).coerceIn(8f, 22f).sp

    val style =
        TextStyle(
            fontSize = fontSize,
            textAlign = TextAlign.Center,
            color = Color.White,
        )

    val layout =
        measurer.measure(
            text = AnnotatedString(text),
            style = style,
            constraints = Constraints(maxWidth = maxWidth, maxHeight = maxHeight),
            overflow = TextOverflow.Ellipsis,
            softWrap = true,
            maxLines = 3,
        )

    val w = layout.size.width.toFloat()
    val h = layout.size.height.toFloat()
    if (w <= 0f || h <= 0f) return

    val x = rect.topLeft.x + (rect.size.width - w) / 2f
    val y = rect.topLeft.y + (rect.size.height - h) / 2f

    val bgPadding = 4f
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.55f),
        topLeft = Offset(x - bgPadding, y - bgPadding),
        size = Size(w + 2 * bgPadding, h + 2 * bgPadding),
        cornerRadius = CornerRadius(4f, 4f),
    )
    drawText(
        textLayoutResult = layout,
        topLeft = Offset(x, y),
    )
}

private fun DrawScope.drawWallpaper(
    bitmap: ImageBitmap,
    rect: RectFloats,
) {
    val width =
        rect.size.width
            .toInt()
            .coerceAtLeast(1)
    val height =
        rect.size.height
            .toInt()
            .coerceAtLeast(1)
    drawImage(
        image = bitmap,
        dstOffset = IntOffset(rect.topLeft.x.toInt(), rect.topLeft.y.toInt()),
        dstSize = IntSize(width, height),
    )
}

/**
 * Decode each unique wallpaper PNG once and keep [ImageBitmap]s alive across
 * recomposition. We key on the path list so adding/removing/swapping a screen
 * triggers a reload, but redraws (pan, zoom, drag) hit the cache.
 *
 * Skia decodes PNG/JPEG natively. HEIC is handled in Swift before we get
 * here — see `getDesktopWallpaperPng` in MacosApi.swift.
 */
@Composable
private fun rememberWallpaperBitmaps(screens: List<ScreenInfo>): Map<String, ImageBitmap> {
    val paths = screens.mapNotNull { it.wallpaperPath }.distinct()
    return remember(paths) {
        paths
            .mapNotNull { path ->
                runCatching {
                    val bytes = File(path).readBytes()
                    path to Image.makeFromEncoded(bytes).toComposeImageBitmap()
                }.getOrNull()
            }.toMap()
    }
}
