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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import com.crosspaste.app.generated.resources.Res
import com.crosspaste.app.generated.resources.crosspaste_svg
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.LocalDesktopAppSizeValueState
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import java.awt.MouseInfo
import kotlin.math.hypot
import kotlin.math.roundToInt

// The app icon's blue gradient; the white clipboard glyph is drawn straight onto it.
private val BUTTON_GRADIENT_TOP = Color(0xFF2F7BFE)
private val BUTTON_GRADIENT_BOTTOM = Color(0xFF0A48FC)

private const val IDLE_ALPHA = 0.6f

private const val GLYPH_FRACTION = 0.75f

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

    val appSizeValue = LocalDesktopAppSizeValueState.current

    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val alpha by animateFloatAsState(if (hovered || panelOpen) 1f else IDLE_ALPHA)

    Box(
        modifier =
            Modifier
                .size(appSizeValue.pastePanelButtonSize)
                .alpha(alpha)
                .hoverable(interactionSource)
                .pointerInput(window) {
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
