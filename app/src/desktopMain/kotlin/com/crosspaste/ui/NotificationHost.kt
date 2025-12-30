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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.crosspaste.notification.Message
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.ui.base.NotificationCard
import com.crosspaste.ui.base.NotificationContentCard
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

@Composable
fun NotificationHost() {
    val notificationManager = koinInject<NotificationManager>()
    val notificationList by notificationManager.notificationList.collectAsState()

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
        val containerColor =
            when (notification.messageType) {
                MessageType.Error -> MaterialTheme.colorScheme.errorContainer
                MessageType.Warning -> MaterialTheme.colorScheme.tertiaryContainer
                MessageType.Success -> MaterialTheme.colorScheme.primaryContainer
                MessageType.Info -> MaterialTheme.colorScheme.secondaryContainer
            }

        val contentColor = contentColorFor(containerColor)

        NotificationCard(
            modifier = Modifier.padding(bottom = tiny),
            containerColor = containerColor,
        ) {
            NotificationContentCard(
                notification = notification,
                contentColor = contentColor,
                onCancelTapped = { visibleState.targetState = false },
            )
        }
    }
}
