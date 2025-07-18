package com.crosspaste.ui.tray

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.MenuScope
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.setContent
import com.crosspaste.ui.base.CrossPasteTrayState
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.awt.ComponentOrientation
import java.awt.GraphicsConfiguration
import java.awt.GraphicsEnvironment
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.ActionEvent
import java.awt.event.MouseListener
import java.util.Locale

private val iconSize = Size(512f, 512f)

internal val GlobalDensity get() =
    GraphicsEnvironment
        .getLocalGraphicsEnvironment()
        .defaultScreenDevice
        .defaultConfiguration
        .density

private val GraphicsConfiguration.density: Density
    get() =
        Density(
            defaultTransform.scaleX.toFloat(),
            fontScale = 1f,
        )

internal val GlobalLayoutDirection get() = Locale.getDefault().layoutDirection

internal val Locale.layoutDirection: LayoutDirection
    get() = ComponentOrientation.getOrientation(this).layoutDirection

internal val ComponentOrientation.layoutDirection: LayoutDirection
    get() =
        when {
            isLeftToRight -> LayoutDirection.Ltr
            isHorizontal -> LayoutDirection.Rtl
            else -> LayoutDirection.Ltr
        }

/**
 * `true` if the platform supports tray icons in the taskbar
 */
val isTraySupported: Boolean get() = SystemTray.isSupported()

// TODO(demin): add mouse click/double-click/right click listeners (can we use PointerInputEvent?)

/**
 * Adds tray icon to the platform taskbar if it is supported.
 *
 * If tray icon isn't supported by the platform, in the "standard" error output stream
 * will be printed an error.
 *
 * See [isTraySupported] to know if tray icon is supported
 * (for example to show/hide an option in the application settings)
 *
 * @param icon Icon of the tray
 * @param state State to control tray and show notifications
 * @param tooltip Hint/tooltip that will be shown to the user
 * @param menu Context menu of the tray that will be shown to the user on the mouse click (right
 * click on Windows, left click on macOs).
 * If it doesn't contain any items then context menu will not be shown.
 * @param onAction Action performed when user clicks on the tray icon (double click on Windows,
 * right click on macOs)
 */
@Suppress("unused")
@Composable
fun CrossPasteTray(
    icon: Painter,
    state: CrossPasteTrayState = rememberTrayState(),
    tooltip: String? = null,
    onAction: (ActionEvent) -> Unit = {},
    mouseListener: MouseListener,
    menu: @Composable (MenuScope.() -> Unit) = {},
) {
    if (!isTraySupported) {
        DisposableEffect(Unit) {
            // We should notify developer, but shouldn't throw an exception.
            // If we would throw an exception, some application wouldn't work on some platforms at
            // all, if developer doesn't check that application crashes.
            //
            // We can do this because we don't return anything in Tray function, and following
            // code doesn't depend on something that is created/calculated in this function.
            System.err.println(
                "Tray is not supported on the current platform. " +
                    "Use the global property `isTraySupported` to check.",
            )
            onDispose {}
        }
        return
    }

    val currentOnAction by rememberUpdatedState(onAction)

    val awtIcon =
        remember(icon) {
            // We shouldn't use LocalDensity here because Tray's density doesn't equal it. It
            // equals to the density of the screen on which it shows. Currently Swing doesn't
            // provide us such information, it only requests an image with the desired width/height
            // (see MultiResolutionImage.getResolutionVariant). Resources like svg/xml should look okay
            // because they don't use absolute '.dp' values to draw, they use values which are
            // relative to their viewport.
            icon.toAwtImage(GlobalDensity, GlobalLayoutDirection, iconSize)
        }

    val tray =
        remember {
            TrayIcon(awtIcon).apply {
                isImageAutoSize = true

                addActionListener { e ->
                    currentOnAction(e)
                }

                addMouseListener(mouseListener)
            }
        }
    val popupMenu = remember { PopupMenu() }
    val currentMenu by rememberUpdatedState(menu)

    SideEffect {
        if (tray.image != awtIcon) tray.image = awtIcon
        if (tray.toolTip != tooltip) tray.toolTip = tooltip
    }

    val composition = rememberCompositionContext()
    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        tray.popupMenu = popupMenu

        val menuComposition =
            popupMenu.setContent(composition) {
                currentMenu()
            }

        SystemTray.getSystemTray().add(tray)

        state.notificationFlow
            .onEach(tray::displayMessage)
            .launchIn(coroutineScope)

        onDispose {
            menuComposition.dispose()
            SystemTray.getSystemTray().remove(tray)
        }
    }
}

@Composable
fun rememberTrayState() =
    remember {
        CrossPasteTrayState()
    }

private fun TrayIcon.displayMessage(notification: Notification) {
    val messageType =
        when (notification.type) {
            Notification.Type.None -> TrayIcon.MessageType.NONE
            Notification.Type.Info -> TrayIcon.MessageType.INFO
            Notification.Type.Warning -> TrayIcon.MessageType.WARNING
            Notification.Type.Error -> TrayIcon.MessageType.ERROR
        }

    displayMessage(notification.title, notification.message, messageType)
}
