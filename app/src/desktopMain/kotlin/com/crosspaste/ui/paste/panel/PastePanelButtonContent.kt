package com.crosspaste.ui.paste.panel

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import com.crosspaste.app.generated.resources.Res
import com.crosspaste.app.generated.resources.crosspaste_svg
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.platform.Platform
import com.crosspaste.ui.LocalDesktopAppSizeValueState
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import java.awt.MouseInfo
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

// The app icon's blue gradient; the white clipboard glyph is drawn straight onto it.
private val BUTTON_GRADIENT_TOP = Color(0xFF2F7BFE)
private val BUTTON_GRADIENT_BOTTOM = Color(0xFF0A48FC)

private const val IDLE_ALPHA = 0.6f

private const val GLYPH_FRACTION = 0.55f

// How long the button stays lit after the last pointer event on Linux.
private val LINUX_HOVER_HOLD = 800.milliseconds

/**
 * Round, translucent button: the app icon's gradient with its white glyph on top, so
 * the two read as one shape. Opaque while hovered or while the panel it controls is
 * open. A press that moves past the touch slop drags the window, anything shorter is
 * a click; a secondary-button click reports its position in window coordinates.
 */
@Composable
fun PastePanelButtonContent(
    window: ComposeWindow,
    panelOpen: Boolean,
    onClick: () -> Unit,
    onSecondaryClick: (x: Int, y: Int) -> Unit,
    onMoved: (x: Int, y: Int) -> Unit,
) {
    val copywriter = koinInject<GlobalCopywriter>()
    val platform = koinInject<Platform>()

    val appSizeValue = LocalDesktopAppSizeValueState.current

    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    // X11 delivers motion to this override-redirect window but, with an absolute
    // pointing device (VMs, tablets, touchscreens) under XWayland, never a crossing
    // event, so Compose's hover state switches on with the first move and never
    // off. On Linux count the button as hovered while pointer events keep coming
    // and let it fade once they stop, instead of waiting for an exit that may not
    // arrive.
    val isLinux = remember { platform.isLinux() }
    var pointerSeenAt by remember { mutableLongStateOf(0L) }
    val recentlyHovered by
        produceState(false, pointerSeenAt) {
            if (pointerSeenAt == 0L) return@produceState
            value = true
            delay(LINUX_HOVER_HOLD)
            value = false
        }
    val opaque = (if (isLinux) recentlyHovered else hovered) || panelOpen
    val alpha by animateFloatAsState(if (opaque) 1f else IDLE_ALPHA)

    Box(
        modifier =
            Modifier
                .size(appSizeValue.pastePanelButtonSize)
                .alpha(alpha)
                .hoverable(interactionSource)
                .pointerInput(isLinux) {
                    if (!isLinux) return@pointerInput
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial)
                            pointerSeenAt = System.nanoTime()
                        }
                    }
                }.pointerInput(window) {
                    awaitEachGesture {
                        val press = awaitEventOfType(PointerEventType.Press)
                        val down = press.changes.first()
                        if (press.buttons.isSecondaryPressed) {
                            down.consume()
                            val up = awaitEventOfType(PointerEventType.Release).changes.first()
                            up.consume()
                            onSecondaryClick(
                                up.position.x
                                    .toDp()
                                    .value
                                    .roundToInt(),
                                up.position.y
                                    .toDp()
                                    .value
                                    .roundToInt(),
                            )
                            return@awaitEachGesture
                        }
                        val startPointer = MouseInfo.getPointerInfo()?.location ?: return@awaitEachGesture
                        val startWindow = window.location
                        var dragging = false
                        drag(down.id) { change ->
                            val pointer = MouseInfo.getPointerInfo()?.location ?: return@drag
                            val dx = pointer.x - startPointer.x
                            val dy = pointer.y - startPointer.y
                            if (!dragging && hypot(dx.toDouble(), dy.toDouble()) > viewConfiguration.touchSlop) {
                                dragging = true
                            }
                            if (dragging) {
                                change.consume()
                                window.setLocation(startWindow.x + dx, startWindow.y + dy)
                            }
                        }
                        if (dragging) {
                            onMoved(window.x, window.y)
                        } else {
                            onClick()
                        }
                    }
                }.clip(CircleShape)
                .background(Brush.verticalGradient(listOf(BUTTON_GRADIENT_TOP, BUTTON_GRADIENT_BOTTOM))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(Res.drawable.crosspaste_svg),
            contentDescription = copywriter.getText("paste_panel"),
            tint = Color.White,
            modifier = Modifier.fillMaxSize(GLYPH_FRACTION),
        )
    }
}

/**
 * Compose's awaitFirstDown / waitForUpOrCancellation only react to the primary mouse
 * button on desktop, so the secondary button is read from the raw event stream.
 */
private suspend fun AwaitPointerEventScope.awaitEventOfType(type: PointerEventType): PointerEvent {
    var event = awaitPointerEvent()
    while (event.type != type) {
        event = awaitPointerEvent()
    }
    return event
}
