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
import androidx.compose.runtime.DisposableEffect
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
import org.koin.compose.koinInject
import javax.swing.JComponent

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
 * This guard replaces the active CustomTitleBar with one that has [CustomTitleBar.forceHitTest]
 * permanently set to true, preventing native buttons from capturing clicks.
 * On dispose, the original CustomTitleBar is restored so Jewel's handler resumes control.
 */
@Composable
private fun TitleBarHitTestGuard(
    window: ComposeWindow?,
    active: Boolean,
) {
    val isDark = LocalThemeState.current.isCurrentThemeDark

    DisposableEffect(active, window) {
        if (!active || window == null) return@DisposableEffect onDispose {}
        if (!JBR.isAvailable()) return@DisposableEffect onDispose {}

        val decorations = JBR.getWindowDecorations()
        val originalTitleBar = findCustomTitleBar(window.rootPane)

        val guardTitleBar = decorations.createCustomTitleBar()
        if (originalTitleBar != null) {
            guardTitleBar.height = originalTitleBar.height
            guardTitleBar.putProperties(originalTitleBar.properties)
        } else {
            guardTitleBar.height = TITLE_BAR_HEIGHT
            guardTitleBar.putProperty("controls.dark", isDark)
        }
        guardTitleBar.forceHitTest(true)
        decorations.setCustomTitleBar(window, guardTitleBar)

        onDispose {
            if (originalTitleBar != null) {
                decorations.setCustomTitleBar(window, originalTitleBar)
            } else {
                val normalTitleBar = decorations.createCustomTitleBar()
                normalTitleBar.height = TITLE_BAR_HEIGHT
                normalTitleBar.putProperty("controls.dark", isDark)
                decorations.setCustomTitleBar(window, normalTitleBar)
            }
        }
    }
}

private const val TITLE_BAR_HEIGHT = 64f

/**
 * Find the CustomTitleBar associated with this window by scanning
 * the rootPane's client properties via known keys and reflection fallback.
 */
private fun findCustomTitleBar(rootPane: javax.swing.JRootPane?): CustomTitleBar? {
    if (rootPane == null) return null
    // Try known JBR client property keys
    for (key in KNOWN_TITLE_BAR_KEYS) {
        val value = rootPane.getClientProperty(key)
        if (value is CustomTitleBar) return value
    }
    // Fallback: scan all client properties via reflection
    return scanClientProperties(rootPane)
}

private val KNOWN_TITLE_BAR_KEYS =
    listOf(
        "jetbrains.awt.customTitleBar",
        "jetbrains.awt.windowCustomTitleBar",
        "JetBrains.CustomTitleBar",
        "customTitleBar",
    )

/**
 * Use reflection to scan JComponent's internal client property storage
 * for a [CustomTitleBar] instance, regardless of the property key.
 */
@Suppress("UNCHECKED_CAST")
private fun scanClientProperties(component: JComponent): CustomTitleBar? =
    try {
        val cpField = JComponent::class.java.getDeclaredField("clientProperties")
        cpField.isAccessible = true
        val arrayTable = cpField.get(component) ?: return null

        val tableField = arrayTable.javaClass.getDeclaredField("table")
        tableField.isAccessible = true
        when (val table = tableField.get(arrayTable)) {
            is Array<*> -> {
                // ArrayTable single-entry: Object[2] = {key, value}
                if (table.size >= 2 && table[1] is CustomTitleBar) table[1] as CustomTitleBar else null
            }
            is java.util.Hashtable<*, *> -> {
                table.values.firstOrNull { it is CustomTitleBar } as? CustomTitleBar
            }
            else -> null
        }
    } catch (_: Exception) {
        null
    }
