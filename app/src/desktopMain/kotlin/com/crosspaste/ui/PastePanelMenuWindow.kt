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
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds

// Gap between the button and the menu beside it.
private val MENU_GAP = 4.dp

// Stand-in for the menu's width until its content has been measured.
private val MENU_WIDTH_GUESS = 200.dp

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

    // Beside the button rather than over it: the button is an override-redirect
    // window the compositor keeps above every managed one, so anything under it
    // would be covered. Right of the button, top edges aligned, or to its left
    // when it would run off the screen. The window packs to its content, so the
    // final placement waits for the content's measured width; until then a
    // guess keeps the first frame close.
    val gap = MENU_GAP.value.toInt()
    val screenWidth = remember { Toolkit.getDefaultToolkit().screenSize.width }
    val density = LocalDensity.current
    var menuWidth by remember { mutableStateOf<Int?>(null) }

    fun leftFor(width: Int): Int {
        val right = anchor.x + anchor.width + gap
        return if (right + width > screenWidth) anchor.x - gap - width else right
    }
    val windowState =
        rememberWindowState(
            position = WindowPosition(leftFor(MENU_WIDTH_GUESS.value.toInt()).dp, anchor.y.dp),
            size = DpSize.Unspecified,
        )
    LaunchedEffect(menuWidth) {
        menuWidth?.let { windowState.position = WindowPosition(leftFor(it).dp, anchor.y.dp) }
    }

    // Mapping the window does not hand it the focus; ask X11 for it as the other
    // windows do, and ignore the focus shuffle on the way up.
    val ignoreFocusLoss = remember { AtomicBoolean(true) }
    LaunchedEffect(Unit) {
        delay(100.milliseconds)
        X11Api.bringToFront(X11Api.getWindow(title), source = NativeLong(1))
        delay(300.milliseconds)
        ignoreFocusLoss.set(false)
    }

    Window(
        onCloseRequest = onDismiss,
        state = windowState,
        title = title,
        undecorated = true,
        resizable = false,
        alwaysOnTop = true,
    ) {
        DisposableEffect(Unit) {
            val listener =
                object : WindowAdapter() {
                    override fun windowLostFocus(e: WindowEvent) {
                        if (!ignoreFocusLoss.get()) {
                            onDismiss()
                        }
                    }
                }
            window.addWindowFocusListener(listener)
            onDispose { window.removeWindowFocusListener(listener) }
        }

        PastePanelWindowContext {
            Column(
                modifier =
                    Modifier
                        .width(IntrinsicSize.Max)
                        .onSizeChanged { size -> menuWidth = with(density) { size.width.toDp() }.value.toInt() }
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
