package com.crosspaste.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.crosspaste.notification.Message
import com.crosspaste.notification.ToastManager
import com.crosspaste.ui.base.NotificationCard
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

@Composable
fun NotificationHost() {
    val toastManager = koinInject<ToastManager>()
    val toastList by toastManager.toastList.collectAsState()

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
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(tiny),
        ) {
            toastList.forEach { toast ->
                key(toast.messageId) {
                    NotificationItemWrapper(
                        toast = toast,
                        onDismiss = { toastManager.removeToast(toast.messageId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationItemWrapper(
    toast: Message,
    onDismiss: () -> Unit,
) {
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }

    // Auto-dismiss logic moved here for cleaner management
    LaunchedEffect(toast.messageId, toast.duration) {
        toast.duration?.let {
            delay(it)
            visibleState.targetState = false
            delay(300) // Wait for exit animation
            onDismiss()
        }
    }

    AnimatedVisibility(
        visibleState = visibleState,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut() + shrinkVertically(),
    ) {
        NotificationCard(
            toast = toast,
            onCancelTapped = {
                visibleState.targetState = false
                // Note: The actual removal should happen after animation
                // In a production app, consider using a callback to the manager
                // after visibleState.isIdle && !targetState
            },
        )
    }
}
