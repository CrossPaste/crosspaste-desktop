package com.crosspaste.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.platform.linux.api.X11Api
import com.crosspaste.ui.DesktopContext.PastePanelWindowContext
import com.crosspaste.ui.base.NativeMenuEntry
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny2X
import com.crosspaste.ui.theme.AppUISize.tiny3X
import com.crosspaste.ui.theme.AppUISize.tiny3XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.tiny5X
import com.crosspaste.utils.GlobalCoroutineScope.mainCoroutineDispatcher
import com.sun.jna.NativeLong
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds

// Gap between the button and the menu beside it.
private val MENU_GAP = 4.dp

// Stand-in for the menu's size until its content has been measured.
private val MENU_WIDTH_GUESS = 180.dp
private val MENU_HEIGHT_GUESS = 200.dp

/**
 * Computes where the context menu window goes beside the button at [anchor].
 *
 * Places the menu to the right of the button if it fits within [usableScreen],
 * otherwise to its left. Top edges are aligned with the button, clamped to ensure
 * the whole menu remains within the vertical bounds of the usable screen area.
 */
internal fun pastePanelMenuPositionBeside(
    anchor: Rectangle,
    menuWidth: Int,
    menuHeight: Int,
    usableScreen: Rectangle,
    gap: Int,
): Point {
    val rightOfButton = anchor.x + anchor.width + gap
    val leftOfButton = anchor.x - gap - menuWidth
    val screenRight = usableScreen.x + usableScreen.width
    val screenBottom = usableScreen.y + usableScreen.height

    val x =
        if (rightOfButton + menuWidth <= screenRight) {
            rightOfButton
        } else if (leftOfButton >= usableScreen.x) {
            leftOfButton
        } else {
            val maxX = maxOf(usableScreen.x, screenRight - menuWidth)
            rightOfButton.coerceIn(usableScreen.x, maxX)
        }

    val maxY = maxOf(usableScreen.y, screenBottom - menuHeight)
    val y = anchor.y.coerceIn(usableScreen.y, maxY)

    return Point(x, y)
}

/**
 * The floating button's context menu on Linux, drawn in a window of its own.
 *
 * The XAWT popup menu finds out about a click elsewhere through an X pointer
 * grab. Under XWayland a click on a Wayland surface never reaches the X server,
 * so that menu simply stayed open. This window takes the keyboard focus instead
 * and closes the moment it loses it, the way the search and bubble windows do,
 * which works wherever the compositor moves focus — X11 and Wayland alike.
 */
@Composable
fun PastePanelMenuWindow(
    anchor: Rectangle,
    entries: List<NativeMenuEntry>,
    onDismiss: () -> Unit,
) {
    val appWindowManager = koinInject<DesktopAppWindowManager>()
    val title = appWindowManager.pastePanelMenuWindowTitle

    val gap = MENU_GAP.value.toInt()
    val density = LocalDensity.current

    val center = remember(anchor) { Point(anchor.x + anchor.width / 2, anchor.y + anchor.height / 2) }
    val usableScreen =
        remember(center) {
            val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
            val configuration =
                ge.screenDevices
                    .map { it.defaultConfiguration }
                    .firstOrNull { it.bounds.contains(center) }
                    ?: ge.defaultScreenDevice.defaultConfiguration
            val bounds = configuration.bounds
            val insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration)
            Rectangle(
                bounds.x + insets.left,
                bounds.y + insets.top,
                bounds.width - insets.left - insets.right,
                bounds.height - insets.top - insets.bottom,
            )
        }

    var menuSize by remember { mutableStateOf<IntSize?>(null) }

    fun currentPosition(
        width: Int,
        height: Int,
    ): Point =
        pastePanelMenuPositionBeside(
            anchor = anchor,
            menuWidth = width,
            menuHeight = height,
            usableScreen = usableScreen,
            gap = gap,
        )

    val initialPos =
        remember(anchor, usableScreen) {
            currentPosition(MENU_WIDTH_GUESS.value.toInt(), MENU_HEIGHT_GUESS.value.toInt())
        }

    val windowState =
        rememberWindowState(
            position = WindowPosition(initialPos.x.dp, initialPos.y.dp),
            size = DpSize.Unspecified,
        )

    LaunchedEffect(menuSize, usableScreen) {
        menuSize?.let { size ->
            val w = with(density) { size.width.toDp() }.value.toInt()
            val h = with(density) { size.height.toDp() }.value.toInt()
            val pos = currentPosition(w, h)
            windowState.position = WindowPosition(pos.x.dp, pos.y.dp)
        }
    }

    val focusLostDuringGracePeriod = remember { AtomicBoolean(false) }
    val ignoreFocusLoss = remember { AtomicBoolean(true) }

    Window(
        onCloseRequest = onDismiss,
        state = windowState,
        title = title,
        undecorated = true,
        transparent = true,
        resizable = false,
        alwaysOnTop = true,
    ) {
        val window = this.window

        DisposableEffect(window) {
            val listener =
                object : WindowAdapter() {
                    override fun windowLostFocus(e: WindowEvent) {
                        if (ignoreFocusLoss.get()) {
                            focusLostDuringGracePeriod.set(true)
                        } else {
                            onDismiss()
                        }
                    }
                }
            window.addWindowFocusListener(listener)
            onDispose { window.removeWindowFocusListener(listener) }
        }

        LaunchedEffect(window) {
            delay(100.milliseconds)
            X11Api.bringToFront(X11Api.getWindow(title), source = NativeLong(1))
            delay(300.milliseconds)
            ignoreFocusLoss.set(false)
            if (focusLostDuringGracePeriod.get() || !window.isFocused) {
                onDismiss()
            }
        }

        PastePanelWindowContext {
            Column(
                modifier =
                    Modifier
                        .width(IntrinsicSize.Max)
                        .onSizeChanged { size -> menuSize = size }
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.key == Key.Escape && keyEvent.type == KeyEventType.KeyDown) {
                                onDismiss()
                                true
                            } else {
                                false
                            }
                        }.clip(tiny3XRoundedCornerShape)
                        .background(AppUIColors.generalBackground)
                        .border(tiny5X, AppUIColors.sectionCardBorder, tiny3XRoundedCornerShape)
                        .padding(vertical = tiny3X),
            ) {
                entries.forEach { entry ->
                    when (entry) {
                        is NativeMenuEntry.Item ->
                            Text(
                                text = entry.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onDismiss()
                                            mainCoroutineDispatcher.launch { entry.action() }
                                        }.padding(horizontal = medium, vertical = tiny2X),
                            )
                        NativeMenuEntry.Separator ->
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = tiny3X),
                                color = AppUIColors.sectionCardBorder,
                            )
                    }
                }
            }
        }
    }
}
