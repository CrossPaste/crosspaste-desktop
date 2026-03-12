package com.crosspaste.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.notification.Message
import com.crosspaste.notification.NotificationManager
import com.crosspaste.platform.Platform
import com.crosspaste.ui.base.MessageContentCard
import com.crosspaste.ui.base.NotificationCard
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny
import com.jetbrains.JBR
import com.jetbrains.WindowDecorations.CustomTitleBar
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.koin.compose.koinInject

@Composable
fun NotificationHost() {
    val notificationManager = koinInject<NotificationManager>()
    val notificationList by notificationManager.notificationList.collectAsState()
    val platform = koinInject<Platform>()
    val appWindowManager = koinInject<DesktopAppWindowManager>()

    // On Windows, temporarily disable native title bar button hit testing
    // when notifications overlap the button area.
    // Without this, clicks on the notification close button are intercepted
    // by the native minimize button rendered underneath.
    if (platform.isWindows()) {
        TitleBarHitTestGuard(
            window = appWindowManager.mainComposeWindow,
            active = notificationList.isNotEmpty(),
        )
    }

    // Calculate the top offset based on window insets or decoration
    val appSizeValue = LocalDesktopAppSizeValueState.current
    val topPadding =
        with(LocalDensity.current) {
            (-appSizeValue.windowDecorationHeight + medium).roundToPx()
        }

    Popup(
        alignment = Alignment.TopCenter,
        offset = IntOffset(0, topPadding),
        properties = PopupProperties(clippingEnabled = false),
    ) {
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(tiny),
            userScrollEnabled = false,
        ) {
            items(
                items = notificationList,
                key = { it.messageId },
            ) { notification ->
                NotificationItemWrapper(
                    modifier = Modifier.animateItem(),
                    notification = notification,
                    onDismiss = { notificationManager.removeNotification(it) },
                )
            }
        }
    }
}

@Composable
private fun NotificationItemWrapper(
    notification: Message,
    onDismiss: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visibleState =
        remember {
            MutableTransitionState(false).apply { targetState = true }
        }

    LaunchedEffect(visibleState.isIdle, visibleState.targetState) {
        if (visibleState.isIdle && !visibleState.targetState) {
            onDismiss(notification.messageId)
        }
    }

    notification.duration?.let { duration ->
        LaunchedEffect(notification.messageId) {
            delay(duration)
            visibleState.targetState = false
        }
    }

    AnimatedVisibility(
        modifier = modifier,
        visibleState = visibleState,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut() + shrinkVertically(),
    ) {
        val (containerColor, contentColor) = notification.messageType.getMessageColor()

        NotificationCard(
            modifier = Modifier.padding(bottom = tiny),
            containerColor = containerColor,
        ) {
            MessageContentCard(
                title = notification.title,
                message = notification.message,
                messageType = notification.messageType,
                contentColor = contentColor,
                onCancel = { visibleState.targetState = false },
            )
        }
    }
}

/**
 * On Windows, Jewel's DecoratedWindow uses JBR's CustomTitleBar for native window buttons.
 * The notification popup can visually overlap these buttons, but clicks are intercepted
 * by the native hit test (WM_NCHITTEST) before reaching Compose.
 *
 * This guard continuously sets [CustomTitleBar.forceHitTest] to true while notifications
 * are visible, preventing native buttons from capturing clicks in the overlap area.
 * Jewel's internal handler naturally restores normal behavior once the guard stops.
 */
@Composable
private fun TitleBarHitTestGuard(
    window: ComposeWindow?,
    active: Boolean,
) {
    LaunchedEffect(active, window) {
        if (!active || window == null) return@LaunchedEffect
        if (!JBR.isAvailable()) return@LaunchedEffect

        val titleBar = findCustomTitleBar(window) ?: return@LaunchedEffect

        while (isActive) {
            titleBar.forceHitTest(true)
            delay(4)
        }
    }
}

private val TITLE_BAR_PROPERTY_KEYS =
    listOf(
        "jetbrains.awt.customTitleBar",
        "jetbrains.awt.windowCustomTitleBar",
        "customTitleBar",
    )

private fun findCustomTitleBar(window: ComposeWindow): CustomTitleBar? {
    val rootPane = window.rootPane ?: return null
    for (key in TITLE_BAR_PROPERTY_KEYS) {
        val value = rootPane.getClientProperty(key)
        if (value is CustomTitleBar) return value
    }
    return null
}
